# SC-023 — Revisão integrada da Sprint 5: validação

Data: 2026-10-24. Escopo: SC-019 (livro), SC-020 (despesas), SC-021 (relatório) e SC-022 (dashboard), mais a validação de telas já integrada no PR #28 ([SPRINT-05-UI-VALIDATION.md](SPRINT-05-UI-VALIDATION.md)). Base: [ADR-0015](../architecture/adr/0015-finance-dashboard.md), checklists de [SECURITY.md](../agents/SECURITY.md) e [QA.md](../agents/QA.md). Código revisado: `main` em `06c032d`.

A revisão foi feita no código, pelo coordenador, apoiada nos testes automatizados. Não houve teste de invasão nem dados reais.

## Resultado

**Nenhuma falha de isolamento entre lojas, de autorização ou de cálculo.** Houve um ajuste de defesa em profundidade, duas lacunas de documentação corrigidas e três riscos registrados para decisão.

| Severidade | Local | Achado | Disposição |
| --- | --- | --- | --- |
| P3 | `finance_entry` (V22) | O livro é só de acréscimo (F7) apenas por convenção do código; o banco aceitaria `UPDATE` e `DELETE` | **Corrigido:** a V25 cria um trigger que recusa `DELETE` e qualquer `UPDATE` exceto o preenchimento único de `settled_at` (caminho da SC-018). Há teste novo |
| P3 | `DATA_MODEL.md`, `ARCHITECTURE.md` | Faltavam a V24 (`advisor_usage`, SC-025) e a descrição do módulo `advisor` | **Corrigido** nesta tarefa |
| Risco | `OrderPaymentApproval.approved` | Pedido pago **sem estoque** (P4) lança o CMV na aprovação, embora a mercadoria ainda não tenha saído; o estorno só acontece no reembolso | **Registrado** para decisão de produto. A alternativa é lançar o CMV só quando o estoque for coberto. Não mudou nada |
| Risco | `expense.description` | Texto livre, que pode conter dado pessoal (por exemplo, nome de funcionário em "Pessoal"), guardado para sempre porque despesas nunca são apagadas (F6) | **Registrado** com a política de retenção (mesma validação jurídica do D2). As descrições não vão para o assistente (SC-025) |
| Observação | `FinanceReportRepository.receivable` | O "a receber" é acumulado até o fim do período, não só do período | **Como desenhado:** F3 define o a receber como tudo o que foi aprovado e ainda não liberado |

## Checklist de segurança

| Item | Verificação | Resultado |
| --- | --- | --- |
| Autenticação | todas as rotas `/api/tenants/*/expenses`, `/finance`, `/dashboard` e `/alerts` exigem sessão na cadeia de segurança | Aprovado |
| Tenant e IDOR | <ul><li>o tenant vem da rota e só vale pelo vínculo OWNER ativo e verificado, conferido no serviço antes de qualquer leitura (`ownsStore`)</li><li>auditoria mecânica de todo SQL de `finance` e de `InventoryInsightRepository`: toda consulta filtra por `tenant_id`, e os prefixos reaproveitados recebem `WHERE tenant_id=?`</li><li>as subconsultas de taxa e o `JOIN` de despesas casam o tenant</li></ul> | Aprovado. Testes A/B em `ExpenseIT`, `FinanceReportIT` e `DashboardIT` |
| CSRF | criar e cancelar despesa são `POST` com CSRF; leituras são `GET` | Aprovado |
| Idempotência e concorrência | <ul><li>lançamentos com chave única por fato (`ON CONFLICT DO NOTHING`), gravados na mesma transação de `PAID` e `REFUNDED`, sob lock do pedido</li><li>despesas com `request_id` único; cancelamento sob lock</li><li>dashboard e relatório leem na mesma transação `REPEATABLE_READ`</li></ul> | Aprovado. Webhook repetido em `PaymentConfirmationIT`; corrida no `DashboardIT` |
| Imutabilidade do livro | antes, só por convenção; agora o trigger da V25 garante | Corrigido |
| Dados pessoais | os relatórios e o dashboard não expõem dados de comprador; as respostas não levam SKU nem custo no dashboard; o texto livre de despesa é risco registrado | Aprovado, com o risco registrado |
| Segredos | nenhum segredo novo na Sprint 5 | Não aplicável |

## Integridade dos números (ADR-0015)

| Regra | Onde | Resultado |
| --- | --- | --- |
| F1: receita na confirmação do pagamento (`paid_at`) e reembolso na confirmação do reembolso, calendário de São Paulo | `recordSale`, `recordRefund`, `ReportPeriod` | Aprovado |
| F3: a receber separado de caixa | `receivable`, `settled` | Aprovado (ver observação) |
| F4: CMV pelo snapshot; custo ausente vira "parcial"; reembolso com devolução estorna o CMV | `saleFigures`, `recordRefund`, contagem `cost_incomplete` | Aprovado (ver risco do pedido sem estoque) |
| F5: margem completa só sem taxa nem custo faltando; percentual sobre receita menos reembolsos e nunca dividindo por zero | `FinanceReportService.report` | Aprovado |
| F8: ticket = receita ÷ pedidos pagos, sem dividir por zero; estoque baixo ≤ 5; sem giro há 30 dias | relatório e `InventoryInsightService` | Aprovado |
| F10: NUMERIC/BigDecimal, sem float; HALF_EVEN só em divisões; sempre 2 casas | migrações, serviço e `formatBRL` (agora com negativos) | Aprovado |

## Testes

| Teste | Resultado |
| --- | --- |
| `FinanceReportIT`: novo `ledgerRowsCannotBeChangedOrDeletedOnlySettledOnce` | <ul><li>alterar valor ou competência e apagar são recusados</li><li>`settled_at` é preenchido uma vez e a segunda tentativa é recusada</li><li>junto com outro campo também é recusado</li><li>o valor fica intacto</li></ul> |
| `FinanceReportIT`, `ExpenseIT`, `FinanceLedgerMigrationIT`, `PaymentConfirmationIT`, `ArchitectureTest` | passando com a V25: o preenchimento inicial da V22, os reembolsos e as despesas não alteram linhas |
| `mvnw verify` completo (2026-10-24) | 86 testes unitários e 147 de integração, todos passando |

## Acessibilidade e interface

A validação de telas da Sprint 5 (PR #28) cobriu:
- quatro larguras;
- teclado parcial;
- estados de erro;
- contraste pontual;
- tabela equivalente ao gráfico.

**Continuam pendentes**, sem alegação de conformidade WCAG completa:
- leitor de tela;
- zoom manual a 200% e 400%;
- espaçamento de texto;
- Axe;
- confirmação nativa de saída com rascunho.

## O que falta para fechar a Sprint 5

1. **SC-018 (taxa, líquido e liberação):** depende da validação real no sandbox do Mercado Pago. Até lá, todo período com vendas aparece como "parcial".
2. **Acessibilidade:** a revisão manual listada acima.
3. **Riscos registrados:** decisão de produto sobre o CMV de pedido pago sem estoque, e política de retenção das descrições de despesa (junto com a validação jurídica do D2).
