# SC-014 — Webhook, confirmação e baixa de estoque: validação

Data: 2026-10-21. Base: [ADR-0013](../architecture/adr/0013-mercado-pago-checkout-pro.md) (P4, P5, P6, P7, P8) e [SPRINT_04.md](../product/SPRINT_04.md).

**Estado:** implementada e validada com **webhook assinado e Orders API falsos** em loopback. Nenhuma notificação real do Mercado Pago foi recebida. A validação real está no lembrete do [SPRINT_04.md](../product/SPRINT_04.md).

> **Atualização (2026-10-24):** o Checkout Pro passou da Orders API para a API de preferências ([ADR-0013, mudança da P1](../architecture/adr/0013-mercado-pago-checkout-pro.md#mudança-da-p1-checkout-pro-pela-api-de-preferências-aprovada-em-2026-10-24)). Os testes citados aqui foram adaptados aos endpoints novos; este registro descreve a validação original.

## Critérios de aceite

| Critério | Evidência (`PaymentConfirmationIT`, salvo indicação) |
| --- | --- |
| Pagamento aprovado leva o pedido a `PAID` e baixa o estoque uma vez | `signedWebhookConfirmsOnceAndDeductsStockOnceBySystemActor` |
| Webhook repetido não tem efeito | o mesmo teste: mesma notificação gera um evento; outra notificação da mesma order não consulta o provedor nem baixa de novo |
| Webhook fora de ordem não tem efeito | `outOfOrderNotificationsOnlyConfirmWhatTheProviderReportsAsPaid` |
| Webhook forjado não tem efeito (401) | `forgedStaleOrMalformedNotificationsAreRejectedAndRecordNothing`, `WebhookSignatureTest` |
| Confirmação por `GET /v1/orders/{id}` com o token da loja dona e transição condicional | o primeiro teste e `eachNotificationIsResolvedToItsOwnStoreAndTokenNeverTheNotificationsClaims` |
| Baixa com movimento `SALE` de ator de sistema (P7) | o primeiro teste confere `delta`, `balance_after`, `actor_kind=SYSTEM`, `actor_id` nulo e motivo |
| Pagamento tardio (P4): reserva de novo ou alerta "pago sem estoque" | `latePaymentReservesAgainOrIsFlaggedPaidWithoutStock` (expirado, cancelado e sem estoque) |
| Corrida entre webhook, expiração e eventos concorrentes | `racesWithExpirationAndBetweenEventsDeductExactlyOnce` (4 rodadas) |
| A/B: notificação de B nunca altera pedido de A; tenant pela order guardada | `eachNotificationIsResolvedToItsOwnStore…` |
| Logs sem token | o primeiro teste confere que a saída não tem o token nem o segredo do webhook |

## O que foi entregue

**Migração `V19__payment_confirmation.sql` (aditiva):** ver [DATA_MODEL](../architecture/DATA_MODEL.md). As CHECKs antigas sem nome explícito são removidas pela definição, e a migração falha se não encontrar exatamente uma.

**Backend, módulo `payments`:**
- **`WebhookSignature`:** HMAC-SHA256 com comparação em tempo constante, sobre o texto `id:[data.id];request-id:[x-request-id];ts:[ts];`. O `ts` pode estar em segundos ou milissegundos, com tolerância de 10 min. Aceita o `data.id` como veio ou em minúsculas.
- **`MercadoPagoWebhookController`:** `POST /api/public/webhooks/mercado-pago`.
  - Sem sessão nem CSRF.
  - Respostas: 404 sem segredo configurado, 413 acima de 16 KB, 401 com assinatura inválida (429 quando há mais de 300 falhas de assinatura em 10 s no processo), 400 com notificação ilegível (ou que cita outra order no corpo) e 200 quando o evento é gravado.
  - O limite conta só as falhas de assinatura: uma enxurrada anônima nunca bloqueia uma entrega assinada.
- **`PaymentWebhookService`:** resolve o tenant pela tabela `payment` (nunca pelo payload), comparando o id da order sem diferenciar maiúsculas, e grava em `payment_event` com a grafia guardada, uma vez por chave de evento. A chave usa só valores assinados (`data.id` e `x-request-id`). Order desconhecida fica `IGNORED`.
- **`PaymentEventService` e `PaymentEventJob`:** a rotina roda a cada 10 s e reserva lotes com `SKIP LOCKED`, com um prazo de 5 min por lote. Erros transitórios voltam com espera crescente (1, 2, 4… min, até 6 h). Depois de 8 tentativas, o evento fica `FAILED`.
- **`PaymentConfirmationService`:**
  1. lê o pagamento; se já foi aprovado, para aí;
  2. obtém o token da loja e confere que a conta conectada é a mesma da order (se a loja trocou de conta, falha com `ACCOUNT_CHANGED`);
  3. chama `GET /v1/orders/{id}` e exige referência igual ao pedido e total presente e igual; confere a conta e a moeda (`BRL`) quando informadas;
  4. sob o lock do pagamento, aprova e chama o `PaymentApprovalListener` na mesma transação.

**Backend, módulos `orders` e `inventory`:**
- **`OrderPaymentApproval` (implementa o listener):** trava o pedido e chama `InventoryReservationService.sell`, depois `markPaid`, que só vale a partir de `PENDING_PAYMENT`, `EXPIRED` ou `CANCELLED` não anonimizado.
- **`sell`:** trava os saldos na ordem das variantes. Cada linha é coberta pela reserva própria em vigor mais o disponível. Se tudo está coberto, baixa o saldo, grava o movimento `SALE` e consome as reservas. Se algo falta, não baixa nada e libera as reservas.
- **Chave de requisição do `SALE`:** derivada de `sale:<pedido>:<variante>`. A unicidade `(tenant_id, request_id)` impede uma segunda baixa da mesma linha também no banco.
- **Movimentos manuais** continuam aceitando só `ENTRY` e `ADJUSTMENT`.
- **Admin:** filtro `PAID` e `paidAt`/`paidWithoutStock` no detalhe do pedido.

**Admin web:**
- situação "Pago" no filtro, na lista e no detalhe;
- data da confirmação;
- alerta "Pago sem estoque" explicando que nada foi baixado e que o reembolso não é automático;
- tipos "Venda" e "Devolução de venda" no histórico de estoque.

**Vitrine:**
- situação "Pagamento confirmado" no pedido e na página de retorno;
- a página continua sem ler a query string.

## Configuração

| Variável | Uso |
| --- | --- |
| `MERCADO_PAGO_WEBHOOK_SECRET` | segredo do webhook da aplicação (painel → Webhooks). Vazio desliga a rota (404) |
| `PAYMENTS_EVENTS_ENABLED` | `true` por padrão: liga a rotina de eventos |

A URL do webhook precisa ser **HTTPS pública** e é cadastrada no painel com o evento "Order (Mercado Pago)". Em localhost não há webhook (P6, sem túnel); a confirmação local virá da conciliação (SC-015).

## Testes

| Teste | Cobertura |
| --- | --- |
| `WebhookSignatureTest` (2) | segundos e milissegundos, `id` em minúsculas, espaços no cabeçalho; outra order, outra requisição, `ts` 10 min fora, outro segredo, cabeçalho ausente, sem `ts` ou `v1`, `v1` errado, maiúsculo ou curto, cabeçalho enorme, id com barra; `toString` sem segredo; segredo curto recusado |
| `PaymentConfirmationIT` (9), com PostgreSQL via Testcontainers | os critérios da tabela acima; resposta que diverge do pedido (referência ou total) ou que omite referência ou total, ou com moeda diferente: fica `PENDING` com nova tentativa e nunca confirma; pagamento parcial não confirma; 320 assinaturas inválidas não impedem uma entrega assinada; id em minúsculas é roteado para o pedido certo |
| `InventoryReservationServiceTest` | validação de entrada de `sell` |
| `OrderAdminDomainTest`, smoke do admin | filtro `PAID` aceito; `REFUNDED` e minúsculas recusados |
| `CheckoutPaymentIT`, `OrderCheckoutIT` e demais | SC-012 e SC-013 sem regressão |
| Smoke da vitrine | fluxo do SC-013 sem regressão |

**Comandos executados:**
- backend: `mvnw verify` completo, com 80 testes unitários e 118 de integração, todos passando;
- admin e vitrine: `lint`, `typecheck`, `build` e `test:smoke`, todos passando.

**Testes antigos ajustados ao novo contrato:**
- `InventoryReservationMigrationIT` compara o ledger pelas colunas originais da V7. A V19 acrescenta colunas sem mudar as linhas.
- `OrderLifecycleIT` e o smoke do admin usavam `PAID` como exemplo de filtro inválido; agora usam `REFUNDED`.

**Não executado:** verificação manual no navegador e notificação real do Mercado Pago.

## Revisão de segurança (multi-tenant)

- **Autenticação:** sem o segredo, nada chega ao banco. Assinatura inválida ou antiga recebe 401. O corpo não pode trocar a order assinada na query.
- **Confiança:** a notificação só diz qual order consultar. O estado vem do `GET` com o token da loja dona, conferido contra a referência, a conta e o total guardados.
- **Tenant:** resolvido por `payment.provider_order_id`. O `user_id` do payload é só registrado. Uma loja reconectada a outra conta não consulta orders da conta anterior.
- **Idempotência em três níveis:**
  1. evento único por chave;
  2. transição condicional sob o lock do pagamento e do pedido;
  3. chave única do movimento `SALE` por pedido e variante.
- **Dados:** o corpo da notificação não é guardado. Logs têm só ids e tipos de erro, nunca token ou segredo.
- **Abuso:** o limite de taxa por processo protege a CPU. O provedor reenvia o que for recusado.

## Revisão independente

Revisor read-only (subagente), sobre o diff completo, em 2026-10-21. Nenhum achado crítico ou alto. Achados e disposição:

| Achado | Severidade | Disposição |
| --- | --- | --- |
| Limite de taxa global consumido antes da assinatura: uma enxurrada anônima bloquearia entregas reais | média | **corrigido:** o limite conta só falhas de assinatura |
| Conferência da resposta pulada quando referência ou total faltam; moeda não conferida | média | **corrigido:** referência e total obrigatórios; moeda `BRL` quando informada |
| Chave de deduplicação vinda do corpo, não assinado | baixa | **corrigido:** chave só com valores assinados |
| Roteamento sensível a maiúsculas, enquanto a assinatura aceita minúsculas | baixa | **corrigido:** busca sem diferenciar maiúsculas (índice em `lower(provider_order_id)`), gravando a grafia guardada |
| Resultado de `approve` ignorado | baixa | **corrigido:** falha se a linha mudou |

O revisor confirmou: isolamento por tenant, comparação em tempo constante, leitura do corpo antes de `getParameter`, idempotência em três níveis, ordem de locks sem ciclo, migração aditiva e ausência de segredos em logs e respostas.

## Hipóteses e limites

- **H5, assinatura:** a documentação consultada não mostrava o texto assinado. Usamos o formato historicamente documentado e aceitamos o `data.id` como veio ou em minúsculas. Confirmar com o "Simular" do painel.
- **H6, `ts`:** pode vir em segundos ou milissegundos (os exemplos da documentação usam os dois). A tolerância de 10 min supõe que cada reenvio é assinado de novo; se não for, os reenvios tardios dão 401, e a conciliação (SC-015) cobre o caso.
- **H7, "pago":** a order precisa estar `processed`/`accredited` com `total_paid_amount` igual ao total. Confirmar no sandbox os valores de Pix e de cartão.
- **H8, chave do evento:** `data.id` + `x-request-id`, os dois assinados. Um reenvio com novo `x-request-id` vira outro evento, sem dano, porque a confirmação é idempotente.
- **Sem webhook em DEV (P6):** o pedido só vira `PAID` localmente com a conciliação (SC-015) ou chamando a rotina de eventos.
- **Fora do escopo:** conciliação e exibição do pagamento no admin (SC-015); reembolso e `SALE_REVERSAL` (SC-016); e-mail ao comprador.
