# Sprint 7 — Caixa, contas a pagar e a receber, venda presencial e crediário (planejamento)

Data: 2026-10-26. **C1–C14 do [ADR-0019](../architecture/adr/0019-cash-payables-receivables.md) aprovadas em 2026-10-26**, exceto a leitura direta de `.xlsx` em C14, que depende de aprovar uma biblioteca. Por enquanto só o planejamento fica registrado; cada tarefa exige autorização própria.

**Objetivo:**
- o lojista controla **num só lugar o que vende online e o que vende na loja física**, com **um só estoque**: cada produto vendido na loja física e lançado no livro de vendas, à mão ou por planilha, baixa o mesmo estoque da vitrine;
- sabe **quanto dinheiro tem e onde** (gaveta, banco, Mercado Pago), o que **vai pagar** e o que **vai receber**;
- o funcionário abre e fecha o caixa, registra sangria, suprimento, venda presencial e recebimento de fiado, sem ver números gerenciais.

Sem PDV: não há leitor de código de barras, cupom, TEF nem impressora. Nada aqui movimenta dinheiro de verdade; tudo é registro.

**Ordem:** em 2026-10-27 o responsável colocou a [Sprint 8](SPRINT_08.md) (vitrine V1) à frente desta sprint.

**Início (2026-11-01):** o responsável decidiu começar esta sprint deixando a SC-018 (taxas do Mercado Pago) e a SC-023 (revisão integrada da Sprint 5) pendentes. Na mesma data, as tarefas foram renumeradas de SC-031–SC-040 para **SC-049–SC-058** e o ADR de 0017 para **0019**, porque os números colidiam com a assinatura SaaS (SC-031 a SC-033 e ADR-0017 da Stripe). Se a sprint entra no escopo da entrega do MVP ainda não foi decidido.

**Dependências:**
- A SC-018 (taxa e liberação do Mercado Pago) continua pendente. Sem ela, os pagamentos online aparecem em "a receber, data desconhecida" (C10).
- A SC-030 (revisão integrada da Sprint 6) deve terminar antes da SC-057, que adiciona alertas ao módulo `advisor`.

## Gates de entrada

| Tarefa | Requer |
| --- | --- |
| SC-049 | C1 |
| SC-050 | C2, C3 |
| SC-051 | SC-050; C4; revisão da migração de `expense` |
| SC-052 | SC-049, SC-050; C5 |
| SC-053 | SC-049, SC-050, SC-052; C6, C7, C8 |
| SC-054 | SC-053; C14 (CSV); `.xlsx` só com a biblioteca aprovada |
| SC-055 | SC-053; C9, C10; orientação sobre prazo de retenção (C9) |
| SC-056 | SC-053; C11 |
| SC-057 | SC-051, SC-055, SC-030; C12 |
| SC-058 | SC-049 a SC-057 |

## SC-049 — Papel de funcionário (`STAFF`)

**Estado (2026-11-01):** autorizada e implementada ([validação](../quality/SC-049-VALIDATION.md)), com o limite por plano de 1, 3 e 10 funcionários decidido pelo responsável na mesma data.

- `tenant_membership` aceita `STAFF`; os repositórios existentes continuam exigindo `OWNER` (negado por padrão).
- **Convite:** o OWNER convida por e-mail; o convidado aceita depois de entrar com conta verificada. Convite expira em 7 dias e é de uso único.
- **Gestão:** lista de funcionários, revogação imediata.
- **Admin:** navegação do STAFF mostra só Caixa, Venda presencial e Crediário (recebimento).

**Aceite:** STAFF recebe 403 em todo endpoint de OWNER; revogado perde acesso na próxima requisição; convite não funciona para outra conta nem duas vezes.

**Testes:** matriz de papéis em todos os endpoints existentes; convite expirado, reutilizado e de outro tenant; A/B.

## SC-050 — Contas financeiras e movimentos

**Estado (2026-11-01):** autorizada e implementada ([validação](../quality/SC-050-VALIDATION.md)). O relatório e as vendas e despesas passam a usar os movimentos nas tarefas seguintes.

