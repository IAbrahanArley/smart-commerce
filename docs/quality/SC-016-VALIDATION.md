# SC-016 — Reembolso controlado: validação

Data: 2026-10-21. Base: [ADR-0013](../architecture/adr/0013-mercado-pago-checkout-pro.md) (P9) e [SPRINT_04.md](../product/SPRINT_04.md).

**Estado:** implementada e validada com a **Orders API falsa** em loopback, incluindo `POST /v1/orders/{id}/refund`. Nenhum reembolso real foi feito. A validação real está no lembrete do [SPRINT_04.md](../product/SPRINT_04.md).

> **Atualização (2026-10-24):** o Checkout Pro passou da Orders API para a API de preferências ([ADR-0013, mudança da P1](../architecture/adr/0013-mercado-pago-checkout-pro.md#mudança-da-p1-checkout-pro-pela-api-de-preferências-aprovada-em-2026-10-24)). Os testes citados aqui foram adaptados aos endpoints novos; este registro descreve a validação original.

## Critérios de aceite

| Critério | Evidência (`PaymentConfirmationIT`) |
| --- | --- |
| "Reembolsar" só em pedido `PAID`, com motivo, confirmação em duas etapas e opção explícita "devolver ao estoque" | backend: `refundGuardsConflictsProviderFailuresAndOtherStores` (pedido pendente: `NOT_REFUNDABLE`) e `restock` obrigatório no endpoint; admin: `order-refund.tsx` (motivo e escolha, depois resumo e confirmação) |
| `X-Idempotency-Key` por pedido | `refundIsAskedOnceConfirmedOnlyByTheProviderAndRestocksExactlyOnce`: as chamadas usam a mesma chave e o token da loja |
| `REFUNDED` só confirmado pelo provedor, uma única vez | o mesmo teste: o pedido continua `PAID` até a order do provedor aparecer `refunded`; conciliação, webhook e nova conciliação levam a um único `REFUNDED` |
| Devolução ao estoque só se escolhida (`SALE_REVERSAL`) | o mesmo teste (um `SALE_REVERSAL` de +2, ator de sistema) e `refundWithoutRestockOrForAStocklessOrderNeverTouchesStock` (sem escolha, ou pedido pago sem estoque: nada volta) |
| Clique duplo e nova tentativa | o primeiro teste (um único `payment_refund`) e o teste de guardas (falha do provedor, depois nova tentativa com a mesma chave) |
| Erro "já reembolsado" | o teste de guardas: 409 `order_already_refunded` é tratado como aceito e confirmado pela consulta |
| Corrida com o webhook | o primeiro teste: conciliação e webhook sobre o mesmo reembolso, com uma única transição e uma única devolução |
| Auditoria de quem reembolsou | `payment_refund.requested_by`, `requested_at` e `reason`, conferidos no primeiro teste |
| A/B: o OWNER de A não reembolsa pedido de B | o teste de guardas: o OWNER B no pedido de A (pela loja A ou B) recebe `MISSING`, sem chamar o provedor |

## O que foi entregue

- **Migração `V21__payment_refund.sql` (aditiva):**
  - estado `REFUNDED` em `sales_order` (com `refunded_at`) e em `payment`, preservando `paid_at` e o histórico de expiração ou cancelamento;
  - tabela `payment_refund`, uma por pagamento: motivo de 3 a 300 caracteres, `restock`, `requested_by`, `requested_at`, aceite do provedor, último erro e confirmação.
- **`MercadoPagoOrdersClient.refundOrder`:** `POST /v1/orders/{id}/refund` com corpo `{}` (reembolso total), o token da loja e `X-Idempotency-Key` derivado do pedido. As respostas 409 `order_already_refunded`, `cannot_refund_order`, `order_refund_already_in_process` e `idempotency_key_already_used` contam como pedido aceito.
- **`PaymentRefundService`:**
  1. sob o lock do pagamento (que precisa estar `APPROVED`), registra o reembolso uma vez; uma escolha diferente sobre o estoque é conflito;
  2. chama o provedor fora de transação e registra o aceite ou o erro;
  3. consulta a order logo em seguida. A conciliação continua consultando a cada minuto até confirmar.
- **`PaymentConfirmationService`:** para um pagamento `APPROVED` com reembolso pendente, só uma order reportada `refunded` leva a `REFUNDED`. Isso acontece sob o lock do pagamento, junto com a confirmação do reembolso e o `PaymentListener.refunded`.
- **`OrderPaymentApproval.refunded`:** muda o pedido de `PAID` para `REFUNDED`. Se a loja escolheu devolver ao estoque, `InventoryReservationService.reverseSale` devolve exatamente o que os movimentos `SALE` tiraram, com `SALE_REVERSAL` de ator de sistema e chave única por pedido e variante.
- **`OrderRefundService` e `POST /api/tenants/{t}/orders/{o}/refund`:** sessão, CSRF e OWNER. O corpo tem `reason` e `restock` (obrigatório). Erros: 409 `NOT_REFUNDABLE` ou `REFUND_CONFLICT`, 409 `PAYMENT_UNAVAILABLE` e 502 `PAYMENT_FAILED`.
- **Admin:**
  - card "Reembolso" no pedido pago: motivo e escolha do estoque, depois resumo do valor, do estoque e do motivo, e por fim "Confirmar reembolso";
  - estado "aguardando a confirmação do Mercado Pago", com "Tentar o reembolso novamente" após erro;
  - pedido reembolsado com data, motivo e destino do estoque;
  - filtro e rótulo "Reembolsado".
- **Vitrine:** situação "Pedido reembolsado".
- **Interface:** `PaymentApprovalListener` passou a se chamar `PaymentListener` e ganhou o método `refunded`.

## Testes

| Teste | Cobertura |
| --- | --- |
| `PaymentConfirmationIT` (16: 13 anteriores e 3 de reembolso) | critérios acima |
| `WebhookRateLimitTest` (novo) | teto de falhas de assinatura com relógio controlado; substitui uma asserção dependente de tempo no IT |
| `OrderAdminDomainTest`, `OrderLifecycleIT`, smoke do admin | `REFUNDED` aceito no filtro; o exemplo inválido passou a ser `CHARGEBACK` |
| Smoke da vitrine e do admin | sem regressão |

**Comandos executados:**
- backend: `mvnw verify` completo, com 81 testes unitários e 125 de integração, todos passando;
- admin e vitrine: `lint`, `typecheck`, `build` e `test:smoke`, todos passando.

**Não executado:** verificação manual no navegador e reembolso real no sandbox.

## Revisão de segurança (multi-tenant)

- **Autorização:** o OWNER é conferido pelo vínculo ativo e verificado (a mesma consulta do cancelamento), e o tenant vem da rota mais o vínculo. Pedido de outra loja recebe o mesmo 404. A rota exige sessão e CSRF.
- **Aprovação humana (AGENTS.md):** nenhum reembolso é automático. Só a ação explícita do OWNER, com motivo e duas etapas, chama o provedor. O pagamento sem estoque (P4) continua sem reembolso automático.
- **Idempotência:**
  1. uma linha `payment_refund` por pagamento;
  2. a mesma chave no provedor;
  3. transição condicional sob o lock do pagamento e do pedido;
  4. chave única do `SALE_REVERSAL`.
- **Confiança:** o pedido só vira `REFUNDED` com a order lida do provedor com o token da loja dona e conferida como no SC-014. A resposta do `POST` sozinha nunca basta.
- **Ordem de locks:** pagamento, depois pedido, depois saldos (a mesma do SC-014). A checagem inicial do OWNER roda numa transação curta, encerrada antes da chamada ao provedor.

## Hipóteses e limites

- **H11, reembolso confirmado:** consideramos confirmado quando a order está `refunded` (em `status` ou em `status_detail`). A documentação mostra `processed`/`refunded` no exemplo. Confirmar no sandbox, inclusive com Pix, cujo reembolso pode ser assíncrono.
- **H12, corpo `{}`:** a documentação manda não enviar valor no reembolso total, mas não mostra o exemplo. Confirmar no sandbox.
- **Fora do escopo:** reembolso parcial (P9) e reembolso feito pelo vendedor direto no painel do Mercado Pago (o sistema não o detecta; o pagamento continua `APPROVED`). Chargebacks além do registro ficam para depois.
