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
- **Fora:** PDV, emissão fiscal, boleto, conciliação bancária por extrato, cobrança automática, juros automáticos de crediário, comissão de vendedor e múltiplas lojas físicas por tenant.
