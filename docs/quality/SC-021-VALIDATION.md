# SC-021 — Relatório financeiro do período: validação

Data: 2026-10-21. Base: [ADR-0015](../architecture/adr/0015-finance-dashboard.md) (F1–F5, F10) e [SPRINT_05.md](../product/SPRINT_05.md).

**Estado:** implementada e validada com PostgreSQL via Testcontainers, com um cenário calculado à mão, e com o smoke do admin. Não houve verificação manual no navegador. As taxas do Mercado Pago ainda não existem no livro (SC-018), então todo relatório com vendas aparece como **parcial**, que é o comportamento esperado até lá.

## Critérios de aceite

| Critério | Evidência (`FinanceReportIT`) |
| --- | --- |
| Os números batem com um cenário calculado à mão | `reportMatchesAHandComputedScenario`, conta abaixo |
| Nada aparece como lucro definitivo com dado faltando | o mesmo teste: `complete=false` com 3 vendas sem taxa e 1 pedido sem custo completo; o admin mostra "Parcial" e a lista do que falta |
| Limites de período no fuso de São Paulo | venda às 23:30 de 30/09 (02:30 UTC de 01/10) conta em setembro |
| Reembolso em mês diferente da venda | reembolso de venda de agosto conta em setembro; reembolso (com devolução) de venda de setembro conta em outubro, com o CMV estornado; agosto continua com sua venda e seu custo |
| A/B | `periodsValidationSessionAndOtherStores`: o OWNER de B recebe 404 na loja A e vê a própria loja vazia; sem sessão, 401 |

**Cenário de setembro de 2026:**
- vendas: 100 + 50 + 30 = **180**;
- reembolso de uma venda de agosto: **80**;
- taxas: **0**, porque nenhuma foi informada (3 vendas sem taxa);
- CMV: 40 + 20 + 10 = **70** (o último pedido tem custo incompleto);
- despesas variáveis: **15** (uma de 5 foi cancelada);
- margem de contribuição: 180 − 80 − 0 − 70 − 15 = **15**, ou 15,00% da receita depois dos reembolsos (100);
- despesas fixas: **500**, mostradas à parte;
- ticket médio: 180 ÷ 3 = **60**;
- caixa: **−15** (só a despesa paga);
- a receber: 260 − 80 = **180**, antes das taxas.

Em outubro, com nenhuma venda, um reembolso de 50 e o CMV estornado de 20, a margem é **−30** e o percentual e o ticket ficam indisponíveis.

## O que foi entregue

- **`GET /api/tenants/{t}/finance?period=…|month=AAAA-MM`:** sessão, vínculo OWNER no SQL e `no-store`.
  - Períodos: `TODAY`, `LAST_7_DAYS`, `LAST_30_DAYS`, `THIS_MONTH` (padrão) e `LAST_MONTH`, ou um mês específico.
  - Os dias começam à meia-noite de São Paulo, e os períodos nomeados incluem hoje.
- **`FinanceReportService`:** um único snapshot (`REPEATABLE READ`) só sobre o livro. Entrega:
  - receita bruta, reembolsos, taxas, receita líquida, CMV (com estornos), despesas variáveis e fixas;
  - margem de contribuição e percentual sobre a receita depois dos reembolsos;
  - `complete` e `missing` (`FEES_UNAVAILABLE`, `COST_MISSING`), pedidos pagos e ticket médio;
  - a receber: vendas ainda não liberadas, depois de reembolsos e taxas conhecidas, até o fim do período ou até agora;
  - caixa do período, pela data de liquidação.
- **Admin:**
  - página **Financeiro** (`/lojas/{id}/financeiro?periodo=…`) e item no menu da loja;
  - cartões de pedidos pagos, ticket médio, a receber e caixa;
  - tabela da margem de contribuição, com etiqueta "Parcial" e a lista do que falta em português;
  - despesas fixas à parte, link para as despesas e aviso de que o resumo não é contábil nem fiscal.

## Testes

| Teste | Cobertura |
| --- | --- |
| `FinanceReportIT` (2, novo) | cenário à mão em agosto, setembro e outubro; períodos nomeados; mês e período inválidos (400); sessão; A/B |
| Smoke do admin | `lib/finance.ts` (período da URL, textos de dados faltantes, percentual) e a rota `/financeiro` com o período selecionado |
| Demais (`mvnw verify`) | sem regressão |

**Comandos executados:**
- backend: `mvnw verify` completo, com 83 testes unitários e 133 de integração, todos passando;
- admin: `lint`, `typecheck`, `build` e `test:smoke`, todos passando.

## Decisões de implementação

- **Sinais:** o relatório mostra as deduções como valores positivos e a margem com sinal. Um CMV negativo (só estorno no período) aparece como tal.
- **Percentual da margem:** sobre a receita depois dos reembolsos, com 2 casas e arredondamento HALF_EVEN (F10). Fica indisponível quando essa base é zero ou negativa.
- **Ticket médio:** receita bruta ÷ pedidos pagos no período (F8), também HALF_EVEN.
- **A receber:** conta todas as vendas não liberadas até o fim do período, não só as do período. Sem as datas de liberação (SC-018), o valor aparece como "data de liberação desconhecida".
