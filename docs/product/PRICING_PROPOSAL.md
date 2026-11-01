# Proposta de planos de assinatura (SaaS)

Registrada em 2026-10-26 a pedido do responsável.

**Decisões do responsável (2026-10-26):**
- **Planos:** os três planos e preços da seção 3 estão aprovados, com teste grátis de 14 dias.
- **Ciclos:** semestral com **10%** de desconto e anual com **20%** (seção 3.1).
- **Cobrança:** **só cartão, pela Stripe.** A Asaas, com Pix Automático, fica para quando o CNPJ completar 6 meses.
- **IA:** o assistente começa no **Claude Sonnet 5.5**.
- **Landing:** os cards de planos estão publicados em `/#planos`.

**Ainda não implementado:** a cobrança e os limites (seção 5).

Texto original da proposta: Preços, limites e o modelo de IA dependem da aprovação do responsável. Tributação e contrato precisam ser validados com contador e com o Mercado Pago.

Base: [PRD.md](PRD.md) ("Assinatura SaaS paga pelo lojista… Preços dos planos ainda serão validados"), [ADR-0016](../architecture/adr/0016-consultative-agent.md) (custo da IA) e [RELEASE_PLAN.md](RELEASE_PLAN.md) (blocos 4 e 5).

## 1. Custos de operação

Valores mensais em US$ convertidos a **R$ 5,50 por dólar**. É uma premissa de planejamento: confirme a cotação, porque a cada R$ 0,50 de variação os custos em dólar mudam cerca de 9%. As fontes são de 2026, e os preços de Render, Vercel e Brevo devem ser conferidos nos painéis antes de contratar, porque as páginas oficiais não puderam ser lidas automaticamente.

### 1.1 Custo fixo, ou seja, a plataforma ligada mesmo sem clientes

| Item | Plano sugerido | US$/mês | R$/mês |
| --- | --- | --- | --- |
| Backend Java no Render | Standard (1 CPU, 2 GB) — o Starter de 512 MB é pouco para a JVM | 25 | 138 |
| Postgres no Render | instância de 1 GB mais cerca de 20 GB de disco (as fotos ficam no banco, ADR-0011) | ~25 | ~138 |
| Workspace do Render | Professional, se exigido para produção ou equipe | 19 | 105 |
| Vercel | Pro (o Hobby não permite uso comercial); admin e vitrine no mesmo time | 20 | 110 |
| Brevo | gratuito (300 e-mails/dia), porque hoje só saem ativação e redefinição de senha | 0 | 0 |
| Domínio `.com.br` | registro anual | — | ~4 |
| **Total** | | **~89** | **~490** |

Por volta de 150 a 300 lojas ativas, a previsão é subir o backend para Pro (US$ 85) e o banco para uma instância maior. O custo fixo passa então para cerca de **R$ 1.100/mês**.

### 1.2 Custo variável por cliente

| Item | Estimativa |
| --- | --- |
| Tráfego de fotos e páginas (Render e Vercel) | R$ 1 a 8 por loja/mês, conforme as visitas; o pacote incluído da Vercel cobre o início |
| Disco das fotos no banco | centavos por loja (100 produtos × 3 fotos ≈ 45 MB) |
| E-mail | zero no plano gratuito; vira custo se a plataforma passar a enviar e-mails de pedido ao comprador |
| **Cobrança da assinatura** (arranjo de 2026-10-26, seção 5.1) | **Pix mensal pelo Mercado Pago:** 0,99% por Pix recebido. **Cartão recorrente pela Stripe:** 3,99% + R$ 0,39 por cobrança, mais cerca de 0,7% do Stripe Billing. Os valores precisam ser confirmados nas contas antes de contratar |
| **Impostos** | Simples Nacional: de **6%** (Anexo III, com fator R) a **15,5%** (Anexo V). Usei 6%; **confirmar com contador** |
| **IA (assistente e "Explicar")** | **R$ 0,16 a 0,44 por pergunta** no Claude Opus 5.5 com `effort: low` (US$ 0,03 a 0,08, ADR-0016 A8, a medir na SC-029). No Sonnet 5.5, cerca da metade. No Haiku 5.5, uma fração disso |

