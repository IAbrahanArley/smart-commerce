# SC-020 — Despesas manuais: validação

Data: 2026-10-21. Base: [ADR-0015](../architecture/adr/0015-finance-dashboard.md) (F6, F7, F10) e [SPRINT_05.md](../product/SPRINT_05.md).

**Estado:** implementada e validada com PostgreSQL via Testcontainers e com o smoke do admin. Não houve verificação manual no navegador.

Atualização de 2026-10-22: [validação das telas](SPRINT-05-UI-VALIDATION.md) com API sintética no navegador, correções de rascunho, foco e datas, testes adicionais e limitações registradas. A ausência de navegador acima descreve a validação inicial de 2026-10-21.

## Critérios de aceite

| Critério | Evidência |
| --- | --- |
| O OWNER registra e corrige despesas com trilha de quem e quando | `ExpenseIT.ownerRecordsCancelsAndCorrectsExpenses…`: cancelar guarda `cancelled_by`, `cancelled_at` e motivo; a despesa cancelada continua listada; a correta é registrada de novo |
| Validação de valor (positivo, 2 casas), datas e categoria | `ExpenseInputTest` (15 casos inválidos) e `ExpenseIT.invalidData…` (400) |
| A correção não apaga o histórico | a despesa e seu lançamento ficam; o cancelamento acrescenta `EXPENSE_REVERSAL` com a mesma competência e liquidação, e o total do mês no livro bate com a lista |
| CSRF e OWNER | `ExpenseIT.invalidData…`: sem CSRF 403, sem sessão 401 |
| A/B | o OWNER B não lista, não cria na loja A nem cancela despesa de A (404); nada muda |
| Clique duplo e nova tentativa | `requestId` por envio: quatro envios simultâneos criam uma despesa e um lançamento; o mesmo `requestId` com outro conteúdo recebe 409 |

## O que foi entregue

- **Migração `V23__expenses.sql` (aditiva):**
  - tabela `expense`: categoria da lista fixa (frete, embalagem, marketing, aluguel, pessoal, outros), tipo fixa ou variável, descrição de 3 a 200 caracteres, valor positivo com 2 casas, data de competência, data de pagamento opcional, `request_id` único, autor e dados de cancelamento obrigatórios em conjunto;
  - `finance_entry.expense_id` (FK composta por tenant) e o tipo `EXPENSE_REVERSAL`. As CHECKs do livro foram ampliadas; a CHECK antiga de tipo, sem nome explícito, é removida pela definição.
- **Backend (`finance`):**
  - `ExpenseService`: criar grava a despesa e o lançamento `EXPENSE` (negativo) na mesma transação, com competência à meia-noite de São Paulo e liquidação na data de pagamento, quando houver. Cancelar grava `EXPENSE_REVERSAL` e não desfaz o que já foi gravado. A lista do mês traz os totais ativos, fixos e variáveis.
  - Rotas, todas com sessão e vínculo OWNER conferido no SQL; as mutações também com CSRF:
    - `GET /api/tenants/{t}/expenses?month=AAAA-MM`;
    - `POST /api/tenants/{t}/expenses`;
    - `POST /api/tenants/{t}/expenses/{id}/cancel`.
- **Admin:**
  - página **Despesas** (`/lojas/{id}/despesas?mes=AAAA-MM`) e item no menu da loja;
  - totais do mês e tabela com categoria, tipo, data, pagamento e valor (riscado quando cancelada, com o motivo);
  - ações "Corrigir" (cancela e reabre o formulário com os valores) e "Cancelar", ambas com motivo;
  - formulário que aceita valores no formato brasileiro (`1.234,56`) e mostra os erros junto de cada campo.
- **Ajuste de texto:** a lista de pedidos ainda dizia "O pagamento ainda não está disponível", o que deixou de valer na Sprint 4. Foi corrigido.

## Testes

| Teste | Cobertura |
| --- | --- |
| `ExpenseInputTest` (2, novo) | listas fixas, valor, datas (inexistente, antes de 2000, competência mais de um ano à frente, pagamento futuro), texto de uma linha, motivo de até 300 caracteres |
| `ExpenseIT` (3, novo) | ciclo completo pela API, conflito de `requestId`, cancelar duas vezes, competência em São Paulo, totais do livro, A/B, CSRF, sessão, concorrência |
| `FinanceLedgerMigrationIT` | V22 continua válida com a V23 depois |
| `ArchitectureTest` | camadas do módulo novo |
| Smoke do admin | `lib/expenses.ts` (mês da URL no fuso de São Paulo, navegação entre meses, valores brasileiros, textos) e a rota `/despesas` |

**Comandos executados:**
- backend: `mvnw verify` completo, com 83 testes unitários e 131 de integração, todos passando;
- admin: `lint`, `typecheck`, `build` e `test:smoke`, todos passando.

## Decisões de implementação

- **Data do pagamento:** não pode ser futura; mudar a situação de paga é uma correção (cancelar e registrar de novo), conforme o F6.
- **Competência:** pode estar até um ano à frente, para registrar despesas já previstas.
- **Lista do mês:** limitada a 500 despesas, sem paginação no MVP.
- **Despesas variáveis × margem:** o F5 usa só as variáveis na margem de contribuição; o formulário explica isso.
