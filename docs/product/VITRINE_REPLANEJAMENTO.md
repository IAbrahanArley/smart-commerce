# Replanejamento da vitrine e loja de extensões (proposta)

Data: 2026-10-27. **Status:** D1 e D3 aprovados em 2026-10-27 — a V1 vira a [Sprint 8](SPRINT_08.md), executada **antes** da Sprint 7, e tudo da V1 mais a conta do cliente são gratuitos. Detalhamento técnico no [ADR-0018](../architecture/adr/0018-storefront-v1.md); D4 vira a decisão V4 do ADR; D5 aprovado em 2026-10-27 junto com as decisões V1–V13 do ADR; D2, D6 e D7 continuam em aberto. Pedido do responsável: remodelar a vitrine para um padrão de "empresa grande" e monetizá-la com uma **loja de extensões** dentro do admin, onde o lojista compra temas e funcionalidades além da vitrine padrão. Referência funcional: [BENCHMARK_VITRINE_SPORTLIFE.md](BENCHMARK_VITRINE_SPORTLIFE.md).

Esta proposta amplia o escopo do MVP e mexe em identidade visual, cobrança e dados pessoais; por isso cada fase depende das decisões da seção 5.

## 1. Conceito

- **Vitrine padrão (gratuita, incluída no plano):** tudo o que um comprador espera de uma loja grande — navegação por categorias, busca com filtros, página de produto completa, carrinho e checkout claros, SEO e páginas institucionais. A vitrine padrão precisa ser boa sozinha; a loja de extensões não pode vender o básico de volta.
- **Loja de extensões (paga, no admin):** temas premium e módulos opcionais que o lojista ativa por loja. A plataforma cobra o **lojista**, nunca o comprador.
- Não é o "marketplace unificado" que o PRD deixa fora do MVP (vários vendedores num mesmo catálogo). Aqui cada loja continua independente; o que se vende são extensões da própria loja. Para evitar confusão, o nome interno proposto é **Loja de extensões**.

## 2. Lacunas entre a vitrine atual e o benchmark

| Funcionalidade | Hoje | Proposta |
| --- | --- | --- |
| Categorias (árvore) e menu | Planas, uma por produto, filtro simples na vitrine (bloco 1c) | Vitrine padrão — fase V1 |
| Marca do produto | Não existe | V1 |
| Preço "de/por" e selo de desconto | Não existe | V1 (só exibição de preço cadastrado; não é cupom) |
| Desconto no Pix e parcelamento exibidos | Não existe | V1, **apenas se** o valor cobrado no Mercado Pago bater com o exibido (D4) |
| Banners e vitrines da home (Lançamentos, Destaques, por categoria) | Lista única paginada | V1 |
| Busca, filtros (categoria, marca, preço, variante) e ordenação | Não existe | V1 |
| Breadcrumb, SEO por página, Open Graph, JSON-LD de produto | Parcial | V1 (JSON-LD supera o benchmark) |
| Galeria com miniaturas e zoom | Galeria simples | V1 |
| Variantes por botões, esgotado marcado | Existe seleção de variante | V1 (refino) |
| Produtos relacionados | Não existe | V1 (mesma categoria) |
| Páginas institucionais (sobre, trocas, privacidade, termos) e rodapé com CNPJ | Só os termos e a privacidade da plataforma (bloco 1d); a loja não tem páginas próprias | V1 |
| Botão de WhatsApp e redes sociais | "Fale com a loja" com WhatsApp, e-mail e Instagram (bloco 1b) | V1 (configuração simples) |
| Rastrear pedido | Página do pedido por cookie | V1 (por número + e-mail) |
| Cálculo de frete por CEP | Frete fixo ou retirada | Fica como está; frete por faixa de CEP é V3; integração nacional segue fora do MVP |
| Conta do cliente, favoritos, histórico | Não existe | V3 (novo domínio de identidade; LGPD) |
| Cupom de desconto | Não existe | Extensão (V3) — mexe em valores cobrados |
| Avise-me, newsletter | Não existe | Extensão (V3) — coleta de e-mail exige base legal |
| Pixels (GA4, Meta, TikTok) | Não existe | Extensão (V3) com banner de consentimento |
| Avaliações | Não existe | Extensão (V3) |
| Temas e personalização visual | Tokens `--store-` fixos | V2 (contrato de tema) |

## 3. Fases propostas

