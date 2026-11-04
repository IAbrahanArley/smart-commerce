# ADR-0019 — Caixa, contas a pagar e a receber, venda presencial e crediário (Sprint 7)

Status: **adotado** em 2026-10-26 para C1–C13, aprovadas pelo responsável na mesma data, com o detalhamento da venda presencial (C6) e a importação por planilha (C14) pedidos por ele. Em C14, só o formato `.xlsx` ainda depende de aprovação. Por enquanto só o planejamento fica registrado: cada tarefa da Sprint 7 exige autorização própria. Plano incremental: [SPRINT_07.md](../../product/SPRINT_07.md).

## Contexto

- **Pedido do responsável (2026-10-26):** um módulo financeiro com contas a pagar, contas a receber, abertura e fechamento de caixa, sangria e livro de vendas. A maior dor é **controlar juntos o que é vendido online e o que é vendido na loja física**. Respostas às perguntas de escopo:
  - **sem PDV** (sem leitor de código de barras, cupom, TEF ou impressora);
  - **papel de funcionário** (operador de caixa) entra agora;
  - **contas a pagar** = contas em aberto: duplicatas de fornecedor, salários, despesas pendentes;
  - **venda fiado/crediário** entra no escopo;
  - **estoque compartilhado:** muitas lojas não separam estoque para o online. A venda física é lançada no livro de vendas, à mão (escolhendo o produto do estoque) ou por planilha, e cada produto lançado baixa o estoque, o mesmo que a vitrine usa.
- **Ampliação de escopo:** o [PRD](../../product/PRD.md) cobre só venda online e deixa "ERP completo" fora do MVP. Venda presencial, crediário e funcionário ampliam o produto; na adoção deste ADR o PRD ganha essas linhas.
- **O que já existe ([ADR-0015](0015-finance-dashboard.md)):**
  - livro `finance_entry` só de acréscimo, com competência e liquidação, chave idempotente por fato e trigger que recusa alterar ou apagar (V27);
  - lançamentos `SALE`, `REFUND`, `FEE`, `COGS`, `COGS_REVERSAL` exigem `order_id` de `sales_order`;
  - `expense` com competência, `paid_on` opcional e cancelamento com motivo (F6);
  - "a receber" e "caixa simplificado" (F3) dependem da SC-018, ainda pendente.
- **Identidade:** só existe `OWNER`, conferido no SQL (`m.role = 'OWNER'`) em todos os repositórios. Um papel novo é negado por padrão em tudo que já existe, o que é seguro.
- **`sales_order`** é feito para a vitrine: exige e-mail e telefone do comprador, reserva com expiração e token de acesso. Não serve bem para uma venda de balcão.

## Decisões propostas

