# ADR-0015 — Financeiro e dashboard do lojista (Sprint 5)

Status: **adotado** em 2026-10-21 para F1–F10, conforme as recomendações abaixo, aprovadas pelo responsável. Proposto na mesma data. Cada tarefa da Sprint 5 ainda exige autorização própria. Plano incremental: [SPRINT_05.md](../../product/SPRINT_05.md).

## Contexto

- **[PRD](../../product/PRD.md), MVP 7 e 8:** receitas por vendas, taxas, despesas manuais, CMV, margem de contribuição, contas a receber e fluxo de caixa simplificado; dashboard com vendas, pedidos, ticket médio, margem estimada, estoque baixo e itens sem giro. Fora do MVP: emissão fiscal, contabilidade oficial e cálculo tributário automático.
- **[BUSINESS_RULES](../../product/BUSINESS_RULES.md):**
  - custo ausente significa margem indisponível, nunca lucro presumido;
  - faturamento, recebíveis, caixa e lucro são conceitos distintos, e o dashboard identifica competência e data de liquidação;
  - taxas, impostos e devoluções aparecem separadamente quando disponíveis;
  - nunca apresentar lucro líquido definitivo sem dados completos.
- **[ARCHITECTURE](../ARCHITECTURE.md), princípios financeiros:** fatos auditáveis, separação entre venda, aprovação, liquidação e caixa, BigDecimal e BRL, snapshot de custos e taxas; a IA nunca é fonte de verdade para cálculos.
- **O que já existe (Sprints 3 e 4):**
  - pedido com snapshot de preço, custo opcional, quantidade e frete;
  - estados `PAID` (`paid_at`) e `REFUNDED` (`refunded_at`);
  - `payment` com valor e valor pago;
  - movimentos `SALE` e `SALE_REVERSAL` no estoque.
- **Lacuna conhecida (SC-015, H9):** a Orders API do Mercado Pago **não informa taxa nem valor líquido** de um pagamento.

## Decisões (aprovadas em 2026-10-21)

| ID | Decisão | Recomendação | Alternativas | Bloqueia |
| --- | --- | --- | --- | --- |
| F1 | Competência da receita | **Data de confirmação do pagamento (`paid_at`)**, no fuso `America/Sao_Paulo`; receita bruta = total do pedido (itens + frete); reembolso entra como dedução na data em que foi confirmado (`refunded_at`), sem reescrever o mês da venda | Data de criação do pedido (contaria pedidos nunca pagos) | SC-019, SC-021 |
| F2 | Taxa e valor líquido do Mercado Pago | **Consultar o pagamento da order** (`transactions.payments[].reference.id` → `GET /v1/payments/{id}`: `fee_details`, `net_received_amount`, `money_release_date`), com o token da loja, guardando um snapshot. Sem esse dado: "taxa indisponível", **nunca estimada**. **Precisa ser confirmado no sandbox real** (hipótese de que o `reference.id` é o id da API de pagamentos) | (a) taxa percentual informada pelo lojista, sempre rotulada "estimada"; (b) ignorar taxas no MVP | SC-018 |
| F3 | Contas a receber e caixa | **A receber** = líquido de pagamentos aprovados ainda não liberados (`money_release_date` futura ou desconhecida). **Caixa simplificado** = líquido liberado + despesas pagas (negativas), por data de liberação ou de pagamento. Sem data de liberação, o valor fica em "a receber, data desconhecida" | Tratar tudo como caixa na aprovação (mistura recebível e caixa, contraria a regra) | SC-018, SC-021 |
| F4 | CMV | **Soma de `unit_cost_snapshot × quantidade`** dos pedidos pagos na competência. Pedido com algum item sem custo: margem do pedido indisponível, contado à parte ("N pedidos sem custo"). Reembolso **com** devolução ao estoque estorna o CMV; **sem** devolução, o CMV fica (perda) | Custo atual do catálogo (quebra o snapshot) | SC-019, SC-021 |
| F5 | Margem de contribuição | **Receita bruta − reembolsos − taxas − CMV − despesas variáveis do período.** Só aparece como valor quando todos os componentes do período estão completos; senão, "parcial" com o que falta listado (taxas indisponíveis, pedidos sem custo) | Mostrar sempre, com aviso (risco de ler lucro onde não há) | SC-021 |
| F6 | Despesas manuais | **Tabela `expense`**: categoria de uma lista fixa (frete, embalagem, marketing, aluguel, pessoal, outros), descrição, valor, data de competência, data de pagamento opcional, tipo fixa ou variável. Só o OWNER cria ou corrige; **não se apaga**: uma correção cancela a anterior com motivo (auditável). Sem recorrência nem anexos no MVP | Exclusão física; despesas recorrentes | SC-020 |
| F7 | Registro financeiro | **Livro de lançamentos `finance_entry` só de acréscimo**, gravado na mesma transação dos fatos: venda confirmada, reembolso, taxa (quando conhecida), estorno de CMV e despesa. Cada lançamento tem chave única derivada do fato (idempotente), competência e liquidação próprias, e o pedido e o pagamento de origem. Os relatórios leem só o livro. Uma migração cria os lançamentos dos pedidos já pagos | Calcular tudo na hora a partir de pedidos e pagamentos (sem trilha própria; a conta muda se uma regra mudar) | SC-019 |
| F8 | Dashboard | **Períodos:** hoje, 7 e 30 dias, mês atual e anterior. **Métricas:** pedidos pagos, receita, ticket médio (receita ÷ pedidos pagos), margem estimada (F5), estoque baixo (disponível ≤ 5, limite fixo no MVP) e itens sem giro (com estoque e sem venda em 30 dias) | Limite de estoque baixo por produto; períodos livres | SC-022 |
| F9 | Gráficos | **Sem dependência nova:** barras e linhas em SVG próprio com os tokens do DESIGN.md e tabela equivalente acessível. Uma biblioteca de gráficos só com aprovação | Recharts ou similar (dependência nova) | SC-022 |
| F10 | Dinheiro e arredondamento | NUMERIC no banco e BigDecimal no código, BRL, sem float; arredondamento HALF_EVEN só na exibição de percentuais (2 casas); valores sempre com 2 casas | — | todas |

## Revisão integrada (SC-023, 2026-10-24)

A revisão das SC-019 a SC-022 está registrada em [SC-023-VALIDATION.md](../../quality/SC-023-VALIDATION.md). Ela não encontrou falha de isolamento nem de cálculo.

**Ajuste feito:** a V25 passou a impor no banco o livro só de acréscimo (F7).

**Ainda pendente para fechar o ADR:**
- a SC-018, ou seja, taxa, líquido e liberação (F2, F3), que depende da validação real no sandbox do Mercado Pago;
- a revisão de acessibilidade com leitor de tela, zoom e Axe.

## Consequências e riscos

- **Dependência do sandbox (F2):** a taxa só fica disponível se a hipótese do `reference.id` se confirmar; a SC-018 pode terminar só com "taxa indisponível", e a margem (F5) fica "parcial" até lá. É a decisão mais incerta.
- **Livro de lançamentos (F7):** mais código e uma migração com preenchimento inicial, mas auditoria e competência ficam explícitas e idempotentes; os relatórios não dependem de reconstruir estado.
- **Escopo:** nada de impostos, DRE oficial, conciliação bancária, exportação contábil ou previsão. A IA (Sprint 6) só lê o livro.
- **Multi-tenant:** todo lançamento, despesa e relatório é por tenant, com OWNER conferido no SQL e testes A/B em cada tarefa.