A IA é o único custo que cresce de forma relevante com o uso. Por isso ela fica nos planos superiores, com cota.

## 2. Referência de mercado (para posicionamento, não para copiar)

- **Nuvemshop:** Essencial R$ 69, Impulso R$ 164 e Escala R$ 449 por mês. Fora do meio de pagamento próprio, cobra tarifa por venda de 0,7% a 2%.
- **Loja Integrada:** planos de cerca de R$ 54 a R$ 250 por mês. Fora do meio de pagamento próprio, cobra tarifa por venda de 0,5% a 0,99%.
- Fontes: páginas e blogs das próprias empresas e de terceiros, de setembro e outubro de 2026, com divergências entre elas.

**Diferenciais do Smart Commerce para justificar o preço:**
- **sem tarifa por venda:** o lojista paga só o Mercado Pago, na conta dele;
- **financeiro com margem real**, despesas e alertas;
- **assistente que explica os números da loja.**

## 3. Planos propostos

Cada "loja" é uma empresa com sua vitrine (ADR-0006). O limite vale por conta de proprietário.

| | **Essencial** | **Profissional** | **Escala** |
| --- | --- | --- | --- |
| Mensal | **R$ 59** | **R$ 129** | **R$ 299** |
| Anual (por mês, cerca de 2 meses grátis) | R$ 49 | R$ 109 | R$ 249 |
| Lojas por conta | 1 | 2 | 5 |
| Produtos por loja | 150 | 2.000 | ilimitado (com limite técnico de segurança) |
| Fotos por produto | 6 | 6 | 6 (limite técnico do ADR-0011, igual em todos os planos desde 2026-10-27) |
| Funcionários (operador de caixa), por conta | 1 | 3 | 10 (aprovado em 2026-11-01, SC-049; cada pessoa conta uma vez em todas as lojas, convites em aberto incluídos) |
| Pedidos e vendas | ilimitados, **sem tarifa por venda** | ilimitados, sem tarifa | ilimitados, sem tarifa |
| Mercado Pago, vitrine, categorias, estoque, logo e contatos | ✓ | ✓ | ✓ |
| Financeiro: margem, despesas e caixa | resumo do mês | completo | completo |
| Alertas (estoque, pedidos, margem) | ✓ | ✓ | ✓ |
| **Assistente com IA e "Explicar"** | — | **60 perguntas/mês** por conta | **200 perguntas/mês** por conta, compartilhadas entre as lojas |
| Suporte | e-mail | e-mail prioritário | prioritário |

### 3.1 Ciclos semestral e anual (aprovados)

O desconto foi escolhido para manter a margem com o pior caso de IA (cota cheia no Sonnet 5.5, R$ 0,22 por pergunta) e com a tarifa da Stripe sobre o total cobrado de uma vez. O pagamento antecipado ainda reduz tarifa por mês e cancelamento.

| Plano | Mensal | Semestral (−10%) | Anual (−20%) | Margem por mês no anual, pior caso |
| --- | --- | --- | --- | --- |
| Essencial | R$ 59 | R$ 318 (R$ 53/mês) | R$ 564 (R$ 47/mês) | R$ 40 |
| Profissional | R$ 129 | R$ 696 (R$ 116/mês) | R$ 1.236 (R$ 103/mês) | R$ 76 |
| Escala | R$ 299 | R$ 1.614 (R$ 269/mês) | R$ 2.868 (R$ 239/mês) | R$ 161 |