| ID | Decisão | Recomendação | Alternativas | Bloqueia |
| --- | --- | --- | --- | --- |
| C1 | Papel de funcionário | **`STAFF`** em `tenant_membership`, por convite do OWNER ao e-mail de uma conta existente (ou que será criada pelo fluxo de cadastro atual). STAFF só opera caixa e registra venda presencial e recebimento de crediário. Não vê relatórios, margens, custos, contas a pagar nem o valor esperado do caixa. OWNER revoga o acesso a qualquer momento; a sessão do STAFF revogado deixa de autorizar na próxima requisição | Permissões configuráveis por funcionário (mais flexível, mais superfície de erro) | SC-049, SC-052, SC-053 |
| C2 | Contas financeiras | **Tabela `finance_account`** por loja, tipos `CASH` (gaveta), `BANK` e `PAYMENT_PROVIDER` (Mercado Pago). **Saldo = soma de `account_movement`**, nunca um campo editável. Movimento só de acréscimo, assinado, com chave idempotente; correção é um movimento de estorno com motivo. Transferência = dois movimentos na mesma transação | Saldo guardado e atualizado (risco de divergência) | SC-050 |
| C3 | Livro × movimentos | **`finance_entry` continua sendo o resultado (competência).** **`account_movement` passa a ser o dinheiro real** (onde, quando, quanto). O "caixa" do relatório passa a ler os movimentos; o `settled_at` do livro continua sendo preenchido uma vez, quando o fato é totalmente liquidado | Só o livro, com `settled_at` (não aceita baixa parcial nem saber em qual conta o dinheiro está) | SC-050, SC-051, SC-054 |
| C4 | Contas a pagar | **Evolução de `expense`, não tabela paralela.** A despesa ganha `due_date`, fornecedor (texto), número do documento e situação derivada: aberta, vencida, paga, cancelada. Parcelamento = N contas com o mesmo grupo. Recorrência mensal gera a próxima conta ao pagar a atual (sem rotina em segundo plano). **Baixa** = data, conta de saída e valor pago; juros/multa e desconto viram lançamentos próprios. Baixa só integral no MVP. A migração mantém as despesas atuais: com `paid_on` viram pagas (sem conta de saída, marcadas "conta não informada"), sem `paid_on` viram abertas com vencimento = competência | Tabela `payable` separada convivendo com `expense` (duas telas para a mesma coisa) | SC-051 |
| C5 | Sessão de caixa | **Uma sessão aberta por conta `CASH`**, garantida por índice único parcial. Abertura com fundo de troco. Movimentos: venda em dinheiro, recebimento de crediário em dinheiro, suprimento, sangria (motivo e destino; com destino = outra conta, vira transferência) e despesa paga pelo caixa. **Fechamento com contagem cega** (o operador informa o contado sem ver o esperado); a diferença vira movimento `CASH_DIFFERENCE` e não se edita. Reabertura só pelo OWNER, com motivo, gerando nova sessão ligada à anterior | Fechamento com o esperado à vista (induz o operador a "ajustar" a contagem) | SC-052 |
| C6 | Venda presencial | **Registro de venda de balcão sem PDV**, a partir do livro de vendas ("Registrar venda presencial"). Cada item entra por um campo de busca **"Adicionar produto do estoque"** (nome, SKU ou variante, mostrando o disponível); não existe item fora do catálogo. Tabela própria `counter_sale` no módulo `orders`: itens do catálogo (variante, quantidade, preço snapshot, custo snapshot), desconto opcional em valor, forma de pagamento (dinheiro, Pix, cartão crédito, cartão débito, crediário) e conta de entrada. Baixa o estoque na hora (movimento `SALE` com origem balcão), sem reserva. Gera no livro `SALE` e `COGS` na mesma transação. Cancelamento só pelo OWNER, com motivo, no mesmo dia ou com estorno explícito (`REFUND`, `COGS_REVERSAL`, devolução ao estoque opcional) | Reaproveitar `sales_order` com canal `STORE` (exigiria afrouxar várias restrições pensadas para a vitrine) | SC-053 |
| C7 | Livro aceita venda presencial | **`finance_entry` ganha `counter_sale_id`**; a regra "fato de venda tem origem" passa a ser "tem exatamente um entre `order_id` e `counter_sale_id`". Migração aditiva, sem tocar lançamentos existentes | Tabela de lançamentos separada para balcão (dois livros) | SC-053 |
| C8 | Taxa de cartão na maquininha | **Taxa por forma de pagamento informada pelo OWNER**, gravada como snapshot na venda e lançada como `FEE` com marca **"informada pelo lojista"**. Sem taxa informada: "taxa indisponível", como no F2. Liberação do cartão de crédito: data informada ou "a receber, data desconhecida" | Ignorar taxa de maquininha (margem superestimada) | SC-053 |
| C9 | Crediário (fiado) | **Cliente de crediário mínimo** por loja: nome, telefone e observação; sem CPF no MVP. Venda no crediário gera conta a receber em 1 a 12 parcelas. **Baixa parcial permitida** (é como o fiado funciona), cada recebimento é um movimento na conta escolhida; a parcela liquida quando o saldo chega a zero. Limite de crédito opcional por cliente, conferido na venda. Dados pessoais: só OWNER e STAFF da loja veem; anonimização quando não houver saldo em aberto há 5 anos (prazo a confirmar com orientação jurídica) | Crediário sem cadastro, só texto livre na venda (não dá para somar o que cada cliente deve) | SC-055 |
| C10 | Contas a receber | **Uma visão única** de: pagamentos do Mercado Pago não liberados (SC-018), cartão de crédito presencial não liberado (C8) e parcelas de crediário. Situação: a vencer, vencida, recebida parcialmente, recebida | Telas separadas por origem | SC-055, SC-057 |
| C11 | Livro de vendas | **Relatório por dia** com canal (online/presencial), forma de pagamento, bruto, desconto, taxa, líquido, reembolso e quem registrou. É também a porta de entrada da venda presencial (C6) e da importação (C14). Lê só o livro e `counter_sale`/`sales_order` para o rótulo. Exportação CSV sem dados pessoais de compradores. Só OWNER vê os totais; STAFF só registra | Livro editável à parte (perde a trilha) | SC-056 |
| C12 | Fluxo de caixa projetado | **Saldo atual das contas + a receber − a pagar por vencimento**, nos próximos 30, 60 e 90 dias, com marcação de dias negativos. Sem previsão de vendas futuras | Projeção com vendas estimadas (inventa números) | SC-057 |
| C13 | Fechamento de período | **Fora desta sprint.** Registrado como próximo passo: travar meses fechados para competência e lançar correções no mês corrente com referência | Incluir agora | — |
| C14 | Importação de vendas presenciais por planilha | **Só OWNER.** Modelo para baixar com as colunas data da venda, SKU, quantidade, preço unitário, desconto e forma de pagamento; uma linha por item, agrupadas numa venda por um código de venda opcional. **Fluxo em duas etapas:** o envio valida tudo e mostra uma prévia (linhas válidas, erros por linha: SKU inexistente, estoque insuficiente, data futura, valor inválido); só a confirmação grava. As linhas válidas confirmadas entram numa transação: cada venda vira `counter_sale` com os mesmos lançamentos e baixa de estoque de C6. A data da venda vale para a competência e a baixa de estoque é feita no momento da importação. Idempotente por hash do arquivo e por linha: reenviar o mesmo arquivo não duplica. Limite de 500 linhas e 1 MB. Vendas importadas não entram em sessão de caixa; forma "dinheiro" exige escolher a conta de destino. Crediário não é aceito na importação (exige cliente). **Formato:** CSV (o Excel salva nesse formato), sem dependência nova. Ler `.xlsx` direto exige aprovar uma biblioteca (ex.: Apache POI) | Ler `.xlsx` desde já (dependência nova e superfície de ataque maior: arquivos compactados, fórmulas) | SC-054 |

