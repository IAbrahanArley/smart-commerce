# SC-022 — Dashboard: validação

Data: 2026-10-21. Base: [ADR-0015](../architecture/adr/0015-finance-dashboard.md) (F8, F9) e [SPRINT_05.md](../product/SPRINT_05.md).

**Estado:** implementada e validada com PostgreSQL via Testcontainers e com o smoke do admin. **Não houve verificação visual no navegador**, então o gráfico em SVG ainda precisa ser conferido numa tela real (está na lista de pendências da SC-023).

Atualização de 2026-10-22: gráfico, tabela equivalente e responsividade conferidos no navegador com API sintética, com correções e [limitações registradas](SPRINT-05-UI-VALIDATION.md). A ausência de navegador acima descreve a validação inicial; a revisão integrada da SC-023 continua pendente.

## Critérios de aceite

| Critério | Evidência (`DashboardIT`) |
| --- | --- |
| Os indicadores batem com o relatório financeiro | pedidos pagos, receita e margem são lidos do mesmo `FinanceReportService` e comparados no teste com `/finance` |
| Os indicadores batem com o estoque | estoque baixo usa a fórmula única de disponibilidade: uma reserva ativa de 3 sobre 7 físicos deixa 4 disponíveis, e o item entra na lista |
| Ticket médio sem divisão por zero | período sem vendas: ticket `null` (o admin mostra "—") |
| Estoque baixo e itens sem giro com dados conhecidos | 2 itens com estoque baixo, em ordem; sem giro inclui o item nunca vendido e o vendido há 45 dias, e exclui o vendido há 5 dias e o produto criado ontem |
| A/B | o OWNER de B recebe 404 no painel de A e não vê os itens de A no próprio painel; sem sessão, 401 |

## O que foi entregue

- **`GET /api/tenants/{t}/dashboard?period=…`** (módulo novo `analytics`): sessão, OWNER (pelo relatório financeiro) e `no-store`. Entrega:
  - os números do período, que são os do relatório da SC-021: pedidos pagos, receita, ticket, margem, `marginComplete` e o que falta;
  - vendas por dia do calendário de São Paulo, com todos os dias presentes;
  - estoque baixo (disponível ≤ 5) e itens sem giro (com estoque, sem venda há 30 dias, de produtos com mais de 30 dias): até 10 de cada, com o total.
- **`InventoryInsightService`** (módulo `inventory`): as duas listas, a partir de `inventory_available_quantity` e dos movimentos `SALE`.
- **`FinanceReportService.salesByDay`:** soma do livro por dia, preenchendo com zero os dias sem venda.
- **Admin, painel "Desempenho" na visão geral da loja (`/lojas/{id}?periodo=…`):**
  - seletor de período e cartões de pedidos pagos, receita, ticket médio e margem estimada, esta com a etiqueta "Parcial" e o motivo quando faltam dados;
  - gráfico de barras da receita por dia em SVG próprio (F9: sem biblioteca), com `role="img"`, título e descrição;
  - tabela equivalente em "Ver os valores do gráfico em tabela";
  - listas de estoque baixo e sem giro, com link para o produto;
  - link para o financeiro completo do período.
- **Ajuste de texto:** a visão geral e o diálogo de publicação ainda diziam "Compras ainda não estão disponíveis"; o trecho foi removido.

## Testes

| Teste | Cobertura |
| --- | --- |
| `DashboardIT` (2, novo) | critérios acima, período `TODAY` e `LAST_7_DAYS`, período inválido (400) |
| `ArchitectureTest` | camadas do módulo `analytics` |
| Smoke do admin | `lib/dashboard.ts`: altura e posição das barras proporcionais ao maior dia, período vazio sem divisão por zero, rótulos de dia e de opções |
| Demais (`mvnw verify`) | sem regressão |

**Comandos executados:**
- backend: `mvnw verify` completo, com 83 testes unitários e 135 de integração, todos passando;
- admin: `lint`, `typecheck`, `build` e `test:smoke`, todos passando.

## Decisões de implementação

- **Mesma fonte:** o dashboard não calcula nada que o relatório já calcula; ele chama o relatório. Resumo, vendas por dia e listas de estoque compartilham uma transação de leitura `REPEATABLE READ`, preservando o mesmo snapshot mesmo quando uma venda é confirmada durante a consulta.
- **Itens sem giro:** a última venda é a data do movimento `SALE` mais recente da variante. Produto criado há menos de 30 dias não entra, para não marcar como parado o que acabou de chegar.
- **Limite de estoque baixo:** fixo em 5 unidades disponíveis (F8). Por produto fica para depois.
- **Lugar no admin:** o painel fica na visão geral, abaixo dos cartões existentes, sem criar uma página nova.

## Revisão antes da publicação (2026-10-22)

- Revisão independente do backend: achado P2 em `DashboardService.dashboard`, onde o resumo e a série diária podiam observar vendas diferentes. Corrigido com uma transação `REPEATABLE READ` envolvendo a composição inteira, incluindo o estoque; segunda revisão sem novos achados nesse ajuste.
- Regressão determinística em `DashboardIT.concurrentSaleDoesNotSplitSummaryAndDailySnapshot`: uma venda é confirmada em outra conexão antes da consulta diária. Antes da correção, o gráfico retornou 100,00 enquanto o resumo retornava 0,00; o teste falhou na comparação com 0,00.
- Após a correção, os três testes de `DashboardIT` passaram, incluindo o cenário concorrente: resumo e gráfico preservam 0,00 no snapshot corrente, e a consulta seguinte observa os 100,00 confirmados.
- Inspeção estática do frontend com `vercel:react-best-practices` e `web-design-guidelines`. Fonte: [Web Interface Guidelines](https://raw.githubusercontent.com/vercel-labs/web-interface-guidelines/main/command.md), consultada em 2026-10-22. Escopo: `store-dashboard.tsx`, integração em `store-detail.tsx` e na rota da loja, helpers e estilos do dashboard. Sem novos achados relevantes no código inspecionado; teclado, leitor de tela, zoom e conferência visual do gráfico continuam pendentes para SC-023. Não implica conformidade WCAG.
- Admin: `npm run lint`, `npm run typecheck`, `npm run build` e `npm run test:smoke` executados novamente e aprovados.
- Backend após a correção: `.\mvnw.cmd -B -ntp verify` completo com JDK 21, 83 testes unitários e 136 de integração, sem falhas, erros ou ignorados; `BUILD SUCCESS`. No encerramento dos contextos descartáveis, houve avisos de conexão/agendamento e mensagem do Surefire sobre timeout de 30 segundos para encerrar a JVM, sem falha no ciclo Maven.
