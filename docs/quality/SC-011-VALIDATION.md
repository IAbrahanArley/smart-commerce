# Validação: SC-011, revisão integrada de segurança, LGPD e QA (Sprint 3)

Data: 2026-10-19. Branch `feat/sc-011-integrated-review`, a partir da `main` em `5c1acd3` (SC-005 a SC-010 integradas). Escopo: [SPRINT_03.md](../product/SPRINT_03.md) e [ADR-0012](../architecture/adr/0012-stock-reservation-cart-orders.md). O modelo de ameaças está em [SC-011-CHECKOUT-THREAT-MODEL.md](../security/SC-011-CHECKOUT-THREAT-MODEL.md).

## Como a revisão foi feita

- **Duas revisões independentes, somente leitura, sobre o fluxo inteiro da `main`** (não sobre um diff):
  - **backend:** inventory, tenant, storefront, orders, bootstrap e as migrações V11 a V15, usando [SECURITY.md](../agents/SECURITY.md) e [REVIEWER.md](../agents/REVIEWER.md);
  - **frontends:** vitrine (carrinho, checkout, acompanhamento) e admin (estoque, entrega, pedidos), usando SECURITY.md e [QA.md](../agents/QA.md).
- Achados conferidos no código pelo coordenador antes de corrigir.
- **Resultado:** nenhum achado crítico nas duas revisões; um major no backend e um major de cobertura de testes nos frontends.

## Achados e disposição

| Origem | Severidade | Achado | Disposição |
| --- | --- | --- | --- |
| Backend | Major | Cada endereço IPv6 era um balde separado no limite por IP; IPv4 mapeado não era unificado. Um /64 burlava o D4 e podia reter estoque escasso | **Corrigido**: `ClientIpBucket` agrupa IPv6 por /64 e converte IPv4 mapeado, sem DNS; testes `ClientIpBucketTest` e `OrderCheckoutIT.signedIpv6AddressesOfOneSlash64ShareTheAttemptLimit` |
| Backend | Menor | Sem proxy confiável declarado, todos os compradores compartilham o balde do servidor da vitrine | **Documentado** como requisito antes de produção (R1); o backend não enxerga a configuração da vitrine |
| Backend | Menor | Anonimização limitada a 2.000 pedidos por dia; rotinas desligadas sem aviso | **Corrigido**: cada execução esvazia a fila dentro de um limite de tempo (30 s na expiração, 10 min na anonimização diária); aviso na inicialização com checkout ligado e rotinas desligadas; teste `OrderMaintenanceJobTest` |
| Backend | Menor | `cancel_reason` (texto livre da loja) permanecia após a anonimização | **Corrigido**: substituído por texto fixo, mantendo as CHECKs da V15; teste em `OrderLifecycleIT` |
| Backend | Menor | `cancelled_by` em RESTRICT impede apagar a identidade do lojista | **Registrado**: não existe exclusão de identidade hoje; decidir junto com a futura exclusão de conta |
| Backend + frontends | Menor | Cotação revela o saldo exato até 10 unidades, sem limite de taxa | **Aceito** em 2026-10-20 (R2, ADR-0012) |
| Backend | Menor | Número sequencial do pedido revela o volume da loja | **Aceito** em 2026-10-20 (R6, ADR-0012) |
| Frontends | Major | O teste de origem externa no checkout enviava um formulário vazio, então nunca executava a action | **Corrigido**: o smoke envia a action real com outra origem e verifica que nenhum pedido foi criado e nenhum cookie foi alterado |
| Frontends | Menor | Foco perdido ao fechar o formulário de cancelamento | **Corrigido**: volta ao botão "Cancelar pedido" |
| Frontends | Menor | Aviso do carrinho após o redirecionamento podia não ser anunciado | **Corrigido**: `FocusNotice` foca o aviso ao montar |
| Frontends | Menor | Mensagem de replay ("pedido já registrado") com estilo de erro | **Corrigido**: estilo informativo e `role="status"`; verificado no smoke |
| Frontends | Menor | Tabelas responsivas perdem semântica com `display: block` | **Corrigido** nas tabelas de pedidos com papéis ARIA explícitos |
| Frontends | Menor | IDs de rota do admin sem validação de formato | **Corrigido**: só UUIDs canônicos passam (layout da loja e detalhe do pedido); verificado no smoke |
| Frontends | Menor | O smoke do admin não exercita os comportamentos da Sprint 3 (duplo envio, 409, ausência de PII no HTML) | **Pendente**: fica para a etapa de testes de navegador (Playwright e Axe, ainda não autorizados) |