Regras comuns a todas as decisões: tenant derivado da sessão e conferido no SQL com o papel; `NUMERIC`/`BigDecimal` em BRL, 2 casas, sem float (F10); datas de calendário em `America/Sao_Paulo`; toda escrita com `request_id` idempotente; nada se apaga, correção é estorno com motivo, ator e instante.

## Implementação da SC-049 — funcionário (2026-11-01)

- **Limite por plano (decisão do responsável em 2026-11-01):** Essencial 1, Profissional 3 (também no teste grátis) e Escala 10 funcionários. A contagem é por dono, uma vez por pessoa em todas as suas empresas, e inclui os convites em aberto ainda válidos. Convidar alguém que já conta (funcionário ou convidado em outra loja do mesmo dono) não ocupa vaga nova. Depois de uma descida de plano, nada é removido; só novos convites ficam bloqueados. Os cards de planos (landing e "Plano e cobrança") só vão anunciar esse limite quando o caixa existir (SC-052), para não prometer o que o funcionário ainda não consegue fazer.
- **Banco (V40):** `STAFF` em `tenant_membership`, um só `OWNER` por empresa e a tabela `staff_invitation` ([DATA_MODEL.md](../DATA_MODEL.md)).
- **Convite:** o OWNER convida por e-mail (Brevo). O token tem 32 bytes aleatórios e só o hash fica no banco. Ele vai no fragmento do link `/convite#token=…`, vale 7 dias e só pode ser usado uma vez. Um convite novo para o mesmo e-mail cancela o anterior.
- **Aceite:** exige sessão de uma conta ativa e verificada cujo e-mail (chave de comparação da identidade) seja o convidado; o clique é explícito. A conta pode ser criada depois do convite pelo cadastro normal.
- **Revogação:** apaga o vínculo, e a próxima requisição do funcionário já é negada. O convite aceito guarda quem revogou e quando.
- **Acesso:** as consultas de acesso existentes já exigiam `role='OWNER'`, então nenhuma foi alterada. `GET /api/tenants` e `GET /api/tenants/{id}` passam a mostrar também as empresas em que a conta é `STAFF`, com o papel; isso não concede nenhuma capacidade de OWNER. As funções do STAFF chegam com a SC-052, a SC-053 e a SC-055.
- **API:** `GET /api/tenants/{id}/staff`, `POST /api/tenants/{id}/staff/invitations`, `DELETE /api/tenants/{id}/staff/invitations/{invitationId}`, `DELETE /api/tenants/{id}/staff/{userId}` (só o OWNER, os demais recebem 404) e `POST /api/staff-invitations/acceptance`.
- **Admin:**
  - o OWNER tem "Funcionários" no menu da loja;
  - a página `/convite` aceita o convite: o token fica só na memória e sai da barra de endereço;
  - o STAFF vê a loja com o selo "Funcionário", menu reduzido e a lista do que chega para a equipe;
  - o aviso de cobrança não aparece para quem não tem loja própria.