- **Migração aditiva:** `finance_account` (CASH, BANK, PAYMENT_PROVIDER) e `account_movement` só de acréscimo, com trigger como a V27.
- Conta Mercado Pago criada automaticamente quando a loja conecta o provedor; uma conta CASH padrão "Caixa da loja".
- Saldo por conta = soma dos movimentos; transferência entre contas com dois movimentos na mesma transação; ajuste de saldo inicial com motivo.
- **Admin (OWNER):** contas, saldos e extrato por conta.

**Aceite:** saldo bate com a soma do extrato; nenhum movimento se altera ou apaga; transferência nunca fica pela metade.

**Testes:** idempotência por `request_id`; transferência concorrente; trigger recusando UPDATE/DELETE; A/B.

## SC-051 — Contas a pagar (evolução das despesas)

**Estado (2026-11-02):** autorizada e implementada ([validação](../quality/SC-051-VALIDATION.md)). Na mesma data, o responsável decidiu que cada parcela conta no mês em que vence e que uma conta paga pode ser cancelada com estorno do valor à conta de saída.

- `expense` ganha vencimento, fornecedor, número do documento, grupo de parcelas e recorrência mensal.
- Situação derivada: aberta, vencida, paga, cancelada.
- **Baixa:** data, conta de saída, valor pago; juros/multa e desconto em lançamentos próprios; gera movimento na conta e preenche a liquidação no livro.
- Migração das despesas atuais conforme C4.
- **Admin (OWNER):** "Despesas" passa a "Contas a pagar", com filtros por situação e vencimento.

**Aceite:** duplicata em 3 parcelas gera 3 contas; pagar a recorrente cria a próxima; despesas antigas aparecem com a situação certa e o relatório do período não muda.

**Testes:** relatório da SC-021 igual antes e depois da migração; baixa repetida; cancelamento de conta paga recusado sem estorno; A/B.

## SC-052 — Sessão de caixa

**Estado (2026-11-03):** autorizada e implementada ([validação](../quality/SC-052-VALIDATION.md)). Na mesma data, o responsável decidiu três coisas:
- a diferença de caixa entra no resultado como linha própria;
- o funcionário pode registrar despesa paga pela gaveta;
- sangria e suprimento sem conta são retirada e aporte do dono.

- Abertura com fundo de troco; uma sessão aberta por conta CASH (índice único parcial).
- Movimentos: suprimento, sangria (motivo, destino opcional → transferência), despesa paga pelo caixa.
- **Fechamento com contagem cega**; diferença registrada como `CASH_DIFFERENCE`.
- Reabertura só pelo OWNER, com motivo.
- **Admin:** tela de caixa para STAFF e OWNER; histórico de sessões, diferenças e quem operou só para OWNER.

**Aceite:** STAFF nunca vê o valor esperado; fechar duas vezes falha; movimento em sessão fechada falha; a diferença aparece no histórico.

**Testes:** abertura e fechamento concorrentes; sangria maior que o saldo esperado (permitida, mas sinalizada); papéis; A/B.

## SC-053 — Venda presencial (lançamento manual)

- Tabela `counter_sale` e itens, no módulo `orders`; `finance_entry` ganha `counter_sale_id` (C7).
- **No livro de vendas, "Registrar venda presencial":** campo de busca **"Adicionar produto do estoque"** (nome, SKU ou variante, com o disponível à vista), quantidade, desconto em valor, forma de pagamento e conta de entrada. Só produtos do catálogo.
- Baixa de estoque na hora, no mesmo estoque da vitrine, com o bloqueio do inventário; recusa abaixo do disponível.
- Lançamentos `SALE`, `COGS` e, quando houver taxa informada, `FEE` (C8); dinheiro entra na sessão de caixa aberta (sem sessão aberta, venda em dinheiro é recusada).
- Cancelamento pelo OWNER com motivo e devolução ao estoque opcional.
- **Configuração (OWNER):** taxa por forma de pagamento e prazo de liberação do cartão.