### V1 — Vitrine padrão "grande empresa" (sem cobrança)
1. **Catálogo estruturado:** categorias hierárquicas (até 3 níveis), marca, preço de comparação ("de"), atributos de variante já existentes usados como filtro. Migrações aditivas.
2. **API pública ampliada:** listagem por categoria, busca textual simples no PostgreSQL, facetas com contagem, ordenação, paginação numerada. Mesmas regras do ADR-0010: só loja e produto publicados, 404 uniforme, sem custo/SKU interno/saldo exato.
3. **Conteúdo da loja no admin:** banners da home, seleção de vitrines da home, páginas institucionais, dados do rodapé (razão social, CNPJ, contatos, redes, WhatsApp).
4. **Nova vitrine:** cabeçalho com busca e mega-menu (gaveta no celular), home com banners e vitrines, categoria/busca com filtros, produto completo, carrinho e checkout sem distrações, rastrear pedido, rodapé completo, SEO.
5. **Desempenho:** hoje a vitrine usa `no-store`. Uma loja grande precisa de cache curto (revalidação por tempo ou por evento de publicação) sem quebrar a regra "ocultar vale para novas consultas" (D6).

### V2 — Temas e base da loja de extensões
1. **Contrato de tema:** um tema é um conjunto validado de tokens `--store-*` (cores, tipografia de uma lista fechada, raios, densidade) e de layouts pré-definidos de cabeçalho, home e card. **Sem CSS ou JavaScript arbitrário do lojista ou de terceiros.** Contraste AA validado no backend antes de aplicar.
2. **Catálogo de extensões** mantido pela plataforma, **direitos por loja** (`tenant` × extensão × estado × validade), ativação e desativação no admin, e a vitrine lê os direitos pelo backend — nunca por parâmetro do navegador.
3. **Cobrança da plataforma** (D2): assinatura mensal por extensão ou pacote. É um fluxo de dinheiro novo, separado dos pagamentos das lojas.

### V3 — Primeiras extensões pagas (candidatas)
- Temas premium (2–3 no lançamento).
- Cupons e promoções (valor percentual/fixo, validade, limite de uso; calculado no backend).
- Avise-me quando chegar + newsletter, com consentimento registrado.
- Integrações de marketing (GA4, Meta Pixel, TikTok) com banner de consentimento.
- Avaliações de produtos com moderação.
- Recomendações de "compre junto" usando os dados do `analytics` (IA só sugere; lojista aprova).
- Frete por faixa de CEP.
- Conta do cliente com favoritos e histórico (pode ser padrão em vez de extensão — D3).

## 4. Riscos

- **Segurança multi-tenant:** temas e extensões não podem vazar configuração entre lojas; direitos sempre derivados do tenant resolvido pelo slug publicado.
- **Scripts de terceiros:** pixels coletam dados do comprador; exigem consentimento e revisão de CSP.
- **Promessa de preço:** Pix e parcelas exibidos diferentes do cobrado geram reclamação e risco no CDC.
- **Escopo:** V1 sozinha é do tamanho de uma sprint e meia; V2 com cobrança é outra sprint. Concorre com a Sprint 7 já planejada.
- **Identidade visual:** "padrão empresa grande" troca a base atual da vitrine (Arial, azul profundo) definida no DESIGN.md; exige aprovação e atualização do documento.

## 5. Decisões necessárias

| # | Decisão | Opções / recomendação |
| --- | --- | --- |
| D1 | Prioridade frente à Sprint 7 | Recomendação: V1 como Sprint 8, depois da Sprint 7; ou intercalar se a vitrine for urgente para vender o produto. |
| D2 | Como a plataforma cobra o lojista | A assinatura já usa a Stripe ([ADR-0017 — Stripe](../architecture/adr/0017-saas-billing-stripe.md)). Falta decidir se as extensões entram como itens adicionais da mesma assinatura, além de NFS-e e política de cancelamento. Agentes não executam cobranças. |
| D3 | O que é gratuito e o que é pago | Recomendação: tudo da V1 é gratuito; conta do cliente também; pagos são temas, marketing, cupons, avaliações e IA. |
| D4 | Pix com desconto e parcelamento | Exibir só se configurado no Mercado Pago do lojista e o backend cobrar exatamente o exibido; senão, mostrar só o preço. |
| D5 | Nova direção visual da vitrine padrão | Aprovar proposta de tipografia, paleta e layout (com mockup) antes de implementar; atualizar DESIGN.md. |
| D6 | Cache da vitrine | Revalidação curta (ex.: 60 s) + invalidação ao publicar/ocultar, alterando o ADR-0010. |
| D7 | Preços das extensões | Definir após validar com lojistas (PRD já lista disposição a pagar como hipótese). |

## 6. Próximo passo sugerido

Aprovar D1, D3 e D5. Com isso, registrar um ADR da vitrine V1 e um `SPRINT_08.md` com tarefas pequenas (catálogo estruturado → API pública → conteúdo no admin → nova vitrine → revisão), no mesmo formato das sprints anteriores. V2/V3 só depois de D2.