- **Revisão de segurança independente (2026-11-01):** nenhum achado alto, e nenhum caminho dá a um STAFF acesso de OWNER. Correções aplicadas:
  - **Aceite:** confere de novo o limite do plano e a situação do dono, contando só quem já é funcionário. Um convite enviado no teste grátis não entra depois de uma descida para o Essencial se a vaga acabou.
  - **Limite de envio:** 30 convites por dono e 5 por destinatário a cada 24 horas, numa transação própria antes do envio.
  - **Assunto do e-mail:** não leva mais o nome da loja (que o dono escolhe); no corpo ele aparece escapado.
  - **Convites simultâneos** ao mesmo e-mail dão 409 em vez de 500.
- **Limitações registradas:**
  - o envio do e-mail acontece dentro da transação do convite; uma falha não deixa convite órfão, mas convites do mesmo dono ficam em fila enquanto o Brevo responde;
  - o e-mail dos convites fica guardado e as FKs para `identity_user` impedem apagar a conta: retenção pendente com a validação jurídica do D2;
  - a parte local do e-mail diferencia maiúsculas, como em toda a identidade;
  - a prévia de descida de plano ainda não avisa quando os funcionários passam do novo limite (nada é removido).
- **Testes:** `StaffIT`, com Postgres real. Inclui uma matriz que percorre todas as rotas mapeadas sob `/api/tenants/{tenantId}` e falha se alguma responder com sucesso a um STAFF; ela protege também as rotas futuras.

## Implementação da SC-050 — contas e movimentos (2026-11-01)

- **Banco (V41):** `finance_account` e `account_movement` ([DATA_MODEL.md](../DATA_MODEL.md)). O trigger torna os movimentos só de acréscimo, como a V27 fez com o livro.
- **Contas padrão:** "Caixa da loja" e, depois que o Mercado Pago é conectado, "Mercado Pago" aparecem no primeiro uso das contas, sem mexer no módulo de pagamentos. O OWNER cria gavetas e contas de banco, até 20 por loja.
- **Movimentos:**
  - ajuste assinado com motivo, inclusive o saldo inicial;
  - transferência entre duas contas da loja, com os dois movimentos na mesma transação.

  O `requestId` torna uma repetição o mesmo lançamento; o mesmo pedido com outro conteúdo dá 409. As escritas de uma loja passam pela trava da loja (`FOR UPDATE`), então duas transferências iguais ao mesmo tempo gravam uma só vez.
- **Extrato:** período de até 366 dias (padrão: últimos 90), com saldo antes e depois e o saldo logo após cada movimento, calculado sobre todo o histórico. Uma transferência mostra a conta do outro lado. Até 500 movimentos por consulta, com aviso quando há mais.
- **API (só o OWNER, os demais recebem 404):**
  - `GET` e `POST /api/tenants/{id}/finance/accounts`;
  - `GET /api/tenants/{id}/finance/accounts/{accountId}/statement?from=&to=`;
  - `POST /api/tenants/{id}/finance/accounts/{accountId}/adjustments`;
  - `POST /api/tenants/{id}/finance/transfers`.

  Valores chegam como texto decimal, nunca como número.
- **Admin:** "Contas" no menu da loja, com cards de saldo (negativo em destaque), "Transferir entre contas" e "Nova conta". O extrato de cada conta tem período, tabela e "Registrar ajuste".
- **Revisão independente (2026-11-01):** nenhum achado crítico ou alto, e o isolamento entre lojas foi confirmado, inclusive pela FK composta. Correções aplicadas:
  - leitura de valores em reais no admin;
  - saldos grandes;
  - limites de data do extrato;
  - pedido novo depois de um conflito.
