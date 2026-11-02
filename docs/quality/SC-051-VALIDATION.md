# SC-051 — Contas a pagar

Data: 2026-11-02. Terceira tarefa da Sprint 7 ([SPRINT_07.md](../product/SPRINT_07.md)). A decisão C4 e a implementação estão no [ADR-0019](../architecture/adr/0019-cash-payables-receivables.md).

**Decisões do responsável (2026-11-02):**
- cada parcela conta no mês em que vence;
- uma conta paga pode ser cancelada, com estorno do valor à conta de saída.

## Critérios de aceite

- Uma duplicata em 3 parcelas gera 3 contas.
- Pagar uma conta mensal cria a do mês seguinte.
- As despesas antigas aparecem com a situação certa, e o livro e o relatório do período não mudam.
- Baixa com data, conta de saída, juros/multa e desconto. A conta do Mercado Pago não paga.
- Repetir a baixa não paga duas vezes; outra baixa da mesma conta é recusada.
- Cancelar uma conta paga estorna tudo.
- Só o dono da loja acessa.

## Verificação

| Verificação | Resultado |
| --- | --- |
| `ExpenseIT` (Postgres real) | 8 aprovados (os 3 da SC-020, adaptados à visão por vencimento, e 5 novos). **Parcelas:** R$ 1.000 em 3x vira 333,33 + 333,33 + 333,34, uma por mês e cada uma com seu `EXPENSE` no mês do vencimento; repetir o pedido não cria nada; 25 parcelas, parcela de menos de um centavo e parcela mensal dão 400. **Baixa:** a liquidação do lançamento fica no dia do pagamento; juros e desconto viram lançamentos próprios; um único `EXPENSE_PAYMENT` de −505,00; a próxima conta mensal é criada uma vez. Repetir a mesma baixa dá 200 e outra baixa dá 409. Desconto que zera a conta e data futura dão 400; a conta de outra loja e outro dono dão 404; a conta do Mercado Pago dá 409. **Cancelamento:** cancelar uma conta paga devolve o saldo e zera a soma dos lançamentos; uma conta cancelada não pode ser paga. Cancelar uma conta mensal paga cancela também a sucessora em aberto. Uma repetição atrasada de um cadastro que foi pago depois devolve a mesma conta. **Filtros:** "a pagar" e "pago" do mês, total vencido de todos os meses e filtro de vencidas de qualquer mês |
| `ExpenseInputTest` | 5 aprovados: divisão das parcelas, datas que valem uma pela outra, combinações inválidas (parcelas com recorrência ou pagamento, pagamento sem conta e conta sem pagamento) e baixa com juros e desconto opcionais |
| `PayablesMigrationIT` | aprovado. Migra até a V41, grava despesas aberta, paga e cancelada com seus lançamentos e migra até a V42. **Livro:** idêntico, linha por linha. **Despesas:** vencimento = competência, a paga sem conta de saída e a cancelada mantida |
| `FinanceReportIT`, `AccountIT`, `FinanceLedgerMigrationIT` | aprovados. As despesas pagas desses testes agora informam a conta (o "Caixa da loja"), e o caixa do período continua igual |
| `StaffIT` (matriz de papéis) | aprovado: a rota nova `/expenses/{id}/pay` entra na varredura, e nenhuma rota responde com sucesso a um funcionário |
| Backend `verify` completo (formatação, ArchUnit, unitários e integração) | aprovado: 121 unitários e 217 de integração, 0 falhas (1 ignorado, já existente) |
| Admin: `lint`, `typecheck`, `build`, `test:smoke` | aprovados. O smoke cobre a prévia das parcelas e do total pago, os valores opcionais e os filtros da URL |

**Navegador** (Chromium do app, admin em dev contra um backend sintético local, fora do repositório):
- **Lista:** totais do mês, rótulo "conta não informada" nas despesas pagas antes da V42, e parcela, fornecedor e documento na linha.
- **Pagamento:** o formulário não oferece a conta do Mercado Pago; com juros de "18,00" e desconto de "5", a prévia mostra R$ 1.813,00. O pagamento é registrado, a mensagem avisa da próxima conta mensal e o foco vai para ela.
- **Cadastro:** enviado sem categoria, mostra os erros e põe o foco no primeiro campo. Com 3 parcelas, a ajuda mostra "2 × R$ 333,33 e a última de R$ 333,34", e o botão vira "Registrar 3 parcelas".
- **Vencidas:** o filtro esconde a navegação por mês e mostra a etiqueta vermelha.
- **Celular:** a 375 px, a página não tem rolagem horizontal.

## Achados e disposição

| Achado | Disposição |
| --- | --- |
| A CHECK nova `expense_recurrence_check` colidia com o nome que o PostgreSQL dá à CHECK da coluna `recurrence` | **Corrigido antes da entrega:** renomeada para `expense_recurrence_installment_check` |
| Revisão (média): corrigir uma conta mensal paga deixava a sucessora em aberto, e a conta corrigida criaria outra para o mesmo mês | **Corrigido:** cancelar uma conta mensal paga cancela também a sucessora ainda em aberto, com o motivo; o aviso do cancelamento explica isso. Testado |
| Revisão (baixa): valor + juros acima do limite do movimento daria 500 | **Corrigido:** dá 400 |
| Revisão (baixa): se o lançamento da despesa não pudesse receber a liquidação, a conta seria debitada mesmo assim | **Corrigido:** a baixa é desfeita inteira |
| Revisão (baixa): "a pagar" e "pago" somavam a lista limitada a 500 contas | **Corrigido:** somados no banco sobre todas as contas do mês |
| Revisão (baixa): a repetição atrasada de um cadastro que foi pago depois dava 409 | **Corrigido:** o pagamento só é comparado quando o cadastro o trazia. Testado |
| Revisão (baixa): mensagem de 404 no cadastro falava da conta de pagamento mesmo sem pagamento; corrigir uma conta paga não lembrava de marcá-la paga de novo | **Corrigido** |
| Revisão (baixa): a próxima conta mensal desliza depois de um dia 31 (31 → 28 → 28) | **Aceito e registrado** no ADR |
| Revisão (baixa): o botão "Pagar" fica desativado sem explicação se a loja não tiver conta manual | **Aceito:** a gaveta "Caixa da loja" é criada no primeiro uso e nunca é apagada, então sempre existe |

## Limitações

- O relatório financeiro continua lendo a liquidação do livro (que agora coincide com os pagamentos); ler direto dos movimentos fica para quando as vendas também gerarem movimentos.
- Baixa só integral; pagamento parcial de conta a pagar não está no escopo (C4).
- Leitor de tela, Axe e Lighthouse não foram executados, como nas revisões anteriores.
