# ADR-0017 — Assinatura da plataforma com Stripe Billing (cartão)

Status: **adotado** em 2026-10-27 para B1–B12, conforme as recomendações abaixo. O responsável também aprovou a dependência `com.stripe:stripe-java` (B11). Proposto em 2026-10-26. Cada tarefa (SC-031 a SC-033) segue com autorização própria, e conta, chaves e produtos da Stripe são criados pelo responsável. As chaves da Stripe são criadas pelo responsável e cadastradas só nos painéis de ambiente, nunca no chat ou no Git.

## Contexto

- **[PRD](../../product/PRD.md):** a assinatura SaaS é paga pelo lojista.
- **[PRICING_PROPOSAL.md](../../product/PRICING_PROPOSAL.md), decisões de 2026-10-26:**
  - planos Essencial R$ 59, Profissional R$ 129 e Escala R$ 299;
  - ciclos mensal, semestral (−10%) e anual (−20%);
  - teste grátis de 14 dias;
  - limites de lojas, produtos, fotos e cota de IA por plano;
  - **cobrança só por cartão, pela Stripe.** O Pix Automático pela Asaas fica para quando o CNPJ completar 6 meses.
- **Separação de dinheiro:**
  - a Stripe recebe **só a mensalidade da plataforma**;
  - as vendas das lojas continuam na conta Mercado Pago de cada lojista ([ADR-0003](0003-payment-ownership.md), [ADR-0013](0013-mercado-pago-checkout-pro.md));
  - nenhum dinheiro de lojista passa pela Stripe.
- **Isolamento ([ADR-0002](0002-tenant-isolation.md)):** a assinatura pertence à **conta do proprietário** (`identity_user`), não a uma loja. Os limites valem para todas as empresas que ele possui ([ADR-0006](0006-tenant-store-onboarding.md): uma loja por empresa).
- **Regras de pagamento herdadas:**
  - o estado nunca vem do retorno do navegador;
  - o webhook é autenticado, idempotente e confirmado por consulta ao provedor;
  - nenhum agente executa cobrança, desconto ou reembolso sem aprovação humana.

**Documentação oficial da Stripe, consultada em 2026-10-26 (reconfirmar antes de cada tarefa):**
- **Ciclo de vida das assinaturas:**
  - estados `trialing`, `active`, `incomplete`, `incomplete_expired`, `past_due`, `unpaid`, `canceled` e `paused`;
  - o acesso é liberado por `invoice.paid` ou pelo status `active`;
  - falhas chegam por `invoice.payment_failed`, com novas tentativas automáticas (Smart Retries).
- **Webhooks:**
  - assinatura HMAC-SHA256 no cabeçalho `Stripe-Signature`, com carimbo de tempo e tolerância padrão de 5 minutos;
  - exige o **corpo bruto** da requisição;
  - repetição por até 3 dias, **sem garantia de ordem**, com eventos duplicados possíveis (deduplicar pelo id do evento);
  - a resposta deve ser 2xx rápida.
- **Portal do cliente:**
  - trocar cartão, mudar de plano (até 10 produtos), cancelar no fim do período e ver faturas;
  - em português do Brasil;
  - sessões efêmeras, sem iframe.
- **Testes grátis:**
  - o Trial Offer exige a versão de API mais nova e o modo de cobrança flexível;
  - o portal não altera assinaturas com Trial Offer;
  - o teste sem cartão tem comportamento configurável no fim (cancelar, pausar ou cobrar).
- **Conta brasileira:**
  - CNPJ ativo e representante com CPF residente;
  - Pix só avulso; **Pix Automático indisponível**.
- **SDK oficial Java:** `com.stripe:stripe-java` (linha 33.x, compatível com Java 21).

## Decisões (aprovadas em 2026-10-27)

