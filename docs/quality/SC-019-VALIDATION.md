# SC-019 — Livro de lançamentos financeiros: validação

Data: 2026-10-21. Base: [ADR-0015](../architecture/adr/0015-finance-dashboard.md) (F1, F4, F7, F10) e [SPRINT_05.md](../product/SPRINT_05.md).

**Estado:** implementada e validada com PostgreSQL via Testcontainers. Ainda não há tela nem relatório: a leitura do livro chega na SC-021. As taxas do Mercado Pago (tipo `FEE`) chegam na SC-018 e as despesas (`EXPENSE`) na SC-020.

## Critérios de aceite

| Critério | Evidência |
| --- | --- |
| Cada fato gera seus lançamentos uma única vez | `PaymentConfirmationIT.signedWebhookConfirmsOnce…`: com eventos repetidos, uma venda e um CMV |
| Reembolso com e sem devolução ao estoque | `refundIsAskedOnce…` (com devolução: reembolso e estorno do CMV) e `refundWithoutRestock…` (sem devolução: o CMV fica; pago sem estoque e reembolsado: o CMV é estornado) |
| Pedido sem custo marcado como margem indisponível | os mesmos testes (as variantes do teste não têm custo: CMV 0 marcado `cost_incomplete`) e `FinanceLedgerMigrationIT` |
| Preenchimento inicial | `FinanceLedgerMigrationIT`: pedidos pagos e reembolsados antes da V22 ganham os lançamentos com as mesmas chaves do código; pedido pendente não ganha nada; competência na data do pagamento e do reembolso |
| Total do livro bate com pedidos e pagamentos | valores conferidos lançamento a lançamento nos dois testes (venda = total do pedido; reembolso = −total; CMV = −Σ custo × quantidade) |
| A/B | lançamentos sempre com o tenant do pedido; o caminho é o mesmo da confirmação e do reembolso, já cobertos por A/B na Sprint 4. A leitura por tenant será testada na SC-021 |

## O que foi entregue

- **Migração `V22__finance_ledger.sql` (aditiva):** tabela `finance_entry`, só de acréscimo:
  - tipos `SALE`, `REFUND`, `FEE`, `COGS`, `COGS_REVERSAL` e `EXPENSE`;
  - valor com sinal conferido por CHECK (entradas positivas, custos e deduções negativos) e moeda `BRL`;
  - `competence_at`, `settled_at` (preenchido pela SC-018), pedido de origem (FK composta por tenant) e `cost_incomplete`;
  - chave única `(tenant_id, entry_key)`.

  A migração também cria os lançamentos dos pedidos já pagos ou reembolsados.
- **Módulo `finance`:**
  - `FinanceLedgerRepository`: `append` idempotente, por `ON CONFLICT DO NOTHING`;
  - `FinanceLedgerService`: venda e CMV em `paid_at`; reembolso em `refunded_at`, com estorno do CMV quando as unidades voltam ao estoque ou nunca saíram dele.
- **Gravação:** `OrderPaymentApproval` grava os lançamentos na mesma transação que marca o pedido `PAID` ou `REFUNDED`. Os valores vêm dos snapshots do pedido (`OrderPaymentRepository.saleFigures`). A dependência é de `orders` para `finance`.

**Chaves:** `sale:<pedido>`, `cogs:<pedido>`, `refund:<pedido>` e `cogs-reversal:<pedido>`.

## Testes

| Teste | Cobertura |
| --- | --- |
| `FinanceLedgerMigrationIT` (1, novo) | V21 → V22 com 6 pedidos: pago com custo, pago sem custo, reembolsado sem estoque, reembolsado sem devolução, reembolsado com devolução e pendente |
| `PaymentConfirmationIT` (18) | lançamentos depois de pagamento, eventos repetidos e reembolsos |
| Demais (`mvnw verify`) | sem regressão |

**Comando executado:** `mvnw verify` completo no backend, com 81 testes unitários e 128 de integração, todos passando. Admin e vitrine não mudaram.

## Decisões de implementação

- **Pago sem estoque e reembolsado:** o CMV é estornado mesmo sem devolução, porque nenhuma unidade saiu (F4 aplicado ao P4).
- **CMV de pedido sem custo:** grava a soma dos custos conhecidos (0 se nenhum) e marca `cost_incomplete`. A SC-021 mostra a margem como "parcial" em vez de somar um custo zero como se fosse real.
- **Imutabilidade:** nada no código atualiza ou apaga `finance_entry`. A SC-018 vai preencher a liquidação gravando lançamentos novos (`FEE` e liberação) ou só o campo `settled_at`, decisão registrada naquela tarefa.
