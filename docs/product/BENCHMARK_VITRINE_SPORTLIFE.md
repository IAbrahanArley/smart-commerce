# Benchmark de vitrine — Sport Life (sportlifejp.com.br)

Data da observação: 2026-10-27, no navegador do app (Chromium, desktop). Somente leitura: um produto foi colocado no carrinho para ver carrinho e a primeira tela do checkout; nenhum dado pessoal foi informado e nenhum pedido foi feito.

**Uso deste documento:** referência **funcional** para a vitrine padrão. Não copiar identidade visual, textos, imagens ou marca da loja nem da plataforma. A loja roda na **Loja Integrada** (`meta generator`, CDN `cdn.awsli.com.br`), então o que aparece aqui é, em grande parte, o pacote padrão de uma plataforma SaaS concorrente — útil para saber o mínimo que o lojista espera.

## 1. Estrutura global (todas as páginas)

| Área | Observado |
| --- | --- |
| Barra superior | Ícones de redes sociais (Facebook, YouTube, Instagram, blog) e link "Fale Conosco". |
| Cabeçalho | Logo; saudação "Bem-vindo, identifique-se para fazer pedidos"; atalhos **Rastrear**, **Meus Pedidos**, **Minha Conta**; mini-carrinho ("Carrinho vazio" / quantidade). |
| Busca | Campo "Digite o que você procura" → `/buscar?q=`. |
| Menu | Departamentos com **mega-menu de até 3 níveis** (ex.: Ciclismo → Roupas → Masculino → Camisa), incluindo coleções de marketing ("Outlet", "Promoções"). |
| Banner de topo | Faixa promocional repetida em todas as páginas (campanha "Queima de estoque"). |
| WhatsApp | Botão flutuante com mensagem pré-preenchida (`wa.me`). |
| Newsletter | "Cadastre-se e receba cupons de até R$50" no rodapé. |
| Rodapé | Categorias; páginas de conteúdo (Fale Conosco, Sobre Nós, Termos de Serviço, Política de Privacidade, Compra Segura, Trocas e Devoluções); "Sobre a loja"; bandeiras de pagamento; selos; **razão social + CNPJ** e copyright. |
| Rastreamento | GA/Google Ads, GTM, Meta Pixel, TikTok Pixel e ferramenta de e-mail marketing. Não foi observado banner de consentimento de cookies. |

## 2. Página inicial

- **Carrossel de banners** (anterior/próximo).
- **Vitrines em sequência**, cada uma um bloco de produtos com título: Lançamentos, Mais Vendidos, Destaques, Outlet, e uma por categoria (Triathlon, Promoções, Corrida, Roupas, Ciclismo, Energia & Hidratação). Aparentemente 6 produtos por bloco (3 em Destaques).
- **Card de produto:** foto, nome, preço "de" riscado + preço "por", **selo "30% DESCONTO"**, **parcelamento** ("até 2x de R$ 115,15 sem juros"), **preço no Pix** ("ou R$ 218,78 via Pix", 5% de desconto) e botão **Comprar**.

## 3. Categoria e busca

- Título (H1), **breadcrumb**, meta description própria por categoria (SEO).
- **Filtros (facetas) com contagem:** departamento, marca e faixa de preço.
- **Ordenação:** relevância, ordem alfabética, mais vendidos, menor preço, maior preço, maior desconto.
- **Paginação numerada**, 40 produtos por página.
- Busca usa a mesma listagem e as mesmas facetas.

## 4. Página de produto

- Breadcrumb completo da árvore de categorias.
- **Galeria** com miniaturas verticais e **zoom**.
- Código (SKU) e **marca** visíveis.
- **Variante por botões** ("Selecione a opção de Tamanho": PP…XGG); tamanhos esgotados ficam marcados.
- Preço de/por, parcelamento, Pix; **tabela de parcelas** expansível (1x, 2x…).
- **Calcule o frete** por CEP na própria página.
- Quantidade + **Comprar**; estoque "Disponível".
- **Avise-me quando chegar** (nome + e-mail) para variante esgotada.
- **Lista de desejos / favoritos** (exige conta).
- Descrição longa com **ficha técnica** em texto.
- **Produtos relacionados** (4).
- Compartilhamento via Open Graph (título, imagem, descrição). **Não há** JSON-LD de produto, avaliações, tabela de medidas nem selo de prazo de entrega.

## 5. Carrinho

- Tabela produto / quantidade / preço / excluir, com SKU, estoque e variante escolhida.
- **Cálculo de frete por CEP** + link "Não sei meu CEP".
- **Cupom de desconto**.
- Total com Pix (5% off) e parcelamento.
- "Continuar comprando" e "Finalizar compra".

## 6. Checkout

- Layout **sem distrações** (sem menu), com atendimento e WhatsApp visíveis.
- **Começa pelo e-mail** ("Para continuar, informe seu e-mail"), explicando o uso do dado (identificar perfil, notificar pedido, histórico, preenchimento mais rápido).
- Resumo do pedido ao lado.
- Etapas seguintes (endereço, frete, pagamento) não foram percorridas para não informar dados.

## 7. Conta do cliente

Links observados: Minha conta, Meus pedidos, Meus favoritos, Rastrear pedido, login por e-mail + senha, sair.

## 8. Pontos fracos (onde podemos ser melhores)

- Sem JSON-LD (rich results do Google) e sem avaliações.
- Mesmos 6 produtos repetidos em quase todas as vitrines da home — curadoria pobre.
- Muitos scripts de terceiros sem consentimento visível (risco LGPD e de desempenho).
- Visual datado (grid de 2013, Bootstrap 2); mega-menu pesado no celular.
- Ficha técnica em texto corrido, sem atributos estruturados (impede filtro por tamanho, cor, gênero).
- Nenhum indicador de prazo de entrega ou política de troca próximo ao botão de compra.
