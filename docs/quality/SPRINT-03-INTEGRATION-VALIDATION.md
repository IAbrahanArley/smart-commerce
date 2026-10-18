# Validação local — Integração de SC-005, SC-006 e SC-007

Data: 2026-10-18. Branch `feat/inventory-storefront-catalog-media`. Escopo: [ADR-0012](../architecture/adr/0012-stock-reservation-cart-orders.md) (D1–D7 aprovadas) e [SPRINT_03.md](../product/SPRINT_03.md). Sem push nem CI remota.

## O que foi integrado

- Redesenho de UI aprovado ([proposta](../design/PROPOSTA-REFERENCIAS-MERCADO.md)) encontrado sem commit na árvore de trabalho; commitado separadamente como `1009248` por decisão do responsável, antes das merges.
- SC-005 reservas (`4f81098`, [validação](SC-005-VALIDATION.md)), SC-006 entrega (`2fb6f72`, [validação](SC-006-VALIDATION.md)) e SC-007 carrinho (`693972e`, [validação](SC-007-VALIDATION.md)), desenvolvidas em paralelo em worktrees próprias a partir de `7a9247d`.

## Conflitos resolvidos

- `admin-web/scripts/smoke.mjs` (redesenho × SC-005 × SC-006): mantidos os imports de inventário e entrega e a mensagem final cobrindo todos os cenários.
- `admin-web/src/components/store-detail.tsx`: mantidos `PageHeader` e `StatusBadge` do redesenho e a seção `StoreFulfillment` da SC-006.
- `storefront-web/src/components/product-offer.tsx` e página do produto: mantidos os seletores por atributo e a região "Combinação escolhida" do redesenho, mais o formulário de quantidade e "Adicionar ao carrinho" da SC-007 (`slug` passado pela página, layout de galeria/descrição do redesenho).
- `BootstrapSecurityConfiguration` mesclou sem conflito: a cadeia pública permite os GET existentes, `GET /api/public/stores/*/fulfillment` e somente `POST /api/public/stores/*/cart/quote`.

## Ajuste de integração

A cotação do carrinho (SC-007) usava o saldo físico, como previsto no plano paralelo. `CartQuoteRepository` passou a usar `inventory_available_quantity(v.tenant_id, v.id, :now)` com o instante do `Clock` do backend (mesmo padrão da vitrine na SC-005), e `CartQuoteService` recebe o `Clock`. Regressão `CartQuoteIT.quoteUsesAvailableStockNetOfActiveReservationsOnly`: reserva ativa de 5 sobre 7 unidades torna 3 INSUFFICIENT e 2 AVAILABLE; reserva expirada não conta; reserva totalmente consumida no tenant B torna a linha UNAVAILABLE em B sem afetar A; saldo físico intacto. Falhou antes do ajuste (AVAILABLE em vez de INSUFFICIENT, log `backend/target/integration-quote-red.log`) e passou depois (`integration-quote-green.log`). Uma expectativa do próprio teste estava errada na primeira versão (zero disponível é UNAVAILABLE pela regra `CartLineStatus.of`); corrigida a expectativa, não o código.

As opções de entrega ainda não entram na cotação; a junção fica para a SC-008, no ponto de extensão documentado em `CartQuoteService`.

## Verificações executadas

- **Backend:** `mvnw.cmd -B -ntp verify -Dspring-boot.repackage.skip=true` com Corretto 21.0.9 e PostgreSQL 18.6/Testcontainers; BUILD SUCCESS em 2026-10-18 08:21:46 -03:00. 128 testes: 56 unitários/arquiteturais e 72 integrações, zero falhas, erros ou ignorados; Spotless limpo. Log: `backend/target/integration-full-verify.log`. O repackage foi pulado porque o backend local executa a partir de `target/`; a CI empacota em runner limpo.
- **Admin e vitrine:** `npm ci`, `npm run lint`, `npm run typecheck`, `npm run build` e `npm run test:smoke` aprovados em cópias isoladas (mesmo código e lockfiles), para não substituir `.next` dos servidores `next start` em execução. O smoke do admin cobre entrega, novo produto com navegação da loja e mensagem RESERVED_STOCK; o da vitrine cobre carrinho (cookie saneado e por loja, cotação do backend, atributos do cookie e rejeição de origem externa) e ausência de credenciais no upstream.

## Limitações e pendências

- Nenhuma das três funcionalidades nem o redesenho foram exercitados em navegador com a API real nesta integração; os servidores locais (8080/3000/3001) ainda executam o código anterior e precisam ser reiniciados para refletir V11/V12 e as telas novas. Axe, leitor de tela e avaliação WCAG completa não executados.
- Reservas só nascem com o pedido (SC-008); até lá o caminho de reserva é exercitado pelos testes. FK composta `order_id → sales_order` pendente para a SC-008.
- SC-008 a SC-011 não iniciadas e dependem de autorização. Textos de privacidade (D2) e validação jurídica pendentes antes de produção.
- SEC-003 permanece aberta e inalterada, sem aceite de risco.