## FK reserva → pedido (V16)

- A V16 valida `inventory_reservation_order_tenant_fk` quando não há reserva sem pedido do mesmo tenant.
- Em bancos locais antigos com reservas de antes dos pedidos, a FK fica `NOT VALID` com um aviso, a migração conclui e nenhuma linha é apagada.
- Teste: `SalesOrderMigrationIT` com 2 cenários. O banco limpo termina com `convalidated = true`. O banco com reserva antiga termina com `false` e um aviso no log, sem perder dados.

## Verificações executadas

**Backend** (Corretto 21.0.9, PostgreSQL 18.6 em Testcontainers)
- `mvnw.cmd -B -ntp verify -Dspring-boot.repackage.skip=true`: BUILD SUCCESS em 2026-10-19 09:43:34 -03:00.
- **162 testes** (67 unitários e arquiteturais, 95 de integração), zero falhas, erros ou ignorados. ArchUnit e Spotless limpos.
- Log local: `backend/target/sc011-final-verify.log`.

**Testes A/B de isolamento da sprint, reexecutados nesse `verify`, todos aprovados:**
- `TenantIsolationIT` (6) e `FulfillmentIsolationIT` (6);
- `CatalogIsolationIT` (11) e `StorefrontIsolationIT` (4);
- `InventoryIsolationIT` (6) e `CartQuoteIT` (4);
- `OrderCheckoutIT` (12) e `OrderLifecycleIT` (9).

**Frontends**
- Vitrine: `npm run lint`, `typecheck`, `build` e `SMOKE_PORT=3042 npm run test:smoke` aprovados, com o checkout ligado e desligado.
- Admin: lint, typecheck, build e `SMOKE_PORT=3052 npm run test:smoke` aprovados.
- No smoke do admin, o ID de rota inválido pode responder 200, porque o `loading.tsx` da raiz faz streaming antes do `notFound()`. A verificação que vale é que a tela de pedidos, que chamaria a API, não é montada.

**SEC-003** (auditoria de 2026-10-19 nos lockfiles da `main`)
- Continuam 5 entradas altas só na cadeia de lint de cada frontend e zero em produção.
- `braces` 3.0.3 é a versão mais recente publicada e está dentro da faixa afetada.
- A SEC-003 segue **aberta**, sem exceção aprovada.

## Critérios de aceite da SC-011

| Critério | Situação |
| --- | --- |
| Testes A/B das tarefas anteriores executados e registrados | Executado/aprovado (lista acima) |
| Nenhum achado crítico aberto | Executado/aprovado: nenhum crítico; os dois majors foram corrigidos |
| SEC-003 registrada como aberta, sem aceite implícito | Executado/aprovado |
| ARCHITECTURE, DATA_MODEL e status do ADR-0012 atualizados | Executado/aprovado |

## Limitações e pendências

- **Decisões humanas:** R2 (inferência de saldo pela cotação) e R6 (números sequenciais do pedido) foram aceitos em 2026-10-20, com revisão antes da produção comercial (ADR-0012).
- **Antes de produção:** proxy confiável com `STOREFRONT_TRUST_FORWARDED_FOR=true` e segredo nas duas aplicações (R1), validação jurídica do D2 e pagamento (Sprint 4).
- **Acessibilidade:** sem Axe nem leitor de tela automatizados, e o smoke do admin é raso. WCAG 2.2 AA é objetivo, não conformidade comprovada.
- **CI remota:** depende do push desta branch.