- **Conta do Mercado Pago só para leitura (decisão do responsável em 2026-11-01):** ela não aceita ajuste nem transferência manual, de entrada ou de saída (409 `PROVIDER_READ_ONLY`). O dinheiro dela vai se mover só com os fatos do próprio Mercado Pago e com a conciliação (SC-018). No admin, ela não aparece nos formulários de transferência e o extrato dela não tem "Registrar ajuste".
- **Fora desta tarefa:** o relatório financeiro ainda não lê os movimentos (C3), e as vendas e despesas ainda não geram movimentos. Isso chega com as contas a pagar (SC-051), a venda presencial (SC-053) e a SC-018 (liberação do Mercado Pago).
- **Testes:** `AccountIT`, com Postgres real: saldo igual à soma dos movimentos, idempotência e conflito, extrato que fecha, trigger recusando `UPDATE` e `DELETE`, transferências concorrentes inteiras, validação de dinheiro, datas e limite de contas, e nenhum acesso de outro dono nem de funcionário. A matriz de papéis do `StaffIT` cobre as rotas novas automaticamente.

## Implementação da SC-051 — contas a pagar (2026-11-02)

- **Decisões do responsável (2026-11-02):**
  - cada parcela conta no mês em que vence;
  - uma conta paga pode ser cancelada, com estorno.
- **Banco (V42):** colunas novas em `expense` e dois tipos de movimento ([DATA_MODEL.md](../DATA_MODEL.md)). As despesas existentes vencem na competência; as pagas ficam com "conta não informada". O livro não muda.
- **Cadastro:**
  - vencimento e competência valem um pelo outro quando só um é informado;
  - fornecedor e número do documento são opcionais;
  - **parcelas:** de 2 a 24, um mês entre cada uma a partir do primeiro vencimento; o total é dividido em centavos iguais e a última parcela absorve a diferença;
  - **recorrência mensal:** não se combina com parcelas;
  - criar já paga exige a data e a conta de saída.

  Cada parcela é uma despesa com seu lançamento `EXPENSE` na mesma transação. Repetir o pedido devolve a primeira parcela, sem criar nada.
- **Baixa (só integral):**
  - informa data (até hoje), conta de saída, juros ou multa e desconto;
  - o lançamento da despesa recebe a liquidação no dia do pagamento;
  - juros ou multa (`EXPENSE`) e desconto (`EXPENSE_REVERSAL`) viram lançamentos próprios, com competência e liquidação nesse dia;
  - o total sai da conta como `EXPENSE_PAYMENT`.

  A conta do Mercado Pago é recusada (409 `PROVIDER_READ_ONLY`). Repetir o mesmo pagamento não muda nada; outro pagamento da mesma conta dá 409 `ALREADY_PAID`. Pagar uma conta mensal cria a do mês seguinte uma única vez (chave derivada da conta paga).
- **Cancelamento:** sem conta de saída, igual à SC-020. Paga a partir de uma conta:
  - cada lançamento ganha seu estorno, com a competência original e liquidação no dia do cancelamento;
  - o valor pago volta para a conta (`EXPENSE_REFUND`) nesse mesmo dia.

  Cancelar uma conta mensal paga cancela também a do mês seguinte, se ainda estiver em aberto: assim a correção, que gera a própria sucessora ao ser paga, não deixa duas contas no mesmo mês. Uma conta cancelada não pode ser paga (409 `NOT_PAYABLE`).
- **Situação derivada:** aberta, vencida (a partir do dia seguinte ao vencimento), paga ou cancelada.
- **API (só o OWNER, os demais recebem 404):**
  - `GET /api/tenants/{id}/expenses?month=&situation=` lista por mês de vencimento, com "a pagar" e "pago" do mês e o total vencido de todos os meses; `situation=OVERDUE` mostra as vencidas de qualquer mês;
  - `POST /expenses` recebe os campos novos;
  - `POST /expenses/{id}/pay` registra a baixa.
- **Admin:** "Despesas" passou a "Contas a pagar", com:
  - navegação por mês de vencimento e abas por situação na URL (`?situacao=vencidas`);
  - prévia das parcelas e do total pago;
  - formulário de pagamento sem a conta do Mercado Pago;
  - etiqueta vermelha para as vencidas;
  - aviso, ao cancelar uma conta paga, de que o valor volta para a conta.
