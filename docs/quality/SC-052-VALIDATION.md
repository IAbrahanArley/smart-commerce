# SC-052 — Sessão de caixa

Data: 2026-11-03. Quarta tarefa da Sprint 7 ([SPRINT_07.md](../product/SPRINT_07.md)). A decisão C5 e a implementação estão no [ADR-0019](../architecture/adr/0019-cash-payables-receivables.md).

**Decisões do responsável (2026-11-03):**
- a diferença de caixa entra no resultado como linha própria, fora da margem de contribuição;
- o funcionário pode registrar despesa paga pela gaveta;
- sangria e suprimento sem conta são retirada e aporte do dono.

## Critérios de aceite

- O funcionário nunca vê o valor esperado.
- Fechar duas vezes falha; movimentar uma sessão fechada falha (no banco).
- A diferença aparece no histórico do dono e no relatório.
- Só uma sessão aberta por gaveta.
- Só o dono reabre, com motivo.
- Quem é de outra loja não vê nem altera nada.

## Verificação

| Verificação | Resultado |
| --- | --- |
| `CashIT` (Postgres real) | 2 aprovados. **Fluxo do funcionário:** a primeira contagem vira saldo inicial; sangria para o banco (transferência), retirada e aporte do dono e despesa paga pela gaveta (conta a pagar já paga, variável). Fechamento às cegas com 110,00 contados contra 115,00 esperados: a resposta não traz o esperado, a diferença nem o movimento de diferença; a gaveta fica com 110,00 e o livro com −5,00. Repetir o fechamento devolve o mesmo; outro fechamento e movimento depois do fechamento dão 409. O funcionário recebe 404 na sessão fechada e no histórico; o dono vê esperado, diferença e a linha "Diferenças de caixa" (−5,00) no relatório. Uma abertura que não bate com a gaveta também vira diferença. **Concorrência e banco:** 4 aberturas simultâneas da mesma gaveta abrem uma única sessão; o banco recusa movimento em sessão fechada e reabrir pelo `UPDATE`. **Reabertura:** só o dono reabre; reabrir de novo devolve a mesma sessão. **Outra loja:** 404 em tudo. **Validação:** tipo desconhecido, valor negativo, categoria em sangria e despesa sem categoria dão 400 |
| `StaffIT` (matriz de papéis) | aprovado. Só `GET /cash` foi liberado na varredura; as outras rotas do caixa recusam o funcionário sem corpo válido, e o fluxo de sucesso dele está no `CashIT` |
| `ExpenseIT`, `ExpenseInputTest`, `FinanceReportIT`, `AccountIT`, migrações | aprovados. O `PayablesMigrationIT` passou a migrar só até a V42, porque a V43 acrescenta uma coluna ao livro |
| Backend `verify` completo (formatação, ArchUnit, unitários e integração), depois das correções da revisão | aprovado: 121 unitários e 219 de integração, 0 falhas (1 ignorado, já existente) |
| Admin: `lint`, `typecheck`, `build`, `test:smoke` | aprovados. O novo `smoke-cash.mjs` cobre a contagem (zero aceito, vazio recusado), o texto de sobra e falta, as contas de destino sem o Mercado Pago e o extrato lendo os tipos novos de movimento |

**Navegador** (Chromium do app, admin em dev contra um backend sintético local, fora do repositório):
- **Funcionário:** o menu tem "Visão geral" e "Caixa", e a página inicial leva ao caixa.
  - Abre com R$ 150,00, registra uma sangria de R$ 50,00 para o "Banco do Bairro" (a lista de destino não tem o Mercado Pago) e fecha contando R$ 95,00.
  - A mensagem é só "Contagem registrada"; nenhum valor esperado aparece em momento nenhum.
- **Dono:** "Caixa" no menu e um histórico com contado, esperado e "Falta de R$ 5,00" em vermelho, além do botão "Reabrir" com nome acessível completo.

## Achados e disposição

| Achado | Disposição |
| --- | --- |
| Categoria ausente no corpo gerava `NullPointerException`, respondida como 403 (também nas despesas, desde a SC-020) | **Corrigido**, com testes |
| Em requisições simultâneas, a mesma sessão de login ficou com o token CSRF desatualizado no teste | **Registrado como artefato do teste** (uma pessoa não dispara pedidos simultâneos com o mesmo login); o teste segue com um login novo |
| Botões "Abrir caixa" e "Fechar caixa" com o mesmo nome dos botões do formulário | **Corrigido:** os botões das gavetas levam o nome da gaveta |
| Revisão (alta): o movimento de diferença aparecia na lista do funcionário e revelava o esperado (contado − diferença) e, na abertura, o saldo da gaveta | **Corrigido:** a diferença não aparece para o funcionário; testado na resposta do fechamento |
| Revisão (média): o id de pedido da reabertura era previsível; um funcionário poderia usá-lo antes numa abertura e bloquear a reabertura; repetir uma abertura antiga devolvia ao funcionário uma sessão já fechada | **Corrigido:** id aleatório (a reabertura continua única pela sessão de origem) e a repetição de uma abertura fechada dá 404 ao funcionário |
| Revisão (média): o funcionário pode operar qualquer conta do tipo dinheiro, e abrir com zero uma gaveta com dinheiro registra a falta | **Aceito, conforme o produto:** toda conta "Dinheiro (gaveta)" é uma gaveta. O valor não é mostrado ao funcionário, e a diferença fica com quem abriu, visível ao dono |
| Revisão (baixa): valores acima do limite dariam 503 | **Sem ação:** o limite de 15 dígitos da entrada já fica abaixo do limite do banco |
| Revisão (baixa): repetir uma despesa da gaveta depois do fechamento dá 409; reutilizar o mesmo `requestId` com e sem conta cria dois movimentos | **Aceito:** o admin gera um id novo a cada tentativa e reaproveita o mesmo só na repetição idêntica |

## Limitações

- O histórico do dono mostra a diferença do fechamento; a diferença de abertura aparece na sessão e no relatório, não na tabela do histórico.
- A venda em dinheiro e o recebimento de crediário entram no caixa com a SC-053 e a SC-055.
- Leitor de tela, Axe e Lighthouse não foram executados, como nas revisões anteriores.
