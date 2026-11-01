# SC-050 — Contas financeiras e movimentos

Data: 2026-11-01. Segunda tarefa da Sprint 7 ([SPRINT_07.md](../product/SPRINT_07.md)). As decisões C2 e C3 e a implementação estão no [ADR-0019](../architecture/adr/0019-cash-payables-receivables.md).

## Critérios de aceite

- O saldo bate com a soma do extrato.
- Nenhum movimento se altera ou se apaga.
- A transferência nunca fica pela metade.
- Repetir um pedido não duplica nada.
- Só o dono da loja acessa.

## Verificação

| Verificação | Resultado |
| --- | --- |
| `AccountIT` (Postgres real) | 4 aprovados. **Saldo, idempotência e extrato:** contas padrão e Mercado Pago depois de conectar; conta, ajuste e transferência idempotentes; mesmo pedido com outro conteúdo dá 409; transferência para a mesma conta é recusada; extrato com saldo antes e depois e saldo após cada movimento; períodos inválidos, anteriores a 2000 ou absurdos dão 400. **Validação:** dinheiro e datas inválidos e limite de 20 contas. **Banco e concorrência:** o banco recusa `UPDATE` e `DELETE` dos movimentos; 8 transferências ao mesmo tempo, com 4 repetidas, gravam 5 transferências inteiras. **Acesso:** outro dono e funcionário recebem 404 em todas as rotas, e nada é gravado |
| `StaffIT` (matriz de papéis) | as rotas novas entram automaticamente na varredura: nenhuma responde com sucesso a um funcionário |
| Backend `verify` completo (formatação, ArchUnit, unitários e integração) | aprovado: 118 unitários e 211 de integração, 0 falhas (1 ignorado, já existente). Os `ERROR` do agendador do Spring Session ao derrubar o banco descartável são o ruído conhecido de [setup.md](../setup.md) |
| Admin: `lint`, `typecheck`, `build`, `test:smoke` | aprovados. O novo `smoke-accounts.mjs` cobre os leitores, a leitura de valores em reais (milhar, decimal, negativo e casos recusados), saldo grande, dia no fuso de São Paulo e mensagens |

**Navegador** (Chromium do app, admin em dev contra um backend sintético local, fora do repositório):
- **Contas:** cards com saldo, e saldo negativo em vermelho.
- **Transferência:** enviada vazia, mostra erro em cada campo e põe o foco no primeiro; com valor "1.234,50", é registrada e atualiza os saldos.
- **Extrato:** saldo antes e depois, transferência com o nome da conta do outro lado e saldo após cada movimento.
- **Ajuste:** um ajuste negativo atualiza o saldo atual.

## Achados e disposição

| Achado | Disposição |
| --- | --- |
| CHECK com `system_key` nulo passaria para `PAYMENT_PROVIDER` sem chave | **Corrigido antes dos testes** com `IS NOT DISTINCT FROM` |
| O extrato rodava em transação só de leitura, e o PostgreSQL recusa a trava `FOR SHARE` da conferência de dono nesse modo (503 para todos) | **Corrigido**, com teste |
| Valores saíam como número JSON | **Corrigido:** saem como texto decimal, como no resto da API |
| Revisão (média): "1234.567" virava R$ 1.234.567,00 | **Corrigido:** ponto só vale como milhar em grupo bem formado; um ponto com 1 ou 2 casas é decimal; o resto é recusado |
| Revisão (baixa): saldo com mais de 16 dígitos quebrava a página | **Corrigido:** saldos sem limite de dígitos na leitura |
| Revisão (baixa): data absurda no extrato dava 500 | **Corrigido:** período entre 2000 e hoje + 1 ano |
| Revisão (baixa): depois de um 409, o formulário ficava preso no mesmo pedido; período com uma data só era ignorado | **Corrigido:** o próximo envio usa um pedido novo; o período pede as duas datas |
| Revisão (baixa): o banco não impõe que um `transfer_id` tenha exatamente dois movimentos; `TRUNCATE` e desligar triggers passam por cima do só de acréscimo | **Aceito e registrado:** o aplicativo grava sempre o par na mesma transação; `TRUNCATE` só é usado nos bancos descartáveis de teste |
| Revisão (baixa): nada impede saldo negativo nem ajuste e transferência manuais na conta do Mercado Pago | **Decidido pelo responsável em 2026-11-01:** a conta fica só para leitura, recusa ajuste e transferência (testado) e sai dos formulários do admin. As outras contas podem ficar negativas, como uma sangria acima do esperado |

## Limitações

- O relatório financeiro ainda não lê os movimentos, e vendas e despesas ainda não geram movimentos (próximas tarefas).
- Leitor de tela, Axe e Lighthouse não foram executados, como nas revisões anteriores.