**Aceite:** venda presencial e online disputam o mesmo estoque sem vender o que não existe; o relatório do período soma as duas; a margem marca taxa "informada pelo lojista".

**Testes:** venda concorrente com checkout da vitrine pelo último item; idempotência; cancelamento; STAFF registrando e OWNER cancelando; A/B.

## SC-054 — Importação de vendas presenciais por planilha

- Modelo CSV para baixar (data, código de venda opcional, SKU, quantidade, preço unitário, desconto, forma de pagamento). Uma linha por item.
- **Envio → prévia → confirmação:** a prévia lista as vendas válidas e os erros por linha (SKU inexistente, estoque insuficiente, data futura, valor ou forma inválidos); só a confirmação grava, numa transação.
- Cada venda importada segue C6: `counter_sale`, lançamentos e baixa de estoque. Competência na data informada; baixa no momento da importação.
- Idempotente por hash do arquivo e por linha. Limite de 500 linhas e 1 MB. Sem crediário. "Dinheiro" exige conta de destino; não entra em sessão de caixa.
- `.xlsx` direto só depois de aprovar a biblioteca.

**Aceite:** reenviar o mesmo arquivo não duplica nada; nenhuma linha com erro é gravada; o estoque cai exatamente o que foi importado.

**Testes:** arquivo vazio, grande demais, com BOM, separador `;` e `,`, decimal com vírgula, injeção de fórmula; estoque insuficiente no meio do arquivo; importação concorrente com venda online; A/B.

## SC-055 — Crediário e contas a receber

- Cliente de crediário (nome, telefone, observação, limite opcional).
- Venda no crediário gera 1 a 12 parcelas; recebimento parcial em qualquer conta, inclusive no caixa aberto.
- **Contas a receber** unificadas (C10): Mercado Pago, cartão presencial e crediário.
- **Admin:** ficha do cliente com saldo devedor e histórico; lista de vencidas.

**Aceite:** o saldo do cliente é a soma das parcelas menos os recebimentos; venda acima do limite é recusada; nada de dado pessoal em log ou CSV.

**Testes:** recebimento parcial e excedente (recusado); parcelas com arredondamento (a última absorve os centavos); acesso de outro tenant; A/B.

## SC-056 — Livro de vendas

- Relatório diário por canal e forma de pagamento: bruto, desconto, taxa, líquido, reembolso, quem registrou e origem (manual ou importação).
- Totais do período iguais aos do relatório financeiro.
- Exportação CSV sem dados pessoais.

**Aceite:** soma do livro de vendas = receita do relatório da SC-021 no mesmo período.

**Testes:** igualdade com o relatório; período com reembolso e cancelamento; CSV com caracteres especiais; A/B.

## SC-057 — Fluxo de caixa projetado e alertas

- Saldo das contas + a receber − a pagar, por dia, em 30, 60 e 90 dias; dias negativos marcados.
- Alertas no `advisor`: contas a pagar vencendo em 3 dias, vencidas, crediário vencido (agregado, sem nome), quebra de caixa acima de um limite, saldo projetado negativo.

**Aceite:** projeção reproduzível à mão com os dados de teste; alertas somem quando a causa some.

**Testes:** limites de data no fuso de São Paulo; loja sem dados; A/B.

## SC-058 — Revisão integrada

- Revisão de segurança multi-tenant e de papéis em todo o módulo, incluindo a importação.
- Conferência cruzada: livro × movimentos × estoque × relatório × livro de vendas.
- Acessibilidade das telas novas (teclado, leitor de tela, zoom) e smoke do admin.
- Atualizar ARCHITECTURE, DATA_MODEL e BUSINESS_RULES.

## Próximos passos fora desta sprint

- Fechamento de período (C13).
- Leitura direta de `.xlsx` (biblioteca a aprovar).
- Conciliação bancária por extrato.
- Ferramentas do assistente para contas a pagar e fluxo projetado.
