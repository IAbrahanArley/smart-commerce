# Proposta — Referências de mercado para admin e vitrine

Status: **aprovada em 2026-10-17** (A1, A3, A4, V2 e V4 aprovados pelo responsável; itens P incluídos na implementação) · Superfícies: admin e vitrine.

Esta proposta segue a governança do [DESIGN.md](DESIGN.md), que já incorpora as decisões. A seção "Execução" no fim registra desvios da implementação.

## Objetivo e critérios de aceite

Usar padrões de interface de plataformas do mesmo ramo como referência para o Smart Commerce, sem copiar identidade de marca e sem ampliar o MVP.

- Referências com fonte e data, separando fato documentado de inferência.
- Cada proposta liga o padrão de mercado a uma tela existente, com arquivo de origem.
- Paleta, Arial, raios 4/8px e assinatura em texto continuam valendo, salvo decisão explícita.
- Funcionalidades fora do escopo (carrinho, frete, avaliações, WhatsApp etc.) aparecem só como "não adotar agora".
- Lista de decisões pendentes para o responsável.

## Fontes consultadas (2026-10-17)

| Plataforma | Fonte | O que foi aproveitado |
| --- | --- | --- |
| Nuvemshop | [Nimbus — padrões](https://nuvemshop.dev/documentation/patterns), [uso de componentes](https://nuvemshop.dev/en-US/apps/admin/design-guidelines/component-usage), [Nimbus para devs](https://dev.nuvemshop.com.br/en/docs/developer-tools/nimbus) | App Shell, Menu lateral, Page, Data Table × Data List, Empty Message, Summary Stats, Form Field, Side Modal |
| Nuvemshop | [Layout Flex](https://www.nuvemshop.com.br/loja-layouts-nuvem/flex), [central de ajuda — layouts](https://atendimento.nuvemshop.com.br/pt_BR/01-layout/guia-novas-funcoes-para-os-layouts-da-sua-loja) | Composição da vitrine: produtos em destaque, galeria com miniaturas, seções reordenáveis |
| VTEX | [Shoreline — cor](https://shoreline.vtex.com/foundations/color/best-practices), [Shoreline — tokens](https://shoreline.vtex.com/guides/styling/design-tokens), [FastStore — tokens](https://developers.vtex.com/docs/guides/faststore/global-tokens-overview) | Cor de destaque reservada a ações principais e seleção; tokens semânticos fg/bg/border; status só com significado |
| Wix | [Products page (dev)](https://dev.wix.com/docs/build-apps/develop-your-app/extensions/dashboard-extensions/dashboard-plugins/supported-wix-app-dashboard-pages/wix-stores/wix-stores-products-page), [navegação do painel Stores](https://dev.wix.com/docs/api-reference/business-solutions/stores/skills/stores-dashboard-navigation), [nova página de produto](https://support.wix.com/en/article/wix-stores-customizing-the-new-product-page) | Catálogo organizado em rotas separadas: lista, novo produto, edição, estoque |
| Tray | [Como funciona](https://tray.com.br/escola/como-funciona-a-tray/), [Temas](https://tray.com.br/escola/temas-tray/) | Temas responsivos com padrão de vitrine, páginas internas e checkout; painel com indicadores |
| Loja Integrada | [Como funciona](https://lojaintegrada.com.br/hub/como-funciona-a-loja-integrada/), [Layout de moda](https://lojaintegrada.com.br/hub/layout-loja-virtual/) | Painel centralizado (pedidos, preços, estoque, produtos); temas por segmento |
| Pesquisa UX | [Baymard — product page](https://baymard.com/research-articles/current-state-ecommerce-product-page-ux) | Preço, disponibilidade e variações precisam ser encontrados rápido no celular; seleção de tamanho por botões, não lista suspensa (artigo citado, conteúdo completo é pago) |

**Limitações.** Os painéis da Tray, Loja Integrada e Wix exigem conta; não foram inspecionados ao vivo. Para eles a análise usa documentação pública e materiais oficiais, não a interface real. A Nuvemshop é a única com design system público detalhado, por isso pesa mais. `awesome-design-md` não tem referência de nenhuma dessas empresas; não foi usado nesta rodada. Shopify ficou fora por escolha do responsável.

## O que o mercado converge

**Admin**
1. **Moldura fixa (App Shell):** menu lateral persistente no desktop, recolhível; gaveta no celular. Cabeçalho com contexto da loja.
2. **Cabeçalho de página padronizado:** título, descrição curta e ação principal no topo, à direita; voltar/trilha acima do título.
3. **Lista de produtos como tabela:** foto pequena, nome, SKU/variações, preço, estoque, status. No celular vira lista de linhas (Nimbus distingue Data Table de Data List).
4. **Criar e editar em página própria:** "Novo produto" é uma rota, não um formulário embaixo da lista (Wix e Nuvemshop).
5. **Formulário em duas colunas no desktop:** conteúdo principal à esquerda (identificação, descrição, fotos, variações), lateral com publicação e resumo de estoque.
6. **Status como selo textual** (Publicado, Oculto) com cor semântica de apoio, nunca só cor.
7. **Estado vazio com próxima ação** — já exigido pelo nosso DESIGN.md.

**Vitrine**
1. **Produtos primeiro:** destaque institucional curto; a grade aparece sem rolar muito.
2. **Card com foto de capa** em proporção fixa, nome, preço e disponibilidade.
3. **Página de produto em duas colunas:** galeria com miniaturas à esquerda; nome, preço, variações e disponibilidade à direita, fixos ao rolar no desktop.
4. **Variações como botões por atributo** (cor, tamanho), não uma lista suspensa com todas as combinações.

## Diagnóstico das telas atuais

| Tela | Situação | Arquivo |
| --- | --- | --- |
| Moldura do admin | Só cabeçalho com nome e "Administração"; sem navegação entre Lojas, Produtos e Estoque. | `admin-web/src/app/layout.tsx` |
| Lista de produtos | Cards em grade sem foto; criação do produto no fim da mesma página. | `admin-web/src/components/catalog.tsx:64-72` |
| Edição de produto | Uma coluna longa; publicação no fim do formulário. | `admin-web/src/components/product-form.tsx:124-149` |
| Largura do conteúdo | `main` limitado a 64rem e `section` a 45rem; pouco aproveitamento em 1440px. | `admin-web/src/app/globals.css` (`main`, `section`) |
| Abertura da vitrine | Bloco azul grande antes dos produtos. | `storefront-web/src/app/lojas/[slug]/page.tsx:25-31` |
| Card da vitrine | Já exibe a capa (`ProductCover`), mas em grade de duas colunas largas, com preço ao lado da descrição. *Correção: o diagnóstico inicial dizia que não havia foto.* | `storefront-web/src/app/lojas/[slug]/page.tsx:46-56` |
| Variações na vitrine | Um `select` com todas as combinações. | `storefront-web/src/components/product-offer.tsx:12` |

## Propostas

Legenda: **P** = cabe no padrão aprovado (tokens/componentes existentes); **R** = mudança relevante, exige aprovação.

### Admin

| # | Proposta | Tipo | Detalhes |
| --- | --- | --- | --- |
| A1 | Moldura com navegação lateral | **R** (padrão de interação) | Itens só para funções existentes: Minhas lojas, Produtos, Estoque, Conta. Desktop ≥ 1024px: menu fixo de ~15rem em superfície branca, item atual com `--sc-color-selected-background` + barra de 3px em `--sc-color-action` + `aria-current="page"`. Abaixo disso: botão "Menu" que abre lista de navegação. Nome da loja ativa no topo do menu, em texto. Sem ícones obrigatórios. |
| A2 | Cabeçalho de página | P | Componente local `PageHeader`: link de volta, `h1`, descrição opcional, ação principal à direita (quebra para baixo no celular). |
| A3 | Lista de produtos em tabela | **R** (padrão de interação) | `<table>` com `<caption>`: Produto (miniatura 48px + nome como link), SKU ou nº de combinações, Preço (tabular, à direita), Disponível, Vitrine (selo), Ações (Estoque). Abaixo de 768px vira lista de linhas com os mesmos dados. Sem seleção em massa nem filtros (não há funcionalidade). |
| A4 | "Novo produto" em rota própria | **R** (fluxo) | Nova rota `lojas/[id]/produtos/novo`, ação principal no cabeçalho da lista. Sem mudança de API ou regra de negócio. |
| A5 | Edição em duas colunas | P | Desktop ≥ 1024px: coluna principal (identificação, descrição, variações, fotos) + lateral (publicação, link e resumo de estoque). Uma coluna no celular, na mesma ordem de leitura. |
| A6 | Selo de status | P | Componente `StatusBadge` com texto sempre visível. "Publicado": `--sc-color-success` sobre `--sc-color-success-background`; "Oculto"/"Em configuração": `--sc-color-text-secondary` sobre `--sc-color-disabled-background`. |
| A7 | Largura de trabalho | P | Conteúdo até ~80rem ao lado do menu; formulários continuam com no máximo 45rem por coluna. |

### Vitrine

| # | Proposta | Tipo | Detalhes |
| --- | --- | --- | --- |
| V1 | Foto de capa no card | P | Usa a capa real do produto, proporção 1:1, `object-fit: contain`, dimensões explícitas e `alt` já obrigatório. Sem foto: área neutra com o texto "Foto não enviada", sem imagem genérica. |
| V2 | Produtos primeiro | **R** (composição aprovada em 2026-10-17) | Trocar o bloco azul grande por uma faixa compacta com nome da loja e contagem de produtos; grade logo abaixo. Azul fica reservado a ações. |
| V3 | Página de produto em duas colunas com resumo fixo | P | Galeria à esquerda; à direita nome, preço, variações e disponibilidade com `position: sticky` no desktop. Uma coluna no celular, preço logo após a galeria. |
| V4 | Variações como botões por atributo | **R** (padrão de interação) | Um grupo de rádios estilizados por atributo (ex.: Cor, Tamanho), rótulo em texto, combinação indisponível marcada por texto e não só por cor. A API pública já expõe `options` (nome/valor) por variação; não há mudança de backend. |

### Não adotar agora (fora do MVP ou sem funcionalidade)

Carrinho e "compra rápida", calculadora de frete, botão de WhatsApp, avaliações, banners rotativos, Instagram, newsletter, busca e filtros, painel de indicadores no admin (Summary Stats só quando existirem dados reais do item 8 do PRD) e chat de IA na moldura.

## Alternativas consideradas

1. **Manter o layout atual e só refinar espaçamentos.** Menor risco, mas o admin continua sem navegação entre módulos, o que piora com pedidos e financeiro.
2. **Adotar o Nimbus (ou outra biblioteca) como dependência.** Rejeitado: troca a identidade, cria dependência de terceiro e a governança proíbe sem aprovação específica.
3. **Proposta acima (recomendada):** padrões estruturais do mercado com tokens e componentes locais do Smart Commerce.

## Impacto, riscos e acessibilidade

- **Código:** `admin-web` (layout, globals.css, catalog, product-form, nova rota) e `storefront-web` (página da loja, product-offer, globals.css). Nenhuma mudança de backend, exceto se V4 exigir atributos na API pública — nesse caso vira tarefa separada.
- **Multi-tenant:** a loja exibida no menu vem do vínculo autorizado já carregado; o menu não é prova de autorização e não muda nenhuma verificação no backend.
- **Acessibilidade:** menu com `nav` rotulado e `aria-current`; tabela com `caption` e cabeçalhos; selos com texto; rádios nativos para variações; foco visível com o token atual; revisar 320/390/768/1440px e zoom 200%.
- **Riscos:** tabela com muitas colunas no celular (mitigado pela lista de linhas); `sticky` encobrindo foco (verificar); fotos pesadas na grade (dimensões fixas e `loading="lazy"`).
- **Dependências:** nenhuma nova biblioteca.

## Alteração proposta no DESIGN.md (após aprovação)

- Em **Composição por superfície → Admin**: substituir "Não antecipar menus ou dashboards sem funcionalidade" por regra de moldura com menu lateral limitado a funções existentes (A1), cabeçalho de página (A2) e listas operacionais em tabela com alternativa de lista no celular (A3).
- Em **Componentes e estados**: incluir `PageHeader`, `StatusBadge` e navegação lateral, com estados padrão, hover, atual e foco.
- Em **Base visual da vitrine**: registrar foto de capa no card (V1), abertura compacta (V2), resumo fixo (V3) e variações por atributo (V4).
- Em **Referências**: registrar as fontes desta proposta com data.

## Decisões pendentes do responsável

1. Aprovar a moldura com navegação lateral no admin (A1)?
2. Aprovar lista de produtos em tabela e "Novo produto" em rota própria (A3, A4)?
3. Aprovar a abertura compacta da vitrine (V2)?
4. Aprovar variações por atributo (V4), aceitando que pode exigir ajuste na API pública?

Itens **P** podem ser implementados assim que houver tarefa de implementação autorizada.

## Execução

- **A1:** o item "Estoque" não entrou no menu porque não existe tela de estoque da loja inteira; o estoque continua acessível por produto (tabela e edição). Itens: Visão geral, Produtos, Minha conta.
- **V1:** a capa já existia; a mudança foi a grade em colunas mais estreitas com o card em coluna.
- **A3:** a tabela não tem miniatura de foto. A API do admin não expõe capa por produto na listagem; buscar as fotos de cada linha geraria uma requisição por produto. Fica como evolução quando houver capa na listagem.
- **A4:** após cadastrar, o admin abre a edição com `?criado=1` para exibir o aviso; recarregar a página exibe o aviso de novo.
- **V1 (correção):** a capa sem foto ficava cinza e sem texto quando o erro da imagem acontecia antes da hidratação; agora mostra "Imagem indisponível".
- **Lista no celular:** abaixo de 48rem a tabela usa `display: block` com rótulos `data-label`. Alguns leitores de tela deixam de anunciar a estrutura de tabela nesse modo; os dados continuam legíveis em ordem. Revisar com leitor de tela na QA.

### Verificação (2026-10-18)

- Executado: `typecheck` e `lint` dos dois apps; `build` e `test:smoke` dos dois apps antes dos ajustes finais de CSS e do `ref` da capa (smoke da vitrine ampliado para variações por atributo; smoke do admin inclui `/produtos/novo`).
- Navegador (servidores de desenvolvimento, backend local, conta e loja de teste `.test`): lista de produtos, visão geral, edição em duas colunas com lateral fixa, fluxo "Novo produto" até a edição com aviso e foco, menu no celular (abre, marca a página atual, fecha ao navegar), vitrine em 1440 e 375 CSS px sem rolagem horizontal, troca de variação por atributo atualizando preço e disponibilidade.
- Contraste sRGB dos pares novos: selo neutro 6,76:1; selo positivo 6,81:1; item atual do menu 7,7:1; borda da opção 8,33:1; opção marcada 7,7:1; foco sobre fundo selecionado 5,66:1.
- Não executado: Axe, leitor de tela, zoom 200% e teclado completo. Sem alegação de conformidade WCAG.
