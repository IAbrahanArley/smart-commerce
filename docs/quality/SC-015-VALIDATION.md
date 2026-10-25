# SC-015 — Conciliação mínima: validação

Data: 2026-10-21. Base: [ADR-0013](../architecture/adr/0013-mercado-pago-checkout-pro.md) (P4, P5, P6) e [SPRINT_04.md](../product/SPRINT_04.md).

**Estado:** implementada e validada com a **Orders API falsa** em loopback. Nenhuma consulta ao Mercado Pago real foi feita. A validação real está no lembrete do [SPRINT_04.md](../product/SPRINT_04.md).

> **Atualização (2026-10-24):** o Checkout Pro passou da Orders API para a API de preferências ([ADR-0013, mudança da P1](../architecture/adr/0013-mercado-pago-checkout-pro.md#mudança-da-p1-checkout-pro-pela-api-de-preferências-aprovada-em-2026-10-24)). Os testes citados aqui foram adaptados aos endpoints novos; este registro descreve a validação original.

## Critérios de aceite

| Critério | Evidência (`PaymentConfirmationIT`) |
| --- | --- |
| Sem webhook, um pagamento aprovado é confirmado pela rotina | `reconciliationConfirmsWithoutAnyWebhookOnceWithEachStoresToken` |
| A rotina usa o token do tenant de cada pedido (A/B) | o mesmo teste: lojas A e B, cada order lida com o próprio token |
| Rotina concorrente e idempotente | `concurrentReconciliationWebhookAndExpirationDeductExactlyOnce`: duas rotinas, a fila de webhook e a expiração ao mesmo tempo, com uma baixa por pedido |
| Provedor indisponível | `providerOutagesAreRecordedAsDivergenceRetriedLaterAndShownToTheOwner`: o pagamento continua pendente, a divergência é registrada e a nova tentativa vem só depois, sem repetição imediata |
| Pedidos pendentes e recém-expirados | `latePaymentsAreReconciledForADayAfterExpiryThenLeftAlone`: vencido há 2 h é confirmado (P4); vencido há 25 h não é mais consultado |
| Admin mostra situação, id da order, valores e divergências | o teste de indisponibilidade lê `OrderAdminService.find`; o smoke do admin cobre as mensagens de divergência |

## O que foi entregue

- **Migração `V20__payment_reconciliation.sql` (aditiva):** `payment.next_check_at`, `check_attempts`, `last_check_error` e um índice parcial. O único dado escrito é o `next_check_at` dos pagamentos que já aguardavam, para entrarem na primeira rodada.
- **`PaymentReconciliationService` e `PaymentReconciliationJob`:**
  - roda a cada minuto (`payments.reconciliation`) e reserva até 20 pagamentos `CREATED` vencidos com `SKIP LOCKED`, com um prazo de 5 min por lote;
  - usa o mesmo `PaymentConfirmationService` do webhook: consulta com o token da loja, confere referência, total e conta, e aprova sob o lock com baixa única;
  - frequência: a cada 1 min enquanto a order está aberta; a cada 15 min por 24 h depois do vencimento, para pegar pagamentos tardios (P4); depois disso, para;
  - em caso de falha, grava o motivo (`PROVIDER_ERROR`, `ACCOUNT_CHANGED`, `NOT_CONNECTED`…) em `last_check_error` e tenta de novo no mesmo ritmo.
- **Primeira consulta:** acontece 1 min depois de criada a order no provedor.
- **Admin, card "Pagamento" no detalhe do pedido:** situação do pagamento, id da order no Mercado Pago, valor cobrado, valor pago, situação no provedor, última consulta e a divergência explicada em texto.

## Configuração

| Variável | Uso |
| --- | --- |
| `PAYMENTS_RECONCILIATION_ENABLED` | `true` por padrão: liga a rotina |

Com a integração configurada, a rotina é o caminho principal de confirmação em DEV, porque lá não há webhook (P6, sem túnel).

## Testes

| Teste | Cobertura |
| --- | --- |
| `PaymentConfirmationIT` (13: 9 do SC-014 e 4 novos) | critérios acima; os testes do SC-014 continuam passando com a rotina desligada no contexto e chamada diretamente |
| `CheckoutPaymentIT` | as rotinas de eventos e de conciliação ficam desligadas nesse contexto, para não consultarem o provedor falso do teste |
| Smoke do admin | mensagens de divergência, sem expor códigos desconhecidos |

**Comandos executados:**
- backend: `mvnw verify` completo, com 80 testes unitários e 122 de integração, todos passando;
- admin: `lint`, `typecheck`, `build` e `test:smoke`, todos passando.

**Não executado:** verificação manual no navegador e consulta ao Mercado Pago real.

## Revisão de segurança (multi-tenant)

- **Tenant:** cada linha reservada traz o próprio `tenant_id`, e a consulta usa o token desse tenant. A conferência de conta (`ACCOUNT_CHANGED`) impede que uma loja reconectada leia orders da conta anterior.
- **Idempotência:** é o mesmo caminho do webhook (lock do pagamento, depois do pedido, depois dos saldos), então rotina, webhook e expiração juntos baixam o estoque uma vez.
- **Carga no provedor:** só pagamentos `CREATED` dentro da janela, com intervalo mínimo de 1 min, lotes de 20 e no máximo 10 lotes por rodada. Uma falha nunca gera repetição imediata.
- **Dados:** o admin vê só o id da order e valores. Nenhum token aparece na resposta (o teste confere).

## Hipóteses e limites

- **H9, taxa e líquido:** a Orders API não informa taxa nem valor líquido (a referência de `GET /v1/orders/{id}` só tem `marketplace_fee`). O admin mostra "Não informados pelo Mercado Pago" em vez de estimar. Obter esses valores, por exemplo pela API de pagamentos, é decisão da Sprint 5 (financeiro).
- **H10, janela de 24 h depois do vencimento:** o provedor devolve ao pagador o que for pago depois do vencimento, mas o P4 exige registrar esses pagamentos. Confirmar no sandbox por quanto tempo um Pix tardio pode aparecer.
- **Estados posteriores** (estorno ou chargeback de um pedido já `PAID`) não são conciliados aqui: ficam para a SC-016 e depois.
- **Expiração do ritmo:** depois de 24 h, um pagamento `CREATED` deixa de ser consultado; se for pago depois disso, só um webhook o confirmaria.
