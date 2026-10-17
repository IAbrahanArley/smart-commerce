# SC-005 — Reserva e disponibilidade por variante: validação

Data: 2026-10-17. Branch: `claude/sc005-reservations-f071b6`. Referências: [ADR-0012 §1](../architecture/adr/0012-stock-reservation-cart-orders.md) (D1 = 30 min, D7 = `409 RESERVED_STOCK`), [SPRINT_03](../product/SPRINT_03.md), ADR-0008/0009/0011.

## Tarefa e critérios de aceite

Reservas por variante com expiração, disponível = físico − reservas `ACTIVE` não vencidas, vitrine e admin usando o disponível, ajuste negativo abaixo do reservado bloqueado. Aceite: com reserva ativa vitrine e admin refletem o disponível; após o prazo o estoque volta sem depender da rotina; o ledger não recebe movimentos de reserva.

## Entrega

- **Migração `V11__inventory_reservation.sql`** (aditiva): tabela `inventory_reservation` (`id`, `tenant_id`, `variant_id`, `order_id`, `quantity`, `status`, `expires_at`, `created_at`, `closed_at`); FK composta `(variant_id, tenant_id) → catalog_variant(id, tenant_id)`; `UNIQUE (order_id, variant_id)`; CHECKs `quantity > 0`, status ∈ {`ACTIVE`,`CONSUMED`,`RELEASED`,`EXPIRED`}, `expires_at > created_at` e `ACTIVE ⇔ closed_at IS NULL`; índice parcial `(tenant_id, variant_id, expires_at) INCLUDE (quantity) WHERE status='ACTIVE'` e índice de vencimento `(expires_at) WHERE status='ACTIVE'`. Não altera `inventory_balance`, `inventory_movement` nem migrações anteriores.
- **Fórmula única**: funções SQL `inventory_reserved_quantity(tenant, variant, at)` e `inventory_available_quantity(tenant, variant, at)` (`STABLE`), chamadas por `InventoryRepository.levels/level/lockLevels` e pelo `StorefrontRepository`. O instante `at` vem sempre do `Clock` UTC do backend (novo bean em `config/ClockConfiguration`), truncado a microssegundos; nunca de `now()` do banco nem do cliente.
- **Lock**: `lockLevels` faz `SELECT … FROM inventory_balance … ORDER BY product_id FOR UPDATE` e só então lê os níveis em **nova instrução**; sob READ COMMITTED essa leitura enxerga as reservas confirmadas pelo detentor anterior do lock. Por isso reservar, liberar e movimentar estoque declaram `Isolation.READ_COMMITTED`.
- **Serviço interno `InventoryReservationService`** (sem endpoint): `reserve` (tudo ou nada; variantes ordenadas; `MISSING` se alguma variante não pertence ao tenant; `INSUFFICIENT_STOCK` sem revelar saldo; `REQUEST_CONFLICT` se o pedido já tem reservas), `release` (idempotente; reserva já vencida é registrada como `EXPIRED`), `expireDue(limit)` (lote com `FOR UPDATE SKIP LOCKED`, sem lock de saldo porque reservas vencidas já não contam) e `consume` (stub documentado que lança `UnsupportedOperationException`, pois a Sprint 4 precisa de tipo de movimento de saída e ator de sistema). Prazo em `inventory.reservation.ttl`, padrão `PT30M` (D1), validado entre 1 min e 72 h.
- **D7**: `InventoryService.moveAuthorized` verifica, sob o lock do saldo e após a checagem de idempotência, se o novo físico ficaria abaixo do reservado → `409 {"code":"RESERVED_STOCK","reserved":N}`. Saldo negativo continua `INSUFFICIENT_STOCK`; entradas e ajustes positivos não mudam.
- **Admin**: `InventoryView` acrescenta `reserved` e `available` (contrato aditivo; `quantity` segue sendo o físico). A tela de estoque mostra físico, reservado e disponível em `<dl>`, atualiza o texto explicativo e mostra mensagem específica para `RESERVED_STOCK` com a quantidade reservada, recarregando os níveis após `RESERVED_STOCK`/`INSUFFICIENT_STOCK`.
- **Vitrine**: produto e variantes usam `inventory_available_quantity(...) > 0`. Contratos públicos inalterados (continua apenas booleano `available`, sem quantidades).

## Comandos e resultados (executados nesta worktree)

| Comando | Resultado |
| --- | --- |
| `backend: mvnw.cmd -B -ntp verify` (JDK Corretto 21.0.9, Docker/Testcontainers) | **BUILD SUCCESS** em 10:53 min. Surefire: 39 testes, 0 falhas. Failsafe: 62 testes, 0 falhas (inclui `InventoryReservationIT` 8, `InventoryReservationMigrationIT` 1, `InventoryIsolationIT` 6, `StorefrontIsolationIT` 4, ArchUnit 3 no Surefire). Spotless check incluído. |
| `admin-web: npm ci` | 354 pacotes do lockfile; nenhuma dependência nova. |
| `admin-web: npm run lint` | exit 0, sem avisos. |
| `admin-web: npm run typecheck` | exit 0. |
| `admin-web: npm run build` | exit 0 (build nesta worktree; nenhum `next start` rodava aqui). |
| `admin-web: SMOKE_PORT=3105 npm run test:smoke` | exit 0, "Smoke aprovado". |