**Valores e regras do pagamento antecipado:**
- Os totais são arredondados para baixo, em reais inteiros, e ficam em `admin-web/src/lib/plans.ts`, que é a fonte da landing. O módulo de cobrança vai repetir os mesmos valores no backend.
- O cálculo da margem considera a Stripe a 4,69% + R$ 0,39, imposto de 6% e a infraestrutura variável.
- **Arrependimento:** quem pagar à vista pode desistir em até 7 dias pelo direito de arrependimento (CDC, art. 49), com reembolso integral.
- **Cancelamento depois disso:** a regra vai nos Termos de uso. Proposta: o acesso continua até o fim do período pago, sem reembolso proporcional.

**Avulsos e regras:**
- **Pacote de IA extra:** +50 perguntas por R$ 19, sem renovação automática. Cobre o pior caso (50 × R$ 0,44 = R$ 22) quase no custo: serve para não frustrar quem precisa, não para gerar lucro.
- **Teste grátis:** 14 dias do Profissional, sem cartão. É melhor do que um plano gratuito permanente, que gera custo fixo e suporte sem receita.
- **Inadimplência:** 7 dias de carência com aviso. Depois, a vitrine sai do ar e o painel fica somente leitura. **Nenhum dado é apagado** sem os prazos da Política de privacidade.
- **Mudança de plano:** sobe na hora, com valor proporcional. Desce no próximo ciclo, desde que o uso caiba no plano menor.

## 4. Margem por cliente, por mês

Considerando o arranjo da seção 5.1 (Pix mensal pelo Mercado Pago e cartão pela Stripe), imposto de 6% e infraestrutura variável estimada.

| Plano | Preço | Tarifa: Pix MP · cartão Stripe | Imposto | IA no uso típico (Opus) | IA no pior caso (cota cheia) | Margem típica (Pix) | Margem no pior caso (cartão) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Essencial | R$ 59 | R$ 0,58 · R$ 3,16 | R$ 3,54 | — | — | **R$ 53** | R$ 51 |
| Profissional | R$ 129 | R$ 1,28 · R$ 6,44 | R$ 7,74 | R$ 3,30 | R$ 26,40 | **R$ 114** | R$ 86 |
| Escala | R$ 299 | R$ 2,96 · R$ 14,41 | R$ 17,94 | R$ 9,90 | R$ 88,00 | **R$ 260** | R$ 171 |

O Pix é o meio mais barato para a plataforma. No cartão, a Stripe custa quase o mesmo que o Mercado Pago a 4,99%.

- **Ponto de equilíbrio:** com o custo fixo de cerca de R$ 490, bastam **10 clientes Essencial**, ou 5 Profissional, para pagar a infraestrutura.
- **Exemplo de receita:** 100 clientes (60 Essencial, 30 Profissional e 10 Escala) geram **R$ 10.400/mês** de receita bruta e cerca de **R$ 8.900 de margem de contribuição**, contra cerca de R$ 1.100 de infraestrutura nessa escala. Isso é antes de salários, suporte, contador e marketing.
- **Com Simples a 15,5%:** a margem cai cerca de R$ 6 (Essencial) a R$ 28 (Escala) por cliente. Os planos continuam saudáveis.

## 5. O que precisa ser construído (bloco novo, depois da aprovação)

1. **ADR de assinatura** ([ADR-0017](../architecture/adr/0017-saas-billing-stripe.md), adotado em 2026-10-27): cobrança das mensalidades pela **conta da plataforma** (Pix mensal pelo Mercado Pago e cartão recorrente pela Stripe, seção 5.1), separada das contas Mercado Pago dos lojistas, que continuam recebendo as vendas. Também define:
   - webhook validado, idempotência e conciliação;
   - carência e mudança de plano;
   - emissão de nota fiscal da assinatura, a definir com o contador.
2. **Módulo `billing`** no backend, com um adaptador por provedor (Mercado Pago Pix e Stripe Billing):
   - planos e assinatura por conta;
   - limites de lojas, produtos, fotos e cota de IA aplicados **no backend**;
   - teste grátis e estados (ativa, em carência, suspensa).
3. **Telas:**
   - escolha de plano no cadastro;
   - "Plano e cobrança" na conta;
   - avisos de limite e de carência;
   - página pública de preços na landing.