| ID | Decisão | Recomendação | Alternativas | Bloqueia |
| --- | --- | --- | --- | --- |
| B1 | Dono da assinatura | **Uma assinatura por conta de proprietário** (`identity_user`). Os limites somam todas as empresas e lojas dele. Colaboradores não pagam, porque ainda não existem | Assinatura por loja (cobraria várias vezes o mesmo lojista e complica o plano Escala) | todas |
| B2 | Teste grátis de 14 dias | **Controlado pelo Smart Commerce, sem cartão e sem objeto na Stripe:** `trial_ends_at` = cadastro + 14 dias, com o Profissional liberado. A Stripe só entra quando o lojista assina. Assim evitamos o Trial Offer, que ainda pede API nova e limita o portal, e não criamos clientes na Stripe para quem não converte | Trial da Stripe sem cartão (`missing_payment_method`) ou Trial Offer |  B9, telas |
| B3 | Coleta do cartão | **Stripe Checkout hospedado, em modo assinatura.** O cartão é digitado na página da Stripe, então o Smart Commerce **nunca vê nem guarda dados de cartão** (escopo PCI mínimo, como no Mercado Pago) | Elements embutido (mais trabalho e mais escopo) | B4 |
| B4 | Catálogo na Stripe | **3 produtos × 3 preços recorrentes** em BRL (mensal; a cada 6 meses; anual), identificados por `lookup_key` (`essencial_mensal`, `profissional_anual`…). O backend tem a tabela de planos, limites e chaves, e um teste garante que os valores batem com `admin-web/src/lib/plans.ts` | IDs de preço em variáveis de ambiente (mais frágil) | todas |
| B5 | Fonte da verdade | **Tabela própria `billing_subscription`**, atualizada **só pelo webhook**, com o objeto conferido por consulta à API (`GET /v1/subscriptions/{id}`) antes de mudar o estado. O retorno do Checkout (`success_url`) só mostra "Confirmando sua assinatura…" | Confiar no redirecionamento (proibido pelas regras de pagamento) | todas |
| B6 | Webhook | Rota pública `POST /api/public/webhooks/stripe`, com o seguinte tratamento:<ul><li>verificação de `Stripe-Signature` pelo SDK sobre o corpo bruto, com tolerância de 5 minutos;</li><li>tabela `billing_event` com o id do evento único (idempotência);</li><li>resposta 2xx depois de gravar, com processamento curto na mesma transação, porque o volume é baixo;</li><li>sem CSRF;</li><li>limite de corpo de **64 KB** só nessa rota (o filtro atual corta em 8 KB);</li><li>eventos assinados: `checkout.session.completed`, `customer.subscription.created`, `customer.subscription.updated`, `customer.subscription.deleted`, `invoice.paid` e `invoice.payment_failed`.</li></ul>A ordem não é garantida, por isso cada evento relê a assinatura na API | Processar só pelo conteúdo do evento (frágil diante de eventos fora de ordem) | B5 |
| B7 | Estados no Smart Commerce | Os estados são:<ul><li>`TRIAL`: teste ativo;</li><li>`TRIAL_EXPIRED`: teste vencido sem assinar;</li><li>`ACTIVE`: Stripe `active`;</li><li>`PAST_DUE`: falha de cobrança, com **carência de 7 dias** desde a primeira falha;</li><li>`SUSPENDED`: carência vencida ou Stripe `unpaid`;</li><li>`CANCELED`: fim do período depois do cancelamento.</li></ul>Em `TRIAL_EXPIRED`, `SUSPENDED` e `CANCELED`, **a vitrine sai do ar** (página "loja indisponível"), **o painel fica só leitura** (escritas bloqueadas, exceto plano e cobrança) e **nada é apagado** | Bloquear tudo ao primeiro atraso (agressivo) ou nunca bloquear (sem receita) | telas, limites |
| B8 | Troca de plano, ciclo e cartão; cancelamento | **Portal do cliente da Stripe**, configurado com os 3 planos e 3 ciclos e valor proporcional (proration):<ul><li>a subida vale na hora;</li><li>a descida vale no fim do período;</li><li>o cancelamento vale no fim do período.</li></ul>Se o uso passar do limite depois de uma descida, **nada é apagado**: só a criação de novas lojas, produtos ou fotos fica bloqueada até voltar ao limite | Telas próprias de troca (muito mais código) | B4 |
| B9 | Aplicação dos limites | **No backend**, nos serviços de criação:<ul><li>empresas por conta (1, 2 e 5);</li><li>produtos por loja (150, 2.000 e sem limite, com teto técnico);</li><li>fotos por produto (5, 10 e 10);</li><li>cota mensal de IA por conta (0, 60 e 200), que substitui a cota por loja do ADR-0016.</li></ul>A resposta é 402 ou 409 com um código legível (`PLAN_LIMIT`), e a tela mostra o limite e um botão "Mudar de plano" | Limites só na interface (contornáveis) | B7 |
| B10 | Arrependimento e reembolso | **CDC, art. 49:** o lojista pode desistir em até 7 dias da contratação ou da renovação anual ou semestral, com reembolso integral. O reembolso é feito **por uma pessoa no painel da Stripe**, nunca automático nem por agente. Depois disso, o cancelamento vale no fim do período, sem reembolso proporcional. O texto entra nos Termos de uso | Reembolso proporcional automático | Termos |
| B11 | Dependência e segredos | **Adotar `com.stripe:stripe-java`** (versão fixa, aprovação de dependência necessária), pela verificação de assinatura e pelos tipos. Segredos:<ul><li>`STRIPE_SECRET_KEY`, uma **chave restrita** com permissão só para Checkout, Customers, Subscriptions, Billing Portal e leitura de Events;</li><li>`STRIPE_WEBHOOK_SECRET` (`whsec_`).</li></ul>No ambiente de teste hospedado, chaves de **modo de teste**; em produção, as chaves live, com aprovação separada | Chamar a API REST à mão (mais código e verificação de assinatura própria) | B6 |
| B12 | Ambientes e travas | **Ambientes:**<ul><li>DEV e `hosted-test` usam só o modo de teste da Stripe; a Stripe CLI encaminha webhooks no ambiente local;</li><li>em produção, o backend **recusa iniciar** com chave `sk_test_` ou sem o segredo do webhook (como a trava D6 do bloco 2b);</li><li>sem as chaves, a cobrança fica desligada e as contas continuam em `TRIAL`, para não travar o desenvolvimento.</li></ul> | — | deploy |

