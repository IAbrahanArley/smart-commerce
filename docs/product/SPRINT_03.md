# Sprint 3 — Carrinho, pedidos e frete básico (planejamento)

Data: 2026-10-17. **D1–D7 aprovadas em 2026-10-17. SC-005, SC-006 e SC-007 entregues e integradas em 2026-10-18 ([validação](../quality/SPRINT-03-INTEGRATION-VALIDATION.md)). SC-008 autorizada e entregue em 2026-10-18 ([validação](../quality/SC-008-VALIDATION.md)), com as decisões complementares registradas no ADR-0012. SC-009 autorizada e entregue em 2026-10-18 ([validação](../quality/SC-009-VALIDATION.md)). SC-010 autorizada e entregue em 2026-10-18, com as decisões registradas no ADR-0012 ([validação](../quality/SC-010-VALIDATION.md)); SC-011 autorizada e entregue em 2026-10-19 ([validação](../quality/SC-011-VALIDATION.md), [modelo de ameaças](../security/SC-011-CHECKOUT-THREAT-MODEL.md)); os riscos residuais R2 e R6 aguardam decisão humana.** Base técnica: [ADR-0012](../architecture/adr/0012-stock-reservation-cart-orders.md) (adotado). A vitrine de consulta prevista no [ROADMAP](ROADMAP.md) para esta sprint já foi entregue ([ADR-0010](../architecture/adr/0010-public-storefront.md)); esta sprint a estende com compra até `PENDING_PAYMENT`.

**Objetivo:** comprador escolhe variantes, monta carrinho, informa dados e entrega (retirada ou frete fixo) e gera pedido `PENDING_PAYMENT` com reserva de estoque que expira; lojista vê e cancela pedidos. Sem pagamento, baixa definitiva ou Mercado Pago (Sprint 4).

## Gates de entrada

Nenhuma tarefa começa sem autorização explícita do responsável. Decisões do ADR-0012 que bloqueiam cada tarefa:

| Tarefa | Requer aprovado |
| --- | --- |
| SC-005 | D1 (prazo), D7 (ajuste abaixo do reservado) |
| SC-006 | D3 (frete) |
| SC-007 | D5 (carrinho) |
| SC-008 | D1, D2 (dados/LGPD), D4 (anti-abuso), SC-005, SC-006 |
| SC-009 | D2, D6 (checkout só em DEV), SC-007, SC-008 |
| SC-010 | SC-008 |
| SC-011 | SC-005 a SC-010 |

SC-005, SC-006 e SC-007 são independentes entre si (arquivos distintos: inventory, tenant, storefront-web/storefront) e podem ser feitas em paralelo. SC-004 (segurança e observabilidade da Sprint 1) segue no seu plano original e não é absorvida aqui.

## SC-005 — Reserva e disponibilidade por variante (inventory)

- Migração aditiva `inventory_reservation` (FKs compostas, índice parcial, CHECKs de quantidade/estado); nenhuma alteração de `inventory_balance` ou do ledger.
- Função única de repositório para disponível = `on_hand − reservas ACTIVE não expiradas`, sob lock ordenado por `variant_id`; serviço interno de reservar/liberar/expirar para uso do módulo orders (sem endpoint público próprio).
- Vitrine passa a usar disponível > 0; admin de estoque mostra físico, reservado e disponível; ajuste negativo abaixo do reservado segue D7.

**Aceite:** com reserva ativa, vitrine e admin refletem o disponível; após o prazo, o estoque volta a ficar disponível sem depender da rotina; ledger não recebe movimentos de reserva.

**Testes:** unidade do cálculo com `Clock` fixo (antes/exatamente no/depois do prazo); integração PostgreSQL com N threads reservando a última unidade (exatamente uma vence, disponível nunca negativo); ordem de locks sem deadlock com dois pedidos de variantes cruzadas; ajuste negativo × reserva concorrente; migração preserva saldos/movimentos existentes. **A/B:** reserva no tenant A não altera disponível no tenant B nem de variante homônima/SKU igual; serviço rejeita reservar variante de B com contexto de A; FK composta impede reserva cruzada mesmo via SQL direto.

## SC-006 — Configuração de entrega da loja (tenant + admin)

- Migração aditiva em `store` (ou tabela 1:1) com retirada/instruções e frete fixo/valor/área conforme D3; `NUMERIC` com CHECKs do catálogo.
- `GET`/`PUT /api/tenants/{tenantId}/fulfillment` com OWNER, CSRF, origem e versão otimista; tela no admin seguindo DESIGN.md.
- API pública expõe apenas opções ativas, valor e textos.

**Aceite:** lojista ativa retirada e/ou frete fixo, salva, recarrega e vê o mesmo valor; loja sem opção ativa não aceita checkout (verificado em SC-008).

**Testes:** valores negativos, escala > 2, texto com controles/excesso; conflito de versão; valor zero permitido. **A/B:** OWNER A não lê nem altera configuração de B (404 uniforme); API pública de B nunca mostra configuração de A; loja em rascunho não expõe configuração.

## SC-007 — Carrinho na vitrine e cotação (storefront + storefront-web)

- `POST /api/public/stores/{slug}/cart/quote` sem efeito colateral; cadeia pública passa a aceitar esse POST, stateless, JSON estrito e corpo limitado.
- storefront-web: adicionar/alterar/remover itens via route handlers/server actions; cookie `HttpOnly` por loja com limites do ADR-0012; página de carrinho SSR com estados de indisponibilidade e opções de entrega.

**Aceite:** comprador adiciona combinação na página do produto, vê subtotal e frete cotados pelo backend, altera quantidade e remove; despublicar produto torna a linha indisponível na próxima cotação.