4. **Cota de IA por plano:** substitui a cota fixa por loja do ADR-0016 (A8) e mantém o teto global.
5. **Termos de uso:** a seção de assinatura (cobrança, renovação, cancelamento e reembolso conforme o CDC) entra no texto em validação do bloco 1d.

### 5.1 Arranjo de cobrança das mensalidades (análise de 2026-10-26)

**A Asaas foi descartada:** ela só libera o Pix Automático para CNPJ ativo há pelo menos 6 meses, e o da operadora ainda não chegou lá.

**O que existe hoje para cobrança recorrente por Pix**

| Provedor | Pix recorrente | Situação |
| --- | --- | --- |
| **Mercado Pago** | as assinaturas (`/preapproval`) aceitam Pix entre os meios, mas cobram pelos meios salvos na conta Mercado Pago do pagador, por link de checkout próprio. **Não há API documentada de Pix Automático** | usar o **Pix mensal**: a plataforma gera uma cobrança Pix (QR Code e copia e cola) a cada ciclo |
| **Stripe** | a documentação técnica descreve o Pix Automático (mandatos), mas a central de ajuda afirma que, **em contas brasileiras, o Pix é só avulso e o Pix Automático "não está disponível"**. O Pix avulso também depende de elegibilidade da conta | **não contar com Pix na Stripe**; ela fica só com o cartão |
| Efí, Asaas e outros | têm API de Pix Automático, com exigências próprias de conta | reavaliar quando o CNPJ completar 6 meses |

**Arranjo recomendado, que é viável com o CNPJ atual**
1. **Pix mensal pelo Mercado Pago** (0,99%), pela conta da plataforma, com credenciais próprias e separadas do OAuth dos lojistas.
   - A cada ciclo, o backend cria a cobrança Pix com vencimento e manda o QR Code por e-mail e no painel cinco dias antes.
   - O pagamento é confirmado por webhook validado mais consulta à API, como já é feito nas vendas (SC-014 e SC-015).
   - Não é débito automático: **o lojista paga a cada mês.** Para reduzir esquecimento:
     - lembretes em D-5, no dia e em D+3;
     - carência de 7 dias;
     - plano anual por Pix com desconto.
2. **Cartão recorrente pela Stripe Billing** (3,99% + R$ 0,39 mais cerca de 0,7%). O cartão é cobrado automaticamente todo mês. A Stripe também cuida de:
   - novas tentativas;
   - cartão vencido;
   - valor proporcional ao trocar de plano;
   - portal do cliente para trocar o cartão.

   A conta exige CNPJ ativo e representante legal com CPF; não encontrei exigência de idade mínima do CNPJ. **Confirmar no cadastro.**
3. **Mais adiante:** quando o CNPJ completar 6 meses, avaliar o Pix Automático (Asaas ou Efí) como terceira opção, sem trocar o resto.

**Custos e riscos de usar dois provedores**

| Ponto | Consequência | Mitigação |
| --- | --- | --- |
| Duas integrações, dois webhooks e dois painéis | mais código e mais conciliação | módulo `billing` com interface única de provedor e um estado da assinatura no nosso banco, alimentado pelos dois |
| Pix mensal depende do lojista | mais atraso que no débito automático | lembretes, carência, plano anual e opção de trocar para cartão a qualquer momento |
| Repasses em duas contas | dinheiro em dois lugares | conciliação mensal simples, já que o volume é baixo no início |
| Nota fiscal | nenhum dos dois emite NFS-e da mensalidade de forma nativa neste arranjo | emissor de NFS-e à parte, a definir com o contador |
| Alternativa mais simples | fazer o cartão também pelo Mercado Pago (`/preapproval`, cerca de 4,99%) | um provedor só, menos código. Troca: a recorrência, a troca de cartão e o valor proporcional da Stripe são mais completos. **Decisão do responsável** |