## Implementação da SC-031 (2026-10-27)

- **Migração V31:** tabela `billing_account`, uma linha por proprietário. A conta nasce em `TRIAL` no primeiro uso (painel, criação de loja ou de produto), com 14 dias.
- **Domínio e serviço:** o módulo `billing` tem `Plan` (empresas, produtos por loja e perguntas por mês), `BillingAccount` (estado efetivo calculado pelo tempo) e `BillingService`.
- **Limites (B9), aplicados no backend sob trava por proprietário ou por loja, para que duas requisições não ocupem a mesma vaga:**
  - empresas, na criação da empresa;
  - produtos por loja, no cadastro de produto;
  - cota de IA por conta, somada entre as lojas do proprietário (Essencial: 0).

  O limite de fotos continua o técnico do ADR-0011, 6 por produto em todos os planos.
- **Situação da conta (B7):**
  - um interceptador recusa com **402 `PLAN_INACTIVE`** as alterações (`POST`, `PUT`, `PATCH` e `DELETE` em `/api/tenants/{id}/**`) de contas fora de dia, **menos as operações de pedidos**: cancelar e reembolsar continuam, porque há compradores esperando;
  - a vitrine pública dessas lojas responde 404, como loja não publicada, menos o acompanhamento e o pagamento de pedidos já feitos;
  - nada é apagado.
- **Limite atingido:** **409 `PLAN_LIMIT`** com mensagem para o lojista.
- **API:** `GET /api/billing/account` devolve status, plano, ciclo, datas, limites e quantas empresas o proprietário tem.
- **Chave `billing.enabled` (`BILLING_ENABLED`), desligada por padrão até a SC-032:** sem ela não há limite nem bloqueio, e o assistente mantém a cota por loja do ADR-0016. Ligar antes da integração com a Stripe bloquearia todas as lojas no fim do teste, sem forma de pagar.
- **Testes** (`mvnw clean verify` em 2026-10-27: 98 unitários e 167 de integração, todos passando; o 1 ignorado é a avaliação paga da SC-029):
  - `BillingAccountTest`, com as regras de estado e a tabela de planos;
  - `BillingIT`, com a chave ligada e Postgres real: teste com limites do Profissional, limites de empresas e de produtos, cota de IA zerada no Essencial, teste vencido e carência vencida bloqueando alterações e a vitrine sem apagar dados, e reativação ao pagar.