**Testes:** cookie adulterado (UUID inválido, quantidade 0/11/negativa, 21 linhas, JSON inválido, > 4 KiB) é rejeitado ou saneado sem 500; cotação nunca expõe custo, SKU, quantidade exata ou IDs internos; preço sempre do backend; smoke SSR sem encaminhar cookies/Authorization. **A/B:** variante de loja B cotada no slug A retorna `UNAVAILABLE` indistinguível de inexistente; cookie de carrinho da loja A não é lido em `/lojas/B`; slug de loja em rascunho retorna 404.

## SC-008 — Checkout e pedido `PENDING_PAYMENT` (orders)

- Novo módulo `orders` (controllers/services/repositories/entities/domain/DTOs, ADR-0008); migrações aditivas `sales_order` e `sales_order_item` com snapshots, numeração por loja, `request_id`/`request_fingerprint` e `access_token_hash`.
- `POST /api/public/stores/{slug}/orders`: valida loja/variantes publicadas, entrega ativa, dados do comprador (D2), `expectedTotal`; cria pedido, itens e reservas numa transação; limites de abuso (D4).
- `GET /api/public/stores/{slug}/orders/{orderId}` com token de acesso.

**Aceite:** checkout válido cria pedido com número, total = itens + frete, prazo de reserva visível e estoque reservado; repetição com o mesmo `requestId` devolve o mesmo pedido; mudança de preço entre cotação e checkout retorna `PRICE_CHANGED` sem gravar.

**Testes:** idempotência (mesmo payload → `replayed=true` sem segunda reserva; payload diferente → `REQUEST_CONFLICT`; falha por estoque não consome a chave); concorrência pela última unidade com checkouts distintos e com o mesmo `requestId`; snapshot imutável após alterar preço/custo/nome no catálogo; custo ausente gravado como nulo; precisão monetária sem arredondamento; frete exigindo endereço só em entrega; limites D4 atômicos sob concorrência; logs sem dados pessoais/token; token armazenado só como hash. **A/B:** pedido no slug A com variante de B rejeitado sem criar nada; mesmo `requestId` em A e B gera pedidos independentes; token de pedido de A não abre pedido de B nem outro pedido de A; pedido de A nunca referencia loja/variante de B (FKs compostas).

## SC-009 — Checkout e acompanhamento na vitrine (storefront-web)

- Formulário de dados e entrega com aviso de privacidade (texto aprovado em D2), resumo cotado, confirmação explícita, preservação do `requestId` em resultado ambíguo e bloqueio de envio duplo.
- Página de confirmação/status do pedido com prazo da reserva e aviso de que o pagamento não está disponível nesta etapa; habilitação apenas por configuração DEV (D6).
- Revisão de UX/acessibilidade conforme [QA.md](../agents/QA.md) e WCAG 2.2 AA como objetivo, sem presumir conformidade.

**Aceite:** comprador conclui o fluxo carrinho → dados → pedido → status em DEV; com configuração desativada, nenhum botão ou rota de checkout aparece.

**Testes:** smoke SSR do fluxo completo; erros de validação associados aos campos; `PRICE_CHANGED`/`INSUFFICIENT_STOCK` apresentados com nova cotação; retry após falha de rede reutiliza a chave; nenhum dado pessoal em URL, `localStorage` ou logs do Next. **A/B:** cookie de pedido de A não exibe pedido de B; status de pedido em outra loja retorna página 404.

## SC-010 — Expiração, cancelamento e pedidos no admin

- Rotina agendada em lotes com `SKIP LOCKED` que marca reservas e pedidos vencidos como `EXPIRED`; anonimização de não pagos conforme retenção aprovada em D2.
- Admin: lista paginada por cursor, filtro por status, detalhe com itens/snapshots/entrega/dados do comprador e cancelamento com motivo (libera reservas, idempotente).

**Aceite:** pedido vencido aparece como expirado e libera estoque; lojista cancela pedido pendente e o disponível volta imediatamente; cancelar pedido expirado ou cancelado não tem efeito duplicado.

**Testes:** rotina reexecutada/duas instâncias simultâneas sem dupla liberação; corrida cancelamento × expiração × novo checkout; CAS de estado; anonimização preserva valores e itens; horários exibidos em America/Sao_Paulo. **A/B:** OWNER A não lista, lê nem cancela pedido de B (404 uniforme); rotina processa A e B sem cruzar tenant; filtros/cursor de A nunca retornam linhas de B.

## SC-011 — Revisão integrada de segurança, LGPD e QA

- Checklists de [SECURITY.md](../agents/SECURITY.md), [QA.md](../agents/QA.md) e [REVIEWER.md](../agents/REVIEWER.md) sobre todo o fluxo; modelo de ameaças do checkout público (abuso de estoque, enumeração, adulteração de cookie, inferência de saldo).
- Registro de validação em `docs/quality/` com comandos e resultados reais; atualização de ARCHITECTURE/DATA_MODEL e status do ADR-0012.

**Aceite:** todos os testes A/B das tarefas anteriores executados e registrados; nenhum achado crítico aberto; SEC-003 continua registrada como aberta, sem aceite implícito.

## Definition of Done

Testes executados (unidade, integração PostgreSQL/Testcontainers, ArchUnit, smoke SSR), lint/build sem falhas, migrações aditivas versionadas, documentação e registro de validação atualizados, revisão de segurança multi-tenant e aprovação humana. Sem dependência nova, serviço pago, integração real, deploy, commit ou push sem autorização.

## Fora da sprint

Mercado Pago (OAuth, Checkout Pro, webhooks, `PAID`, baixa de estoque, conciliação, reembolso), e-mail ao comprador, descontos, frete por CEP/transportadora, conta do comprador e carrinho abandonado — ver seção 9 do ADR-0012.