**Esforço estimado:** de duas a duas sprints e meia:
- ADR, módulo `billing`, Pix mensal no Mercado Pago com webhook e conciliação, estados e lembretes;
- Stripe Billing com checkout de cartão, webhooks e portal do cliente;
- limites por plano, telas e testes nos ambientes de teste dos dois provedores.

## 6. Decisões do responsável

1. Preços e limites da tabela da seção 3, ou ajustes.
2. **Modelo da IA:** Opus 5.5 (melhor qualidade, custo maior), Sonnet 5.5 (cerca de metade do custo) ou Haiku 5.5 (muito mais barato e menos capaz). Decidir de preferência depois da avaliação paga da SC-029.
3. Teste grátis de 14 dias ou plano gratuito permanente.
4. Regime tributário e alíquota real, com o contador.
5. **Cobrança (2026-10-26):** a Asaas ficou descartada pela idade do CNPJ. Falta confirmar:
   - Pix mensal pelo Mercado Pago com cartão pela Stripe, ou tudo pelo Mercado Pago;
   - as tarifas reais nas duas contas;
   - se a Stripe aceita o CNPJ atual.

## Fontes consultadas (2026-10-26)

- **Render:** [makerkit](https://makerkit.dev/pricing-calculator/render), [deployhandbook](https://deployhandbook.com/pricing/render), [frontdeskreview](https://frontdeskreview.com/software/managed-postgres/render/).
- **Vercel:** [documentação do plano Pro](https://vercel.com/docs/plans/pro-plan).
- **Brevo:** [moosend](https://moosend.com/blog/brevo-pricing/), [sendx](https://www.sendx.io/blog/brevo-pricing-plans-costs-alternatives-2026).
- **Mercado Pago (comparação):** [blog oficial sobre recorrência](https://www.mercadopago.com.br/blog/pagamento-recorrente-ou-unico), [calculadoradetaxas](https://www.calculadoradetaxas.com.br/mercado-pago/taxas).
- **Asaas:**
  - documentação: [Pix Automático](https://docs.asaas.com/docs/pix-automatico), [FAQ do Pix Automático](https://docs.asaas.com/docs/faq-2), [Assinaturas](https://docs.asaas.com/docs/assinaturas), [eventos de assinatura](https://docs.asaas.com/docs/eventos-para-assinaturas), [webhooks](https://docs.asaas.com/docs/receive-asaas-events-at-your-webhook-endpoint);
  - tarifas: [blog oficial](https://blog.asaas.com/taxas-asaas/), [QUASA](https://quasa.io/pt/media/asaas-ou-iugu-tarifa-publicada-vence-ate-o-volume-pedir-proposta), [Dardo](https://dardo.studio/pt/blog/gateway-de-pagamento/).
- **Stripe:**
  - [Pix Automático na documentação](https://docs.stripe.com/payments/pix/accept-a-recurring-payment);
  - [como ativar o Pix no Brasil](https://support.stripe.com/questions/how-to-enable-pix-as-a-payment-method-in-brazil), onde está a informação de que o recorrente não está disponível em contas brasileiras;
  - [exigências para conta brasileira](https://support.stripe.com/questions/brazil-specific-information-to-open-a-stripe-account);
  - [preços](https://stripe.com/en-br/pricing).
- **Mercado Pago, assinaturas:** [visão geral](https://www.mercadopago.com.br/developers/pt/docs/subscriptions/overview) e [Pix Automático no blog](https://www.mercadopago.com.br/blog/pix-automatico-gestao-assinaturas-receita-recorrente) (sem API documentada).
- **Pix Automático, regras do Banco Central:** [Suno](https://www.suno.com.br/noticias/?p=496954), [Tecnospeed](https://blog.tecnospeed.com.br/?p=23867).
- **Concorrência:** [Nuvemshop, planos](https://www.nuvemshop.com.br/planos-e-precos), [comparativo Nuvemshop × Loja Integrada](https://www.nuvemshop.com.br/compare/nuvemshop-ou-loja-integrada).
- **Claude:** preços registrados no ADR-0016.