- **Limitação:** a próxima conta mensal vence um mês depois da anterior; depois de um dia 31 que caiu em mês curto, as seguintes ficam no dia 30 ou 28.
- **Fora desta tarefa:** o relatório financeiro continua lendo a liquidação do livro, que agora coincide com os pagamentos registrados nas contas; ler direto dos movimentos (C3) fica para quando as vendas também gerarem movimentos.

## Implementação da SC-052 — sessão de caixa (2026-11-03)

- **Decisões do responsável (2026-11-03):**
  - a diferença de caixa entra no resultado como linha própria, "Diferenças de caixa", fora da margem de contribuição;
  - o funcionário pode registrar despesa paga com o dinheiro da gaveta;
  - sangria sem conta de destino e suprimento sem conta de origem são retirada e aporte do dono, sem entrar como despesa nem receita.
- **Banco (V43):** `cash_session`, três tipos de movimento novos e o tipo `CASH_DIFFERENCE` no livro ([DATA_MODEL.md](../DATA_MODEL.md)). Uma sessão aberta por gaveta, nenhum movimento em sessão fechada e nenhuma mudança na sessão fechada: tudo isso é garantido pelo banco, não só pelo serviço.
- **Quem faz o quê:**
  - o dono e os funcionários da loja abrem, movimentam e fecham;
  - só o dono reabre, vê o histórico e vê o valor esperado.

  A conferência de papel trava a loja; qualquer outra pessoa recebe 404. É a primeira funcionalidade em que o funcionário recebe respostas de sucesso. A matriz do `StaffIT` libera só `GET /cash`, e as demais rotas do caixa ficam cobertas pelo `CashIT`.
- **Valor esperado:** é o saldo da gaveta no fechamento, porque o saldo já é o dinheiro que está nela.
  - **Abertura:** o fundo de troco contado é comparado com o saldo. Numa gaveta que nunca teve movimento, vira o saldo inicial (`ADJUSTMENT`); depois disso, uma diferença vira `CASH_DIFFERENCE`.
  - **Fechamento às cegas:** a tela não mostra o esperado. A diferença (contado − esperado) vira movimento e linha do livro, com competência e liquidação no dia.
- **Movimentos da sessão:**
  - **sangria e suprimento:** com outra conta da loja, viram transferência (a perna da gaveta leva a sessão); sem conta, viram retirada ou aporte do dono;
  - **despesa paga pelo caixa:** vira conta a pagar já paga pela gaveta, pelo mesmo caminho da SC-051. Aluguel e pessoal entram como fixas; as demais categorias, como variáveis.
- **Idempotência:** o `requestId` torna repetições a mesma abertura, o mesmo movimento ou o mesmo fechamento. Outro fechamento dá 409 `SESSION_CLOSED`, e outra abertura com o caixa já aberto dá 409 `SESSION_OPEN`.
- **API:**
  - `GET /api/tenants/{id}/cash` (gavetas, sessão aberta e contas, só com o nome);
  - `GET /cash/sessions` (histórico, só o dono);
  - `GET /cash/sessions/{sessionId}`;
  - `POST /cash/sessions`;
  - `POST /cash/sessions/{sessionId}/movements`;
  - `POST /cash/sessions/{sessionId}/close`;
  - `POST /cash/sessions/{sessionId}/reopen` (só o dono).
- **Admin:** "Caixa" no menu do dono e do funcionário, e a página inicial do funcionário leva ao caixa. A tela mostra:
  - abertura com fundo de troco, sangria, suprimento e despesa;
  - fechamento às cegas;
  - para o dono, o histórico com esperado, contado e diferença (falta em vermelho) e o botão "Reabrir".

  O relatório financeiro ganhou a linha "Diferenças de caixa". Os cards dos planos agora anunciam o limite de funcionários (1, 3 e 10), como previsto na SC-049.
- **Correção de passagem:** uma categoria, tipo ou recorrência ausente no corpo da requisição gerava `NullPointerException` (respondida como 403) em vez de 400. Isso valia também para as despesas desde a SC-020.

## Implementação da SC-053 — venda presencial (2026-11-03)

- **Decisões do responsável (2026-11-03):**
  - o desconto do funcionário fica limitado a um percentual definido pelo dono, e o dono não tem limite;
  - cartão de débito e de crédito fica a receber, com a data prevista pelo prazo configurado e a taxa informada pelo lojista;
  - o Pix entra na hora numa conta padrão escolhida pelo dono.
