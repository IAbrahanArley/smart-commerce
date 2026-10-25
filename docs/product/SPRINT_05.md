# Sprint 5 — Financeiro e dashboard (planejamento)

Data: 2026-10-21. **F1–F10 do [ADR-0015](../architecture/adr/0015-finance-dashboard.md) aprovadas em 2026-10-21.** Cada tarefa exige autorização própria; a SC-019 foi autorizada na mesma data.

**Dependência:** a Sprint 4 está implementada e revisada, mas sua validação ponta a ponta no sandbox está pendente ([SPRINT_04.md](SPRINT_04.md)). As tarefas que não dependem de dados reais do Mercado Pago podem andar em paralelo; a SC-018 (taxas) precisa dessa validação para fechar.

**Objetivo:** o lojista vê quanto vendeu, quanto o Mercado Pago descontou, quanto ainda vai receber, quanto custaram os produtos vendidos e as despesas, e a margem de contribuição do período, sempre separando venda, recebível e caixa e mostrando "parcial" ou "indisponível" quando faltar dado. O dashboard resume vendas, ticket médio, estoque baixo e itens sem giro. Sem impostos, contabilidade oficial ou previsões.

## Gates de entrada

| Tarefa | Requer |
| --- | --- |
| SC-018 | F2, F3 aprovadas; validação real da Sprint 4 para confirmar os campos do Mercado Pago |
| SC-019 | F1, F4, F7, F10 |
| SC-020 | F6 |
| SC-021 | SC-019, SC-020; F3, F5 |
| SC-022 | SC-021; F8, F9 |
| SC-023 | SC-018 a SC-022 |

## SC-018 — Taxa, líquido e liberação do Mercado Pago (módulo `payments`)

- Ao aprovar um pagamento, e na conciliação, ler o pagamento da order (F2) com o token da loja e guardar um snapshot: taxa total e por tipo, valor líquido, data de liberação e situação da liberação.
- Sem o dado: "taxa indisponível", sem estimativa; nova tentativa na conciliação.
- Admin: no card "Pagamento", taxa, líquido e data de liberação no lugar de "não informados".

**Aceite:** um pagamento de teste mostra taxa e líquido reais; sem o dado, nada é estimado.

**Testes:**
- resposta completa, parcial e ausente;
- valores que não fecham (bruto ≠ líquido + taxas) viram divergência;
- A/B: o pagamento de A é lido com o token de A.

## SC-019 — Livro de lançamentos (módulo novo `finance`)

**Estado (2026-10-21):** autorizada e implementada ([validação](../quality/SC-019-VALIDATION.md)).

- Tabela `finance_entry`, só de acréscimo (F7), com tipos: venda, reembolso, taxa, CMV, estorno de CMV e despesa. Cada lançamento tem valor com sinal, competência, liquidação (quando houver), chave única derivada do fato e a origem (pedido, pagamento ou despesa).
- Lançamentos gravados na mesma transação de `PAID`, `REFUNDED` e da taxa (SC-018), pela interface já existente entre `payments` e `orders`.
- Migração com preenchimento inicial dos pedidos já pagos ou reembolsados.

**Aceite:** cada fato gera seus lançamentos uma única vez; o total do livro bate com pedidos e pagamentos.

**Testes:**
- webhook repetido e corrida não duplicam lançamentos;
- reembolso com e sem devolução ao estoque (estorno de CMV só no primeiro caso);
- pedido sem custo marcado como margem indisponível;
- preenchimento inicial idempotente;
- A/B.

## SC-020 — Despesas manuais (módulo `finance`)

**Estado (2026-10-21):** autorizada e implementada ([validação](../quality/SC-020-VALIDATION.md)).

- Admin: lista, cadastro e correção (cancelar com motivo e lançar de novo, F6); categoria da lista fixa, tipo fixa ou variável, competência e pagamento.
- Cada despesa gera seus lançamentos (competência e, se paga, caixa).

**Aceite:** o OWNER registra e corrige despesas com trilha de quem e quando.

**Testes:**
- validação de valor (positivo, 2 casas), datas e categoria;
- correção não apaga o histórico;
- CSRF e OWNER;
- A/B.

## SC-021 — Relatório financeiro do período

**Estado (2026-10-21):** autorizada e implementada ([validação](../quality/SC-021-VALIDATION.md)). Até a SC-018, todo relatório com vendas aparece como parcial por falta das taxas.

- `GET /api/tenants/{id}/finance?period=…`: receita bruta, reembolsos, taxas, CMV, despesas fixas e variáveis, margem de contribuição (F5), a receber (por data de liberação) e caixa simplificado (F3), todos derivados do livro.
- Indicação "parcial" com o motivo (taxas indisponíveis, N pedidos sem custo).
- Admin: página "Financeiro" com o resumo, os componentes da margem e a lista do que falta.

**Aceite:** os números batem com um cenário de teste calculado à mão; nada aparece como lucro definitivo com dado faltando.

**Testes:**
- cenários fechados com valores conhecidos;
- limites de período no fuso de São Paulo;
- reembolso em mês diferente da venda;
- A/B.

## SC-022 — Dashboard

**Estado (2026-10-21):** autorizada e implementada ([validação](../quality/SC-022-VALIDATION.md)). A conferência visual do gráfico e das telas foi realizada em 2026-10-22 com API sintética ([registro](../quality/SPRINT-05-UI-VALIDATION.md)); validação integrada e pendências de acessibilidade continuam para SC-023.

- Página inicial da loja no admin com os indicadores do F8: pedidos pagos, receita, ticket médio, margem estimada, estoque baixo e itens sem giro, com seletor de período.
- Gráfico simples de vendas por dia em SVG próprio (F9), com tabela equivalente acessível.

**Aceite:** os indicadores batem com o relatório financeiro e com o estoque.

**Testes:**
- cálculo do ticket médio sem divisão por zero;
- estoque baixo e itens sem giro com dados conhecidos;
- smoke do admin;
- A/B.

## SC-023 — Revisão integrada

**Estado (2026-10-24):** revisão executada ([validação](../quality/SC-023-VALIDATION.md)). Não houve falha de isolamento nem de cálculo, e a V27 passou a impor no banco o livro só de acréscimo. A sprint só fecha depois da SC-018 (sandbox real do Mercado Pago) e da revisão manual de acessibilidade.

**Preparação (2026-10-22):** validação das telas autorizada e realizada, com correções de moeda negativa, rascunho/foco/datas de despesas e estados do dashboard ([evidências e limitações](../quality/SPRINT-05-UI-VALIDATION.md)). Esta etapa não encerra a tarefa; a SC-018 e a validação real da Sprint 4 seguem como dependências.

- Checklists de [SECURITY.md](../agents/SECURITY.md) e [QA.md](../agents/QA.md), com foco em tenant, integridade dos números e acessibilidade do dashboard.
- Atualização de ARCHITECTURE, DATA_MODEL e do status do ADR-0015.

## Definition of Done

- Testes executados: unidade, integração PostgreSQL, ArchUnit e smoke; cenários financeiros com resultado esperado calculado à mão.
- Migrações aditivas.
- Valores em NUMERIC e BigDecimal, sem float.
- Documentação atualizada.
- Revisão multi-tenant.
- Aprovação humana.

## Fora da sprint

Impostos e regime tributário, DRE oficial, emissão fiscal, conciliação bancária, exportação contábil, previsão de caixa, despesas recorrentes, anexos, metas, IA (Sprint 6).
