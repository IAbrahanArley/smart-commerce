# SC-054 — Importação de vendas presenciais por planilha

Data: 2026-11-03. Sexta tarefa da Sprint 7 ([SPRINT_07.md](../product/SPRINT_07.md)). A decisão C14 e a implementação estão no [ADR-0019](../architecture/adr/0019-cash-payables-receivables.md). Só CSV: ler `.xlsx` direto depende de aprovar uma biblioteca.

## Critérios de aceite

- Reenviar o mesmo arquivo não duplica nada.
- Nenhuma linha com erro é gravada.
- O estoque cai exatamente o que foi importado.
- A prévia não grava nada.
- Só o dono importa.

## Verificação

| Verificação | Resultado |
| --- | --- |
| `CounterImportParserTest` | 5 aprovados.<br>**Formatos:** Excel em português (Windows-1252, `;`, vírgula decimal, "R$ 1.234,50" entre aspas, cabeçalho com acentos e "Forma de pagamento") e UTF-8 com BOM e `,` dão as mesmas vendas e as mesmas impressões digitais.<br>**Duplicidade:** vendas idênticas sem código são distintas e estáveis quando o arquivo ganha uma linha.<br>**Erros por linha:** data futura ou inexistente, fórmula, quantidade zero, três casas, desconto igual ao total, fiado, forma desconhecida, formas diferentes na mesma venda, SKU repetido.<br>**Venda incompleta:** uma venda com uma linha errada espera inteira.<br>**Códigos:** o mesmo código em outro dia é outra venda.<br>**Linhas:** só separadores são ignoradas; um preço partido por vírgula num arquivo com `,` é recusado.<br>**Arquivo inteiro recusado:** vazio, acima de 1 MB, sem colunas, mais de 500 linhas, aspas sem fechar |
| `CounterImportIT` (Postgres real, multipart) | 2 aprovados.<br>**Prévia:** 3 vendas prontas (R$ 64,00), 2 com erro (SKU fora do catálogo, estoque insuficiente) e a linha da fórmula; nada gravado.<br>**Acesso:** funcionário e outro dono recebem 404.<br>**Conta do dinheiro:** sem conta, 409; conta de outra loja, 409.<br>**Confirmação:** as 3 vendas entram e o estoque cai o certo. A venda em dinheiro tem competência e liquidação no dia da planilha e o dinheiro entra na gaveta nesse dia; o Pix entra no banco; o débito tem taxa de 0,20 e recebimento no dia seguinte.<br>**Reenvio:** o mesmo arquivo importa 0 e mostra 3 já importadas.<br>**Estoque mudado:** a confirmação vê a mudança e importa só o que ainda cabe.<br>**Arquivos ruins:** vazio é "arquivo recusado"; sem colunas, 400; JSON no lugar de multipart, 400 |
| `CounterSaleIT`, `StaffIT`, `ProductImagesIT` | aprovados. A matriz do `StaffIT` cobre as rotas novas (o funcionário não tem sucesso), e o upload de fotos continua igual depois da mudança no filtro |
| Backend `verify` completo (formatação, ArchUnit, unitários e integração), depois das correções da revisão | aprovado: 126 unitários e 223 de integração, 0 falhas (1 ignorado, já existente) |
| Admin: `lint`, `typecheck`, `build`, `test:smoke` | aprovados. O smoke cobre o modelo da planilha, o texto das linhas e as recusas antes do envio (sem arquivo, extensão, vazio, mais de 1 MB) |

**Navegador** (Chromium do app, admin em dev contra um backend sintético local, fora do repositório; o arquivo foi anexado ao campo por script, porque o painel não abre o seletor de arquivos do sistema):
- **Sem arquivo:** mostra o erro e põe o foco no campo.
- **Prévia:** resumo com o foco, erro por linha, situação de cada venda e escolha da conta do dinheiro (gaveta ou banco).
- **Sem conta:** importar sem a conta põe o erro no campo, com o foco nele.
- **Depois de importar:** a mensagem recebe o foco, o arquivo continua escolhido e a prévia é refeita mostrando "Já importada" e o que ficou com erro, sem o botão de importar.

## Achados e disposição

| Achado | Disposição |
| --- | --- |
| O filtro de identidade só aceitava multipart nas rotas de imagens e limitava os outros corpos a 8 KB, então a planilha nunca chegava ao controller | **Corrigido:** as duas rotas entram na lista de uploads, com `X-CSRF-TOKEN` obrigatório |
| "Forma de pagamento" no cabeçalho não era reconhecida | **Corrigido:** o cabeçalho aceita variações; testado |
| "10,001" num arquivo com `;` virava 10.001,00 | **Corrigido:** milhar no estilo americano só vale em arquivo com `,`; testado |
| Revisão (alta): uma linha errada de uma venda com código deixava o resto ser importado sem ela, e a planilha corrigida gravava a venda de novo | **Corrigido:** a venda inteira espera; testado |
| Revisão (média): uma vírgula decimal num arquivo com `,` partia o preço, e ele era lido menor sem aviso | **Corrigido:** linha com mais células preenchidas que o cabeçalho é recusada; testado |
| Revisão (baixa): linhas só de separadores, códigos que recomeçam a cada dia, download do modelo em alguns navegadores, a confirmação escondia o que ficou de fora, a lista mostrava a hora da importação em vez do dia da venda | **Corrigido** |
| Revisão (baixa): corrigir uma venda já importada cria outra; uma venda importada e cancelada não volta pela planilha; dinheiro importado para uma gaveta que já mostrou sobra conta duas vezes | **Registrado** no ADR e na ajuda da tela (cancelar e registrar de novo; escolher outra conta) |
| Revisão (baixa): célula com quebra de linha entre aspas quebra a leitura; arquivo acima de 5 MB recebe a resposta padrão do Spring | **Aceito:** o modelo não tem campo de texto livre, e a tela recusa acima de 1 MB antes de enviar |

## Limitações

- `.xlsx` direto depende de aprovar uma biblioteca.
- O preço é o da planilha, e o custo é o do catálogo no dia da importação.
- Leitor de tela, Axe e Lighthouse não foram executados, como nas revisões anteriores.