- **Banco (V44):** `counter_sale`, os itens, as configurações e os tipos novos no estoque, no dinheiro e no livro ([DATA_MODEL.md](../DATA_MODEL.md)).
- **Registrar a venda (dono ou funcionário):**
  - "Adicionar produto do estoque" busca pelo nome ou pelo SKU (2 caracteres no mínimo, até 20 resultados) e mostra o disponível de agora, sem custo;
  - preço e custo vêm do catálogo como snapshot;
  - a baixa usa a mesma trava do estoque da vitrine, em ordem de variante, e respeita as reservas em vigor da vitrine. Se faltar estoque em algum item, nada é gravado (409 `INSUFFICIENT_STOCK`).
- **Lançamentos e dinheiro:**
  - o livro recebe `SALE` (total) e `COGS` (custo incompleto quando falta custo) e, no cartão com taxa configurada, `FEE`;
  - dinheiro e Pix liquidam na hora; cartão fica a receber;
  - o dinheiro entra na gaveta de uma sessão de caixa aberta; o Pix entra na conta padrão. Sem essa conta, a venda no Pix é recusada (409 `PIX_NOT_CONFIGURED`).
- **Cancelamento (só o dono, com motivo):**
  - devolução ao estoque opcional (`COUNTER_RETURN`);
  - `REFUND` e, se o produto voltou ao estoque, estorno do CMV;
  - dinheiro e Pix saem da conta de origem; no dinheiro, ligado à sessão aberta da gaveta, se houver;
  - a taxa do cartão não é estornada.
- **Relatório:**
  - a venda na loja entra na receita;
  - dinheiro e Pix não contam como "taxa indisponível"; só o cartão sem taxa configurada conta;
  - `informedFees` mostra a parte das taxas informada pelo lojista, e o texto da taxa faltante deixou de citar só o Mercado Pago.
- **API:**
  - `GET /api/tenants/{id}/counter-sales` (papel, limite, Pix, sessões abertas e as 50 últimas vendas);
  - `GET /counter-sales/products?q=`;
  - `POST /counter-sales`;
  - `POST /counter-sales/{saleId}/cancel` (só o dono);
  - `GET` e `PUT /counter-sales/settings` (só o dono).

  A matriz do `StaffIT` libera só `GET /counter-sales` para o funcionário.
- **Admin:** "Vendas na loja" no menu do dono e do funcionário, e a página inicial do funcionário leva até lá. A tela tem:
  - busca no estoque, itens, desconto com o limite e forma de pagamento (caixa aberto, Pix ou cartão), com prévia do total;
  - as 50 últimas vendas, com "Cancelar" só para o dono;
  - as configurações do dono.

  O histórico de estoque e o extrato das contas reconhecem os tipos novos.

## Implementação da SC-054 — importação por planilha (2026-11-03)

- **Banco (V45):** `counter_sale_import` e, em `counter_sale`, o vínculo com a importação, a impressão digital da venda e o dia da venda ([DATA_MODEL.md](../DATA_MODEL.md)).
- **Arquivo:** só CSV, até 1 MB e 500 linhas de itens, lido em memória e nunca guardado.
  - **Codificação:** UTF-8, com ou sem BOM; se os bytes não forem UTF-8, Windows-1252 (o "CSV" do Excel em português).
  - **Separador:** `;` ou `,`, conforme o cabeçalho.
  - **Colunas:** pelo nome, em qualquer ordem e com variações ("Forma de pagamento", "Código da venda", "Preço").
  - **Valores e datas:** aceita "R$ 1.234,50", "1234,50" e "1234.50", e datas em dd/mm/aaaa ou aaaa-mm-dd, de 2000 até hoje.
  - **Recusas por linha:** célula começando com `=`, `+`, `-`, `@`, tabulação ou CR; crediário; linha com mais células preenchidas que o cabeçalho (uma vírgula decimal que partiu o preço). Linhas só de separadores são ignoradas.
- **Venda:** linhas com a mesma data e o mesmo código formam uma venda; sem código, cada linha é uma venda.
  - Se uma linha da venda tiver erro, a venda inteira espera a planilha corrigida. Isso evita gravar a venda sem um item e depois gravá-la de novo, inteira.
  - A impressão digital é calculada a partir de data, código, forma e itens, mais a ocorrência do mesmo conteúdo no arquivo. Reenviar o arquivo, ou uma versão corrigida, não repete as vendas já importadas.
  - Corrigir uma venda que já foi importada exige cancelá-la e registrá-la de novo.