Uma execução anterior, limitada às classes novas, falhou em 3 testes por erro no próprio teste (o detalhe público aninha `product`); corrigido antes do `verify` completo acima.

## Cobertura dos testes exigidos

| Exigência (SPRINT_03 SC-005) | Teste |
| --- | --- |
| Unidade com `Clock` fixo: antes / exatamente / depois do prazo | `ReservationPolicyTest` (30 min a partir do `Clock`, precisão de µs, ativo só antes do prazo); `InventoryReservationServiceTest` (prazo e ordenação com `Clock.fixed`, rejeição total, entradas inválidas, stub de consumo) |
| Mesma fronteira na função SQL | `InventoryReservationIT.availabilityExcludesReservationsBefore…` (prazo − 1 µs conta; no prazo e + 1 µs não contam; sem saldo → 0) |
| N threads pela última unidade | `exactlyOneOfManyConcurrentOrdersReservesTheLastUnit` (12 threads: 1 vence, 11 `INSUFFICIENT_STOCK`, disponível 0, uma linha) |
| Ordem de locks sem deadlock | `crossedVariantOrdersLockInVariantOrderWithoutDeadlock` (25 rodadas com pedidos [x,y] × [y,x] simultâneos, sem falhas) |
| Ajuste negativo × reserva concorrente | `negativeAdjustmentAndConcurrentReservationNeverLeavePhysicalBelowReserved` (10 rodadas: sempre exatamente um vence — `OK/RESERVED_STOCK` ou `INSUFFICIENT_STOCK/OK` — e físico ≥ reservado) |
| D7 via HTTP + admin | `InventoryIsolationIT.negativeAdjustmentBelowReservedReturnsReservedStock…` (409 com `reserved`, níveis no GET, retirada até o disponível permitida, liberação restaura) |
| Aceite: vitrine/admin refletem e recuperam após o prazo sem rotina; ledger intacto | `storefrontAndAdminReflectReservations…` e `releaseIsIdempotent…` |
| Migração preserva saldos/movimentos | `InventoryReservationMigrationIT` (migra até V10, grava saldo e 2 movimentos, migra até a última, compara linhas idênticas) |
| A/B entre tenants | `reservationsAreIsolatedByTenantEvenWithSameSkuAndViaDirectSql`: reserva em A não altera B com mesmo SKU (API e vitrine); serviço com contexto A rejeita variante de B (`MISSING`, sem linhas, inclusive em pedido misto); `levels` de A não retorna variante de B; FK composta rejeita INSERT e UPDATE cruzados via SQL direto. `release` com tenant B não libera pedido de A. OWNER B recebe 404 no estoque de A. |
| CHECKs do schema | `schemaRejectsInvalidQuantitiesStatesWindowsAndDuplicatedOrderLines` |

## Revisão de segurança multi-tenant (checklist SECURITY/REVIEWER)

- Tenant de reservas vem só do chamador interno (contexto confiável); toda consulta e escrita filtra `tenant_id`, e a FK composta impede variante de outro tenant mesmo por SQL. Exceção deliberada: `expireDue` percorre todos os tenants, mas só altera status/`closed_at` da própria linha.
- Sem endpoint público novo; a vitrine continua expondo só o booleano. O 409 `RESERVED_STOCK` mostra o reservado apenas ao OWNER autenticado do próprio tenant (acesso alheio = 404 uniforme).
- Nenhum dado pessoal, token ou SQL em logs/respostas. Sem dependências novas, sem infraestrutura nova.

## Pendências e riscos

1. **FK de `order_id`**: sem FK porque `sales_order` só nasce em SC-008. SC-008 deve adicionar `FOREIGN KEY (order_id, tenant_id) REFERENCES sales_order(id, tenant_id)` (exige `UNIQUE (id, tenant_id)` em `sales_order`). Até lá, reservas só podem ser criadas pelo serviço interno.
2. **Bean `Clock` global** (`config/ClockConfiguration`): outras branches não devem declarar outro `Clock`, ou a injeção fica ambígua. Registrado nas notas compartilhadas para o coordenador.
3. **SC-007 (cotação)**: deve usar `inventory_available_quantity(...)` com o `Clock`, nunca `inventory_balance.quantity`, para não regredir a regra única (risco do ADR-0012).
4. **SC-008**: o checkout que chamar `reserve` deve rodar em READ COMMITTED (REPEATABLE READ veria um snapshot antigo após o lock).
5. **SC-010**: a rotina agendada e a expiração do pedido não foram implementadas; `expireDue` já está pronto para ela.
6. **Sprint 4**: `consume` é stub; exige movimento de saída no ledger e decisão sobre ator de sistema.
7. **Limitações de verificação**: não validei a tela no navegador (os servidores 3000/3001/8080 não foram tocados e não subi cópias); a skill `web-design-guidelines` não estava disponível no catálogo desta sessão, então a revisão de UI/UX e a acessibilidade manual (WCAG 2.2 AA) ficam pendentes para QA. A UI usa só HTML semântico (`<dl>`), tokens existentes e os papéis `alert`/`status` já em uso.
8. README, ARCHITECTURE, DATA_MODEL, SPRINT_03 e ADR-0012 não foram editados, conforme instruído; o coordenador consolida.
