# SC-055 — Crediário (primeira parte)

Data: 2026-11-04. Sétima tarefa da Sprint 7 ([SPRINT_07.md](../product/SPRINT_07.md)). As decisões C9 e C10 e a implementação estão no [ADR-0019](../architecture/adr/0019-cash-payables-receivables.md); o cliente é a `store_customer` do [ADR-0021](../architecture/adr/0021-store-crm.md) (R2).

**Decisões do responsável (2026-11-04):**
- **Cliente:** é a `store_customer`, criada já nesta tarefa.
- **Retenção:** fica registrada como pendente de orientação jurídica; a anonimização vai para a SC-074.
- **Funcionário:** vê só o cliente que atende.
- **Entrega:** em duas partes. As contas a receber unificadas (cartão presencial e Mercado Pago) ficam para a segunda.

## Critérios de aceite (desta parte)

- O saldo do cliente é a soma das parcelas menos os recebimentos.
- Venda acima do limite é recusada; recebimento acima do saldo é recusado.
- A última parcela absorve os centavos.
- Nenhum dado pessoal em log, URL ou mensagem de erro.
- Quem é de outra loja não vê nem altera nada.

## Verificação

| Verificação | Resultado |
| --- | --- |
| `CreditIT` (Postgres real) | 2 aprovados.<br>**Cliente:** o dono cadastra com e-mail, observação e limite. A repetição devolve o mesmo cliente; o mesmo pedido com outros dados dá 409; o mesmo e-mail com outra caixa de letras dá 409 `EMAIL_TAKEN`.<br>**Funcionário:** cadastrar com limite dá 400. Ele vê o telefone mascarado e não acha cliente por parte do telefone nem por e-mail, só pelo nome ou pelo telefone completo. Editar dá 404.<br>**Venda:** 100,00 em 3 parcelas vira 33,33 + 33,33 + 33,34, com vencimentos mensais. O estoque cai, o livro fica sem liquidação e nenhum dinheiro se move. A repetição devolve a mesma venda; outro primeiro vencimento dá 409. Na lista, o funcionário não vê o nome do cliente; o dono vê.<br>**Recusas:** acima do limite, 409 `CREDIT_LIMIT`. Vencimento no passado, 13 parcelas ou sem cliente dão 400.<br>**Recebimento:** 40,00 no caixa aberto quita a primeira parcela e 6,67 da segunda, e a gaveta soma 40,00. A repetição não duplica; o mesmo pedido com outro valor dá 409. Além do saldo, 409 `OVERPAYMENT`; gaveta sem sessão, 409 `ACCOUNT_NOT_ALLOWED`; sem destino, 400.<br>**Cancelamento:** venda com recebimento dá 409 `CREDIT_RECEIVED`, e o banco recusa também. Venda sem recebimento é cancelada: as parcelas somem do saldo e o estoque volta.<br>**Quitação:** o resto no banco zera o saldo, liquida o `SALE` e mostra o histórico.<br>**Só de acréscimo:** `UPDATE` e `DELETE` são recusados.<br>**Vencidos:** a lista do dono mostra totais, cliente vencido e a parcela marcada; o funcionário recebe 404 na lista.<br>**Outra loja:** 404 na ficha, no recebimento e na busca; a busca na própria loja não acha o cliente da outra; vender com o cliente da outra dá 404.<br>**Concorrência:** dois recebimentos de 60,00 sobre 100,00 ao mesmo tempo: um passa, o outro dá 409 |
| `StaffIT`, `CounterSaleIT`, `CounterImportIT` | aprovados. A matriz do `StaffIT` cobre as rotas novas: com corpo vazio, o funcionário não tem sucesso em nenhuma |
| Backend `verify` completo (formatação, ArchUnit, unitários e integração), depois das correções da revisão | aprovado: 126 unitários e 229 de integração, 0 falhas (1 ignorado, já existente) |
| Admin: `lint`, `typecheck`, `build`, `test:smoke` | aprovados. O novo `smoke-credit.mjs` cobre a divisão das parcelas igual à do backend, os vencimentos mensais (fim de mês e ano bissexto), o dia de hoje em São Paulo, os limites do primeiro vencimento, o telefone, o nome e os filtros |