- **Prévia e confirmação:** a prévia (`POST /counter-sales/imports/preview`) não grava nada e mostra cada venda como pronta, já importada ou com erro.
  - A confirmação (`POST /counter-sales/imports`) trava a loja, analisa o arquivo de novo e grava as vendas prontas numa transação, pelo mesmo caminho da SC-053.
  - O que mudou desde a prévia (por exemplo, o estoque) aparece como erro na hora. Uma corrida no instante da baixa desfaz tudo (409 `STALE_IMPORT`).
  - Só o dono importa.
- **Datas e dinheiro:**
  - a competência e a liquidação ficam no dia da venda, e o estoque sai na importação;
  - o preço vem da planilha e o custo vem do catálogo atual;
  - o dinheiro entra na gaveta ou na conta bancária escolhida na confirmação, fora de sessão de caixa (sem conta: 409 `CASH_ACCOUNT_REQUIRED`);
  - o Pix entra na conta padrão;
  - o cartão fica a receber a partir do dia da venda, mais o prazo configurado.
- **Filtro de identidade:** as duas rotas entram na lista de uploads do `IdentityRequestFilter`, que continua exigindo `X-CSRF-TOKEN`; antes, elas caíam no limite de 8 KB.
- **Admin:** "Importar vendas de uma planilha" na página "Vendas na loja", só para o dono. Tem:
  - modelo para baixar;
  - prévia com resumo, erros por linha e situação de cada venda;
  - escolha da conta do dinheiro;
  - confirmação.

  Depois de importar, a prévia do mesmo arquivo é refeita e mostra o que entrou e o que ficou de fora. Na lista, a venda importada mostra a data da venda.
- **Fora desta tarefa:** `.xlsx` direto, que depende de aprovar uma biblioteca.

## Consequências e riscos

- **Escopo:** é a maior ampliação desde o MVP (funcionário, venda presencial, crediário). Fatiado em tarefas independentes para poder parar em qualquer ponto com algo útil.
- **Papel novo (C1):** todo endpoint novo confere o papel explicitamente; testes de negação para STAFF em cada endpoint OWNER existente. Convite não pode conceder acesso por e-mail não verificado.
- **Estoque (C6):** a venda de balcão disputa o mesmo estoque da vitrine; a baixa usa o mesmo bloqueio do inventário e recusa vender abaixo do disponível (reservas da vitrine respeitadas).
- **Importação (C14):** a planilha é dado não confiável. Precisa de limite de tamanho, recusa de células começando com `=`, `+`, `-` ou `@` no CSV exportado (injeção de fórmula) e nenhum conteúdo da planilha em log. Uma importação grande trava o estoque de muitos produtos de uma vez; a transação tem que ser curta, com os produtos bloqueados sempre na mesma ordem.
- **Concorrência (C5):** abrir duas sessões, fechar duas vezes ou movimentar sessão fechada precisam falhar no banco, não só no serviço.
- **Migração de `expense` (C4):** aditiva (colunas novas, valores derivados), sem alterar lançamentos do livro. Revisar com `supabase-postgres-best-practices` antes de aplicar.
- **LGPD (C9):** primeiro cadastro de cliente persistente da plataforma; minimização, acesso por papel, nenhum dado pessoal em log, CSV ou ferramenta do assistente.
- **Taxas informadas (C8):** a margem fica "estimada pelo lojista" nessas vendas; o relatório precisa distinguir taxa do provedor de taxa informada.
- **Assistente (Sprint 6):** não lê os dados de crediário. Ferramentas novas (contas a pagar, fluxo projetado) só depois da revisão integrada, com agregados.
- **Paginação (2026-11-04):** vendas na loja, histórico do caixa, contas a pagar e extrato paginam no banco por cursor (50 por página), sem os cortes fixos anteriores de 50, 60 e 500 linhas; os totais continuam cobrindo todos os lançamentos. Detalhes em [ADMIN-PAGINATION.md](../../quality/ADMIN-PAGINATION.md).
- **Fora:** PDV, emissão fiscal, boleto, conciliação bancária por extrato, cobrança automática, juros automáticos de crediário, comissão de vendedor e múltiplas lojas físicas por tenant.
