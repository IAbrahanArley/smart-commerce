# Sprint 8 — Vitrine padrão V1 (planejamento)

Data: 2026-10-27. **Prioridade aprovada pelo responsável em 2026-10-27: esta sprint passa à frente da [Sprint 7](SPRINT_07.md)**, porque a vitrine é o que vende o produto. Tudo desta sprint e a futura conta do cliente são gratuitos para o lojista. As decisões V1–V13 do [ADR-0018](../architecture/adr/0018-storefront-v1.md) e a direção visual (V12, registrada no [DESIGN.md](../design/DESIGN.md#vitrine-padrão-v1-sprint-8)) foram aprovadas em 2026-10-27. Só o planejamento fica registrado; cada tarefa exige autorização própria.

**Objetivo:** um comprador entra na loja e encontra o que quer como numa loja grande — menu de categorias, busca com filtros, página de produto completa, preço "de/por", parcelamento, páginas institucionais e rodapé confiável — e o lojista monta isso pelo admin, sem código. Referência funcional: [BENCHMARK_VITRINE_SPORTLIFE.md](BENCHMARK_VITRINE_SPORTLIFE.md).

**Fora desta sprint:** conta do cliente e favoritos (próxima sprint de vitrine, ADR próprio), cupons, avise-me, newsletter, avaliações, pixels, frete por CEP, desconto no Pix, temas e loja de extensões.

**Dependências:**
- Parte do que existe no `main` (blocos 1b e 1c do [RELEASE_PLAN.md](RELEASE_PLAN.md)): categorias planas, dados da loja com logo e contatos, card de produto do Dashtail. As tarefas abaixo evoluem isso, sem refazer.
- **Faz parte da entrega do MVP** (decisão do responsável em 2026-10-27, como exceção): entra como bloco 1f do [RELEASE_PLAN.md](RELEASE_PLAN.md), antes da entrega em produção.
- Nenhuma da Sprint 7. A SC-018 (taxa e liberação do Mercado Pago) continua pendente e não bloqueia.
- A validação real do Mercado Pago continua pendente; a SC-042 só acrescenta o máximo de parcelas à preferência.

## Gates de entrada

| Tarefa | Requer |
| --- | --- |
| SC-041 | V1, V2, V3, V5; revisão das migrações |
| SC-042 | SC-041; V3, V4, V6, V7 (inclui a extensão `unaccent`, aprovada com V6); confirmar que ela existe no PostgreSQL de produção |
| SC-043 | SC-041; V8, V13 |
| SC-044 | V12; SC-041 para o menu |
| SC-045 | SC-042, SC-044 |
| SC-046 | SC-044; V10 |
| SC-047 | SC-045; V9, V11 |
| SC-048 | SC-041 a SC-047 |

SC-041 e SC-044 podem andar em paralelo (backend × base visual). SC-043 e SC-042 também, depois da SC-041.

## SC-041 — Categorias, marcas, slug e preço "de"

**Estado (2026-10-27):** implementada e mesclada (PR #55), exceto as categorias adicionais por produto, pendentes (ver "Implementação e desvios" no ADR-0018). Validação: [SC-041A](../quality/SC-041A-CATEGORY-TREE.md), [SC-041B](../quality/SC-041B-BRANDS-SLUG-COMPARE-AT.md).

- **Migrações aditivas (V33+):** a `catalog_category` existente (V30) ganha `parent_id` (até 3 níveis), slug único por loja, ordem e publicação, com unicidade do nome por pai; as categorias atuais viram raiz. O `category_id` do produto passa a ser a categoria principal e entra `catalog_product_category` (categorias adicionais, N:N); `catalog_brand`, `catalog_product.brand_id`, `slug` do produto (único por loja, preenchido a partir do nome para os existentes), `compare_at_price` em produto e variante (maior que o preço), histórico de slugs para redirecionamento.
- **Admin (OWNER):** a tela "Categorias" existente ganha subcategorias, mover, ordenar e ocultar (remover continua deixando os produtos sem categoria, com confirmação; categoria com subcategorias não pode ser removida); nova tela "Marcas"; o cartão "Categoria" do editor ganha categorias adicionais, e o produto ganha marca, slug editável e preço "de".
- O filtro antigo `?categoria={id}` da vitrine redireciona (308) para a rota com slug.
- Ocultar categoria não oculta produto; categoria oculta some do menu e da navegação.

**Aceite:** loja existente migra sem mudar preço, publicação, estoque ou categoria escolhida; produto sem categoria continua visível na vitrine; árvore com 4 níveis é recusada; `compare_at_price` menor ou igual ao preço é recusado.

**Testes:** migração sobre base com produtos e variantes; ciclo na árvore (mover pai para dentro do filho) recusado; slug duplicado; CAS de versão; categoria/marca de outra loja no produto recusada; A/B.

## SC-042 — API pública de navegação

**Estado (2026-10-27):** implementada em rota nova (`/catalog`), com a listagem antiga intacta. O parcelamento devolve só o número máximo de parcelas, sem valor de parcela (desvio registrado no ADR-0018). Validação: [SC-042](../quality/SC-042-PUBLIC-CATALOG-API.md).

- `GET /api/public/stores/{slug}` passa a trazer menu (categorias publicadas), dados do rodapé e configuração de parcelas.
- **Listagem** por categoria (inclui subcategorias) e por busca: facetas com contagem (categoria, marca, faixa de preço, opções de variante), ordenação (relevância, recentes, mais vendidos, menor/maior preço, maior desconto), paginação numerada de 24.
- **Busca:** `tsvector` gerado com `portuguese` + `unaccent`, índice GIN; prefixo para termos curtos; limite de tamanho do termo.
- **Produto público** ganha slug, marca, categoria principal com caminho (breadcrumb), preço "de" e percentual (arredondado para baixo), e **relacionados** (mesma categoria principal, publicados, disponíveis, até 8).
- **Mais vendidos:** unidades de pedidos pagos em 90 dias, sem dado de comprador.
- **Parcelas:** a loja configura o máximo (1–12) no admin; o valor vai para `payment_methods.installments` da preferência; a API pública devolve o máximo e o valor da parcela calculado no backend.
- Continua sem custo, SKU interno, saldo exato ou IDs internos da loja.

**Aceite:** facetas e totais batem com a listagem; produto oculto, de outra loja ou em categoria oculta não aparece em contagem nenhuma; a parcela exibida é a mesma usada na preferência.

**Testes:** acentos e maiúsculas na busca ("oculos" acha "Óculos"); termo com caracteres especiais; facetas com filtros combinados; ordenação estável na paginação; preferência com `installments`; plano de execução das consultas com dados volumosos; A/B em contagens e "mais vendidos".

## SC-043 — Aparência e conteúdo da loja (admin)

**Estado (2026-10-27):** implementada, com as categorias adicionais por produto que faltavam da SC-041. Diferenças em relação ao texto abaixo: os dados do rodapé ficam numa tabela própria, e as redes são Facebook, TikTok e YouTube, gravadas como nome de usuário (desvios no ADR-0018). Validação: [SC-043](../quality/SC-043-STORE-CONTENT.md).

- Nova área **"Aparência e conteúdo"** no admin (OWNER):
  - **Banners:** até 5, imagem desktop e celular, texto alternativo obrigatório, link interno (categoria, produto, busca), período opcional, ordem.
  - **Seções da home:** tipos fechados (lançamentos, mais vendidos, destaques, categoria X), ordem e título; marcação de produtos em destaque.
  - **Páginas institucionais:** Sobre, Trocas e devoluções, Privacidade, Termos, Entrega; texto sanitizado no backend.
  - **Rodapé e contato:** o `store_profile` (logo, e-mail, WhatsApp, Instagram, já na tela "Dados da loja") ganha razão social, CNPJ (dígitos verificadores), telefone, endereço opcional e redes sociais (https em domínios da lista: Instagram, Facebook, TikTok, YouTube, X, LinkedIn, Pinterest).
- API pública devolve só o conteúdo ativo e publicado.

**Aceite:** banner fora do período não aparece; link externo em banner é recusado; HTML com script ou atributo de evento sai limpo; CNPJ inválido recusado.

**Testes:** sanitização (script, `javascript:`, `on*`, `iframe`); upload de banner inválido; período no fuso de São Paulo; limite de banners; A/B.

## SC-044 — Base visual e estrutura global da vitrine

**Estado (2026-10-27):** implementada junto com a SC-045, na mesma branch, para a busca do cabeçalho não apontar para uma página inexistente. Validação: [SC-044/045](../quality/SC-044-045-STOREFRONT-PAGES.md).

- **Tokens `--store-*` novos** conforme a proposta visual aprovada (cores, tipografia, raios, espaçamento, sombras), com contraste AA; `DESIGN.md` atualizado na mesma mudança. Nada muda em `--sc-*`.
- **Cabeçalho:** logo/nome, busca, atalhos (Acompanhar pedido, carrinho com quantidade), faixa de aviso opcional.
- **Menu:** categorias em até 3 níveis; no desktop, painel ao passar/focar com teclado; no celular, gaveta com navegação por níveis.
- **Rodapé:** categorias, páginas institucionais, contato, redes, formas de pagamento aceitas (texto), razão social e CNPJ.
- **WhatsApp:** o link `wa.me` do "Fale com a loja" passa a ter também um botão flutuante, sem script de terceiros, sem cobrir o conteúdo no celular.

**Aceite:** menu inteiro operável por teclado e leitor de tela; sem rolagem horizontal em 320 CSS px; aviso de ambiente de teste preservado.

**Testes:** smoke SSR com menu, rodapé e loja sem conteúdo configurado (fallback); inspeção manual em 1280/768/375 px.

## SC-045 — Home, categoria/busca e produto

**Estado (2026-10-27):** implementada; banners e seções configuráveis da home entraram com a SC-043. A rota antiga do produto continua respondendo em vez de redirecionar, e o endereço antigo redireciona dentro do HTML, não com 308 (desvios no ADR-0018). Validação: [SC-044/045](../quality/SC-044-045-STOREFRONT-PAGES.md).

- **Home:** carrossel de banners (pausa ao focar, sem rotação automática com `prefers-reduced-motion`), seções configuradas, cada uma com até 8 produtos e "Ver todos".
- **Card de produto:** foto, marca, nome, preço "de/por", selo de desconto, "em até N× no Mercado Pago", indisponível marcado.
- **Categoria e busca:** título, breadcrumb, filtros (barra lateral no desktop, painel no celular), ordenação, contagem de resultados, paginação; filtros na URL (compartilhável).
- **Produto:** galeria com miniaturas e zoom, marca, variantes em botões com esgotado marcado, preço "de/por", parcelamento, quantidade, adicionar ao carrinho, descrição, relacionados; rota antiga por UUID redireciona (308).

**Aceite:** nada exibido difere da cotação do carrinho; busca sem resultados sugere categorias; produto sem foto, sem marca e sem categoria continua com layout íntegro.

**Testes:** smoke SSR das três páginas; redirecionamento 308; filtros pela URL; 404 uniforme para categoria oculta.

## SC-046 — Carrinho, checkout e acompanhar pedido

- Carrinho e checkout na nova base visual; checkout com cabeçalho reduzido (sem menu), contato visível e resumo lateral.
- **Acompanhar pedido:** número do pedido + e-mail; resposta uniforme; limite de tentativas por IP e por pedido; mostra situação, itens e entrega, sem telefone ou endereço completo.

**Aceite:** fluxo do carrinho ao pagamento continua igual em regras (reserva, idempotência, Mercado Pago); combinação errada e inexistente são indistinguíveis.

**Testes:** limite de tentativas; e-mail com maiúsculas/espaços; pedido de outra loja; smoke do fluxo completo.

## SC-047 — SEO, cache e imagens

- Metadados por página, `canonical`, Open Graph, JSON-LD (`Product`, `BreadcrumbList`, `Organization`), `sitemap.xml` e `robots.txt` por loja; rascunho com `noindex`.
- Cache das páginas públicas com revalidação de 60 s e invalidação por etiqueta da loja ao publicar, ocultar ou editar conteúdo; carrinho, checkout e pedido continuam `no-store`.
- Imagens públicas em tamanhos responsivos, cache de 1 dia com URL versionada.
- ADR-0010 atualizado (fim do `no-store` nas páginas de navegação).

**Aceite:** ocultar produto some da vitrine em até 60 s e do sitemap na próxima geração; chave de cache sempre inclui a loja; JSON-LD válido no validador de dados estruturados.

**Testes:** invalidação após publicar/ocultar; duas lojas com o mesmo caminho relativo não compartilham cache; sitemap só com publicados; chamada interna de invalidação sem credencial recusada.

## SC-048 — Revisão integrada

- Revisão de segurança multi-tenant da API pública nova (facetas, busca, mais vendidos, conteúdo, acompanhar pedido, cache).
- Revisão de UI/UX com `web-design-guidelines` e acessibilidade (teclado, leitor de tela, zoom 200%, contraste) nas páginas novas.
- Medição de desempenho (Lighthouse local) da home, categoria e produto, sem metas inventadas: registrar o resultado.
- Atualizar ARCHITECTURE, DATA_MODEL, BUSINESS_RULES, DESIGN e README.

## Próximos passos fora desta sprint

- **Conta do cliente** (gratuita): cadastro, login, histórico de pedidos e favoritos, com ADR próprio.
- **Sprint 7** (caixa, contas, venda presencial), já planejada.
- **Loja de extensões** (V2/V3 do [replanejamento](VITRINE_REPLANEJAMENTO.md)): depende de decidir a cobrança da plataforma (D2).