**Navegador** (Chromium do app, admin em dev contra um backend sintético local, fora do repositório; o painel ficou oculto, então as interações foram por referência de acessibilidade e a leitura por texto, sem capturas de tela):
- **Dono, lista:** totais "a receber" e "vencido", abas e clientes com saldo, próximo vencimento e situação.
- **Dono, ficha:** contato, limite e crédito disponível, parcelas (a vencida marcada), recebimento e histórico.
  - Receber 100,01 sobre 100,00 mostra o erro no campo.
  - Receber 50,00 quita a parcela vencida e parte da seguinte, e a mensagem mostra o que ainda falta.
- **Venda no crediário:** sem cliente, mostra o erro. A busca "joão" acha o cliente. Com 3 parcelas, a prévia mostra 49,66 + 49,66 + 49,68 para 149,00. A venda aparece na lista com o cliente e as parcelas.
- **Funcionário:** o menu tem "Crediário". A busca mostra o telefone mascarado. A ficha não mostra e-mail, observação, limite, histórico nem "Editar".

## Achados e disposição

Revisão independente de segurança e correção (agente revisor, só leitura, 2026-11-04): nenhum achado crítico ou alto. Isolamento confirmado: toda consulta é filtrada pela loja, as FKs são compostas e a loja é travada antes das escritas.

| Achado | Disposição |
| --- | --- |
| Erro durante o desenvolvimento: `lower(?)` e `char_length(?)` sem tipo derrubavam a busca (503) | **Corrigido** antes da revisão; o teste cobre |
| Erro durante o desenvolvimento: transações só de leitura recusavam a trava da loja (`FOR SHARE`) | **Corrigido**: busca, ficha e lista usam transação normal |
| Erro durante o desenvolvimento: as rotas novas não estavam na lista do Spring Security (403) | **Corrigido** em `BootstrapSecurityConfiguration` |
| Revisão (média): o funcionário podia descobrir o telefone inteiro e o e-mail buscando dígito a dígito | **Corrigido**: ele busca só por nome ou pelo telefone completo; testado |
| Revisão (média): a lista de vendas na loja mostrava ao funcionário o nome de todos os clientes do crediário | **Corrigido**: ele vê o nome só na venda que acabou de registrar; testado |
| Revisão (baixa): repetir o pedido com outro primeiro vencimento devolvia a venda original | **Corrigido**: dá 409; testado |
| Revisão (baixa): parcela e cliente da venda, e recebimento e conta da sessão, não eram conferidos no banco | **Corrigido**: triggers na V46 |
| Revisão (baixa): recusa de trigger aparecia como "serviço indisponível" | **Corrigido**: vira 409 `DATA_CHANGED` |
| Revisão (baixa): total menor que um centavo por parcela só era recusado depois das escritas | **Corrigido**: conferido antes de gravar |
| Revisão (baixa): o javadoc dizia que o vencido do cliente era só do dono | **Corrigido**: o funcionário vê o vencido do cliente que atende, como decidido |

## Limitações

- **Relatório financeiro:** conta o crediário como recebido só na quitação da venda. Os recebimentos parciais aparecem nas contas, mas não no "caixa do período", até o relatório ler os movimentos (C3).
- **Estorno:** um recebimento ainda não pode ser estornado.
- **Segunda parte:** as contas a receber unificadas (C10), com a baixa do cartão presencial e o Mercado Pago como "data desconhecida".
- **Dados pessoais:** retenção e anonimização aguardam orientação jurídica (C9) e a SC-074.
- **Testes manuais:** leitor de tela, Axe e Lighthouse não foram executados, como nas revisões anteriores.