## Fluxos

1. **Cadastro:** a conta nasce em `TRIAL` com 14 dias, e o painel mostra quantos dias faltam.
2. **Assinar:**
   - em "Plano e cobrança", o lojista escolhe plano e ciclo;
   - o backend cria (ou reaproveita) o `Customer` com o e-mail da conta e `metadata.account_id`;
   - o backend cria a Checkout Session, com `mode=subscription`, `client_reference_id` igual ao id da conta e o preço por `lookup_key`, e redireciona.
3. **Confirmação:**
   - chega o webhook `checkout.session.completed` ou `customer.subscription.created`;
   - o backend consulta a assinatura na API e grava `ACTIVE`, com plano, ciclo e `current_period_end`.
4. **Renovação:** `invoice.paid` estende o período.
5. **Falha de cobrança:**
   - `invoice.payment_failed` coloca a conta em `PAST_DUE`, com aviso por e-mail e no painel;
   - a Stripe tenta de novo (Smart Retries);
   - depois de 7 dias sem pagamento, a conta vai para `SUSPENDED`.
6. **Gerenciar:** o botão "Gerenciar assinatura" abre uma sessão do portal do cliente.
7. **Cancelar:** pelo portal, no fim do período; `customer.subscription.deleted` leva a conta a `CANCELED`.

## Consequências e riscos

| Risco | Mitigação |
| --- | --- |
| **Eventos fora de ordem ou repetidos** | id de evento único, releitura da assinatura na API e mudanças de estado idempotentes |
| **Webhook falso** | assinatura HMAC conferida pelo SDK e tolerância de tempo; nada muda sem confirmação na API |
| **Lojista sem cartão de crédito** | é limitação conhecida até o Pix Automático (cerca de 6 meses, pela Asaas); o teste grátis dá tempo para decidir |
| **Tarifa:** cerca de 3,99% + R$ 0,39 por cobrança e cerca de 0,7% do Billing | já considerada na margem dos planos (PRICING_PROPOSAL, seção 3.1); confirmar no contrato |
| **Nota fiscal** | a Stripe não emite NFS-e no Brasil; o emissor fica a definir com o contador, fora deste ADR |
| **Dois lugares com valores** (Stripe e código) | teste que compara a tabela do backend com `lib/plans.ts`, e preços na Stripe criados por script revisado, nunca à mão sem conferência |
| **Bloqueio indevido de loja** | carência de 7 dias, avisos antes, dados preservados e reativação imediata ao pagar |

## Tarefas propostas (cada uma exige autorização própria)

1. **Modelo e regras (SC-031):**
   - migração `billing_account`, `billing_subscription` e `billing_event`;
   - estados, teste grátis e carência;
   - limites por plano no backend, incluindo a cota de IA por conta;
   - testes de isolamento.
2. **Integração com a Stripe (SC-032):**
   - SDK;
   - Checkout Session, portal e webhook com verificação e conciliação;
   - travas de ambiente;
   - testes com servidor falso da Stripe.
3. **Telas e textos (SC-033):**
   - "Plano e cobrança" na conta;
   - avisos de teste, limite e carência;
   - vitrine suspensa;
   - seção de assinatura nos Termos de uso.
4. **Validação real:** o responsável cria a conta Stripe, as chaves de teste e os produtos, e roda o fluxo completo em modo de teste no ambiente hospedado.

## Referências

- **Stripe, assinaturas:** [visão geral](https://docs.stripe.com/billing/subscriptions/overview), [testes grátis](https://docs.stripe.com/billing/subscriptions/trials).
- **Stripe, webhooks e portal:** [webhooks](https://docs.stripe.com/webhooks), [portal do cliente](https://docs.stripe.com/customer-management).
- **Stripe, Brasil:**
  - [Pix Automático](https://docs.stripe.com/payments/pix/accept-a-recurring-payment), indisponível para conta brasileira segundo a [central de ajuda](https://support.stripe.com/questions/how-to-enable-pix-as-a-payment-method-in-brazil);
  - [exigências para conta brasileira](https://support.stripe.com/questions/brazil-specific-information-to-open-a-stripe-account).
