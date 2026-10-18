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

## Navegador com API real

Em 2026-10-18, com autorização do responsável, backend, admin e vitrine foram reiniciados com o código integrado (`next build` + `next start`; os servidores anteriores, iniciados às 07:58 por outra sessão, foram encerrados). O Flyway validou 12 migrações e aplicou V11 e V12 ao banco local sem erros. Fixture: conta, loja `validacao-adr0011` e produto "Camiseta Validação" da validação do ADR-0011.

- **Entrega (SC-006):** a seção Entrega aparece nos detalhes da loja, no layout redesenhado. Retirada com instruções e frete fixo de 12,50 com área atendida foram salvos ("Entrega salva."), reapareceram após recarregar (valor exibido como "12,50") e a API pública passou a listar só as opções ativas (`PICKUP` e `FIXED_SHIPPING` 12.50 BRL), com acentos corretos em UTF-8.
- **Estoque com reservado (SC-005):** como reservas só nascem com pedidos (SC-008), foi inserida no banco local uma reserva sintética de 3 unidades da combinação Azul · M (5 físicas), expirando em 15 minutos. A tela de estoque mostrou físico 5, reservado 3 e disponível para venda 2. Uma retirada de 3 unidades foi bloqueada (D7) com a mensagem "Há 3 unidades reservadas por pedidos pendentes…", sem alterar o saldo.
- **Carrinho (SC-007 + ajuste de integração):** na página do produto, com seletores por atributo do redesenho, "Adicionar ao carrinho" com 3 unidades levou ao carrinho com "Quantidade indisponível. Reduza a quantidade." e subtotal R$ 0,00. Com 2 unidades: "Disponível", R$ 105,00. O cookie do carrinho não é visível a `document.cookie` (HttpOnly). Depois de antecipar o vencimento da reserva sintética no banco, ela continuou com status ACTIVE (a rotina de expiração é da SC-010), mas o disponível voltou a 5 e o carrinho aceitou 5 unidades por R$ 262,50, confirmando a expiração lógica sem rotina.

Interação com o painel do navegador: com o painel oculto, a página fica com viewport 0×0, `requestAnimationFrame` não dispara e o React não revela o conteúdo transmitido por streaming; nesse estado, os campos da entrega foram preenchidos por eventos de DOM. Com o painel visível, o carrinho foi exercitado com cliques reais. A reserva sintética permanece no banco local, vencida e sem efeito na disponibilidade, até a rotina da SC-010 existir.

## Limitações e pendências

- Axe, leitor de tela e avaliação WCAG completa não executados.
- Reservas só nascem com o pedido (SC-008); até lá o caminho de reserva é exercitado pelos testes. FK composta `order_id → sales_order` pendente para a SC-008.
- SC-008 a SC-011 não iniciadas e dependem de autorização. Textos de privacidade (D2) e validação jurídica pendentes antes de produção.
- SEC-003 permanece aberta e inalterada, sem aceite de risco.
