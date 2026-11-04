# ADR-0018 — Vitrine padrão V1 (Sprint 8)

Status: **adotado** em 2026-10-27. Aprovado pelo responsável na mesma data: a vitrine V1 vira a **Sprint 8 e passa à frente da Sprint 7** ("para vender o produto, a vitrine é mais importante"); **tudo da V1 e a conta do cliente são gratuitos**. As decisões V1–V13 e a direção visual (V12) foram aprovadas pelo responsável na mesma data. Por enquanto só o planejamento fica registrado: cada tarefa da Sprint 8 exige autorização própria. Plano incremental: [SPRINT_08.md](../../product/SPRINT_08.md). Direção visual aprovada (2026-10-27), detalhada no [DESIGN.md](../../design/DESIGN.md#vitrine-padrão-v1-sprint-8): tipografia Manrope, base branca com texto quase preto `#111418`, destaque azul `#1F4FD8`, desconto em vermelho `#B3261E`, WhatsApp em verde `#127C3C`, cantos 10–16 px e botões em pílula; telas de home, categoria/busca, produto e home no celular. Contexto e benchmark: [VITRINE_REPLANEJAMENTO.md](../../product/VITRINE_REPLANEJAMENTO.md), [BENCHMARK_VITRINE_SPORTLIFE.md](../../product/BENCHMARK_VITRINE_SPORTLIFE.md).

## Contexto

- **Pedido do responsável (2026-10-27):** vitrine no padrão de uma loja grande, usando como referência funcional uma loja na Loja Integrada; depois, uma loja de extensões pagas no admin (V2/V3, fora deste ADR).
- **O que já existe:**
  - [ADR-0010](0010-public-storefront.md): publicação explícita de loja e produto, API pública só GET, 404 uniforme, sem custo/SKU/saldo exato, páginas com `no-store`;
  - [ADR-0011](0011-catalog-media-description-variants.md): imagens (JPEG/PNG até 4096 px), descrição HTML sanitizada, variantes com até 3 opções;
  - [ADR-0012](0012-stock-reservation-cart-orders.md) e [ADR-0013](0013-mercado-pago-checkout-pro.md): carrinho em cookie, pedido com reserva, Checkout Pro por preferência;
  - URLs `/lojas/{slug}` e `/lojas/{slug}/produtos/{uuid}`; catálogo paginado por cursor;
  - **categorias planas** (bloco 1c do MVP, V30, [validação](../../quality/MVP-1C-CATEGORIES.md)): `catalog_category` por loja, até 50, nome único na loja sem diferenciar maiúsculas, no máximo **uma por produto** (`catalog_product.category_id`), filtro na vitrine por `?categoria={id}`;
  - **dados da loja** (bloco 1b, V29 `store_profile`, [validação](../../quality/MVP-1B-STORE-PROFILE.md)): logo, e-mail, WhatsApp (E.164) e Instagram públicos por escolha, com o bloco "Fale com a loja" (`wa.me`, `mailto:`). Desde a [revisão de interface do MVP](../../quality/MVP-2-INTERFACE-REVIEW.md) (2026-10-31), o perfil público também traz o tamanho do logo (`logoWidth`, `logoHeight`), para a página reservar o espaço;
  - **páginas legais da plataforma** (bloco 1d): termos e privacidade do Smart Commerce no admin; a loja ainda não tem páginas próprias;
  - card de produto da vitrine portado do Dashtail (DESIGN.md);
  - cobrança do lojista pela Stripe ([ADR-0017 — Stripe](0017-saas-billing-stripe.md)), base para cobrar extensões no futuro.
- **Lacunas principais:** categorias sem hierarquia nem coleções, sem marca, sem busca textual, facetas ou ordenação, sem conteúdo editorial, páginas institucionais da loja, rodapé com razão social/CNPJ nem SEO estruturado.
- **Pagamentos:** a preferência do Checkout Pro não define parcelas; os juros do parcelamento dependem da configuração da conta Mercado Pago do lojista, que a plataforma não conhece. O valor da preferência é único, então um desconto só para Pix exigiria escolher o meio antes de criar a preferência.

## Decisões

| ID | Decisão | Recomendação | Alternativas | Bloqueia |
| --- | --- | --- | --- | --- |
| V1 | Categorias | **Evoluir a `catalog_category` existente (V30)**: árvore de até 3 níveis (`parent_id`), slug único por loja, ordem e publicação própria; a unicidade do nome passa a ser **por pai** (para permitir "Camisetas" em Feminino e em Masculino). O `category_id` atual vira a **categoria principal** (breadcrumb e relacionados) e o produto ganha **categorias adicionais** (`catalog_product_category`, N:N). Categorias existentes viram raiz, sem perda. O limite de 50 por loja continua. O filtro antigo `?categoria={id}` redireciona (308) para a rota com slug. "Outlet", "Promoções" e "Lançamentos manuais" são categorias comuns. Remover continua como hoje (os produtos ficam sem categoria, após confirmação); remover uma categoria com subcategorias é recusado; ocultar a categoria não oculta os produtos | Uma categoria por produto (não atende coleções de marketing); tags livres (sem árvore para o menu) | SC-041, SC-044 |
| V2 | Marca | **`catalog_brand` por loja** (nome, slug), produto com `brand_id` opcional. Sem logo de marca nesta sprint | Texto livre no produto (não dá filtro confiável) | SC-041 |
| V3 | Preço "de/por" | **`compare_at_price` opcional** no produto e na variante, obrigatoriamente maior que o preço. É só exibição: o valor cobrado continua sendo o preço. Percentual do selo calculado no backend e **arredondado para baixo** (nunca promete mais desconto que o real). Não é cupom nem promoção com prazo | Promoções com validade (vira extensão de cupons, V3) | SC-041, SC-042 |
| V4 | Parcelamento e Pix | **Parcelamento:** a loja configura o máximo de parcelas, enviado na preferência (`payment_methods.installments`); a vitrine mostra **"em até N× no Mercado Pago"**, **sem "sem juros"**, porque os juros dependem da conta do lojista. **Desconto no Pix fica fora** da Sprint 8 (candidato a extensão, com escolha do meio antes da preferência) | Mostrar "sem juros" por declaração do lojista (promessa que a plataforma não confere) | SC-042, SC-045 |
| V5 | URLs legíveis | Produto ganha **`slug` único por loja** (gerado do nome, editável). Novas rotas: `/lojas/{loja}/c/{categoria}`, `/lojas/{loja}/p/{produto}`, `/lojas/{loja}/busca?q=`. A rota antiga por UUID responde **308** para a nova. Slug antigo guardado por 90 dias para redirecionar quando editado | Manter UUID (pior para SEO e compartilhamento) | SC-041, SC-045 |
| V6 | Busca, filtros e ordenação | **PostgreSQL:** coluna `tsvector` gerada (configuração `portuguese` + `unaccent`) sobre nome, marca e categorias, com índice GIN; termo curto (< 3 letras) cai em prefixo. **Facetas com contagem:** categoria, marca, faixa de preço (faixas calculadas por loja) e opções de variante (tamanho, cor). **Ordenação:** relevância, mais recentes, mais vendidos, menor preço, maior preço, maior desconto. Paginação numerada, 24 itens. Facetas calculadas só sobre produtos publicados da loja resolvida pelo slug. Exige aprovar a extensão `unaccent` (contrib padrão do PostgreSQL) | Motor de busca externo (infraestrutura nova, fora do escopo); `ILIKE` puro (lento, sem relevância) | SC-042 |
| V7 | "Mais vendidos" | **Soma de unidades de pedidos pagos nos últimos 90 dias**, por produto, calculada no backend sem nenhum dado de comprador. Empate por mais recente. Loja sem vendas: o bloco não aparece | Ordem manual (dá trabalho ao lojista) | SC-042, SC-043 |
| V8 | Conteúdo da loja | No admin, **"Aparência e conteúdo"**: (a) até **5 banners** da home, cada um com imagem desktop e celular (pipeline de imagens do ADR-0011), texto alternativo obrigatório, link **só interno** (categoria, produto ou busca) e período opcional; (b) **seções da home** em ordem escolhida, de tipos fechados: lançamentos (mais recentes), mais vendidos, destaques (produtos marcados), categoria X; (c) **páginas institucionais** de tipos fechados (Sobre, Trocas e devoluções, Privacidade, Termos, Entrega), texto com o mesmo sanitizador da descrição; (d) **dados do rodapé**: estende o `store_profile` (que já tem logo, e-mail, WhatsApp e Instagram) com razão social, CNPJ (dígitos verificadores conferidos), telefone, endereço opcional e outras redes sociais (https, domínios de uma lista fechada) | Editor de página livre (blocos arbitrários; vira tema premium, V2) | SC-043 |
| V9 | Cache e revalidação | Páginas públicas passam de `no-store` para **cache com revalidação de 60 s** e invalidação por etiqueta da loja quando o lojista publica, oculta ou altera conteúdo (chamada interna autenticada do backend para a vitrine). Imagens públicas com cache de 1 dia e URL versionada. **Carrinho, checkout e pedido continuam `no-store`**; o checkout sempre revalida preço e estoque no backend. Ocultar passa a valer em até 60 s (altera o ADR-0010) | Manter `no-store` (lento em loja grande) | SC-047 |
| V10 | Acompanhar pedido | Página **"Acompanhar pedido"** com número do pedido + e-mail do comprador. Resposta uniforme para combinação inexistente, limite de tentativas por IP e por pedido, e só situação, itens e entrega na resposta (nada de telefone ou endereço completo) | Só o cookie atual (perde-se ao trocar de aparelho) | SC-046 |
| V11 | SEO | Título e descrição por página; `canonical`; Open Graph; **JSON-LD** `Product` (preço, moeda, disponibilidade por variante), `BreadcrumbList` e `Organization`; `sitemap.xml` e `robots.txt` por loja só com conteúdo publicado; loja em rascunho com `noindex` | Só metadados básicos | SC-047 |
| V12 | Direção visual | **Aprovada em 2026-10-27** (proposta visual D5, registrada no DESIGN.md). Princípio: nova base de tokens `--store-*` (cores, tipografia, raios, espaçamento) para a vitrine padrão, contraste AA, sem tocar `--sc-*` do admin. A V2 (temas) reaproveita esses mesmos tokens como contrato | — | SC-044 a SC-046 |
| V13 | Contato e redes | **WhatsApp por link `wa.me`** (o número já vem do `store_profile`), agora também como botão flutuante, com mensagem fixa e sem script de terceiros. Redes sociais como links simples. **Nenhum pixel ou script de rastreamento** na vitrine padrão (pixels serão extensão com consentimento) | Widget de chat de terceiros | SC-043, SC-044 |

**Conta do cliente:** gratuita (aprovado), mas **fora da Sprint 8**. Primeiro cadastro de comprador da plataforma (LGPD, recuperação de senha, sessões separadas das do admin) pede ADR próprio e entra na sprint seguinte de vitrine, junto com favoritos e histórico de pedidos.

Regras comuns: loja resolvida só pelo slug publicado; toda consulta pública repete o escopo de loja e produto publicados; 404 uniforme; nenhum custo, SKU interno, saldo exato ou dado de comprador na API pública; valores `NUMERIC`/`BigDecimal` em BRL; migrações aditivas (a partir de V33; conferir a última versão no `main` antes de criar).

## Consequências e riscos

- **Escopo:** é a maior mudança da vitrine desde o ADR-0010. Fatiado para que cada tarefa entregue algo publicável.
- **Ordem das sprints:** a Sprint 7 passa para depois desta. Por decisão do responsável (2026-10-27), **a Sprint 8 faz parte da entrega do MVP**, como exceção: a vitrine no padrão de loja grande não tinha entrado no [RELEASE_PLAN.md](../../product/RELEASE_PLAN.md) e é o que vende o produto.
- **Identificadores:** SC-031 a SC-033 já são usados tanto pela cobrança (Stripe) quanto pela Sprint 7; esta sprint usa SC-041 a SC-048, livres. Em 2026-11-01 a Sprint 7 foi renumerada para SC-049 a SC-058, e seu ADR passou a ser o ADR-0019. A SC-018 (taxa e liberação do Mercado Pago) continua pendente e não bloqueia a Sprint 8.
- **Multi-tenant:** categorias, marcas, banners e páginas são dados por loja; facetas e "mais vendidos" são agregações e precisam de testes A/B específicos (contagem de outra loja nunca aparece).
- **Cache (V9):** risco de mostrar conteúdo oculto por até 60 s e de vazar entre lojas se a chave de cache não incluir o slug. Teste de chave e de invalidação obrigatórios.
- **Conteúdo do lojista (V8):** texto institucional e banners são dados não confiáveis; sanitização no backend, links só internos, redes só em domínios da lista.
- **Busca (V6):** a extensão `unaccent` precisa existir no PostgreSQL de produção; revisar com `supabase-postgres-best-practices` antes da migração.
- **Promessa de preço (V3, V4):** nada exibido pode diferir do que o checkout cobra; testes comparam vitrine × cotação do carrinho × preferência.
- **Desempenho:** imagens responsivas (tamanhos gerados no upload ou no proxy) entram na SC-044; sem CDN paga.
- **Fora:** conta do cliente, favoritos, cupons, avise-me, newsletter, avaliações, pixels, frete por CEP, desconto no Pix, temas e loja de extensões.

## Implementação e desvios registrados

**SC-041 (2026-10-27, PR #55):** V1, V2, V3 e V5 implementadas conforme acima, exceto as **categorias adicionais por produto** (N:N de V1). Hoje cada produto tem só a categoria principal; coleções de marketing ("Outlet", "Promoções") funcionam como categorias comuns, mas um produto não pode estar ao mesmo tempo em "Camisetas" e em "Promoções". Ficou como pendência da Sprint 8 e foi resolvida na SC-043 (V37). Registros: [SC-041A](../../quality/SC-041A-CATEGORY-TREE.md) e [SC-041B](../../quality/SC-041B-BRANDS-SLUG-COMPARE-AT.md).

**SC-042 (2026-10-27):**
- **Rota nova, contrato antigo intacto.** O catálogo com busca, filtros e facetas é `GET /api/public/stores/{loja}/catalog`. A listagem original (`GET /api/public/stores/{loja}`, ADR-0010) continua campo a campo, para a vitrine atual não quebrar até a SC-045.
- **V4, parcelamento:** o limite fica em `store.max_installments` e é **opcional**. Vazio, a preferência não manda limite (vale o padrão do Mercado Pago, como antes) e a vitrine não promete parcelas. A API pública devolve só o número máximo, e somente com o Mercado Pago conectado. **Não devolve valor de parcela:** com juros definidos na conta do lojista, "6× de R$ X" poderia não ser o valor cobrado (CDC). O aceite da SC-042 que falava em "valor da parcela calculado no backend" foi trocado por isso.
- **V6, busca:** procura no nome do produto e no nome da marca (não no nome da categoria, que já é filtro e faceta). Termos são reduzidos a letras e números e viram prefixos (`camis` acha "Camiseta"); nada digitado altera a sintaxe da consulta.
- **V6, facetas:** cada faceta ignora o próprio filtro (marca, preço, categoria) para oferecer as outras escolhas. A faceta de opções de variante ignora **todos** os filtros de opção, não só o da mesma opção: com filtros de tamanho e cor ao mesmo tempo, as contagens de cor não consideram o tamanho escolhido. Simplificação aceita para o volume de uma loja pequena.
- **Categoria oculta:** some do menu, das facetas e da navegação por endereço (404), mas os produtos dela continuam no catálogo, como decidido em V1.
- **Paginação:** 24 por página, até a página 500.

**SC-044 e SC-045 (2026-10-27):**
- **Rota antiga do produto:** `/lojas/{loja}/produtos/{id}` **continua respondendo**, sem 308. A ação "adicionar ao carrinho" (server action) posta nessa rota, e redirecionar ali poderia quebrar o carrinho. Os cards e menus já levam a `/p/{endereço}`. A URL canônica para buscadores vem na SC-047.
- **Endereço antigo do produto:** redireciona para o atual, mas **dentro do HTML** (status 200 com `meta refresh`), porque a página é enviada por streaming atrás do `loading.tsx` global, como já acontece com as páginas 404. Um 308 de verdade, decidido antes do streaming, fica para a SC-047 (`proxy.ts`), junto com o cache.
- **Filtro antigo `?categoria={id}`:** a home nova ignora esse parâmetro. A loja ainda não foi lançada, então não há links antigos com ele a preservar.
- **Filtro inválido na URL:** a API responde 400 e a vitrine mostra a página "não encontrada", em vez de erro.

**SC-043 (2026-10-27):**
- **Categorias adicionais (pendência da SC-041):** feitas com a V37, até 10 por produto. A principal continua definindo o caminho e os relacionados.
- **Dados do rodapé fora do `store_profile`:** ficam em `store_company`, separada, porque o perfil tem versão e logo próprios ("Dados da loja"). WhatsApp, e-mail, Instagram e logo continuam onde estavam.
- **Redes sociais:** só Facebook, TikTok e YouTube, além do Instagram que já existia. São gravadas como nome de usuário e a vitrine monta o link para o domínio fixo, então nenhum endereço digitado vira link. X, LinkedIn e Pinterest ficam para quando houver pedido.
- **Banners:**
  - a imagem passa pelo mesmo normalizador das fotos de produto, com pelo menos 300 px no menor lado;
  - a de celular é opcional; sem ela, vale a principal;
  - banner sem imagem principal não aparece;
  - o período é gravado como instante; o admin converte a data digitada pelo fuso do navegador do lojista;
  - a troca automática acontece a cada 7 s, para com o ponteiro ou o foco dentro do carrossel, não acontece com `prefers-reduced-motion`. Desde 2026-11-04, a navegação é por bolinhas (uma por banner, a atual preenche durante os 7 s) e o botão "Pausar" foi removido a pedido do responsável; a pausa fica no ponteiro ou foco dentro do carrossel (DESIGN.md).
- **Seções:**
  - até 8, cada uma com até 8 produtos;
  - seção de categoria oculta não aparece, e seção sem produtos também não;
  - sem nenhuma seção configurada, a home mostra "Lançamentos", como antes;
  - o "Ver todos" dos destaques usa o filtro novo `destaque=1` da API do catálogo.
- **Destaque do produto:** marcado no cartão "Vitrine" da edição do produto, junto com marca, endereço e preço "de".
- **Resposta do catálogo:** banners e seções vêm em toda resposta de `/catalog`, como o menu e o rodapé. O custo extra fica para o cache da SC-047.

**SC-047 (2026-10-28):**
- **V9, o que fica em cache:**
  - em cache ficam as **leituras do backend** feitas pela vitrine: 60 s, com a etiqueta `store:{loja}`, e a URL (que contém a loja) como chave;
  - o **HTML** continua `no-store`, porque traz a contagem do carrinho do visitante;
  - carrinho, checkout, pedido e "Acompanhar pedido" continuam sem cache nenhum.
- **V9, invalidação:**
  - depois do commit, os serviços que mudam a vitrine chamam `POST /interno/revalidar` da vitrine, em segundo plano;
  - esses serviços são: produto, foto, cartão "Vitrine", categoria, marca, empresa, páginas, banners, seções, perfil da loja, entrega e publicação da loja;
  - a chamada é assinada com HMAC sobre a loja e o horário, com segredo próprio;
  - a vitrine descarta as leituras da loja sem servir conteúdo velho (`expire: 0`), então um produto ocultado some na próxima visita;
  - sem o segredo, a rota não existe e vale só o prazo de 60 s;
  - estoque, logo e parcelamento atualizam só pelo prazo de 60 s.
- **V9, imagens:**
  - o otimizador do Next gera os tamanhos, sem CDN paga;
  - a foto pelo id fica em cache por 1 dia (`immutable`), porque um envio novo é um id novo; o card passou a receber o id da capa (`imageId`);
  - `primary`, logo e banners, que mudam no mesmo endereço, ficam 60 s;
  - uma foto de produto ocultado continua acessível pelo endereço por até 1 dia.
- **V11, metadados:**
  - título próprio da loja e descrição por página;
  - `canonical` e Open Graph só com `STOREFRONT_PUBLIC_ORIGIN` configurada;
  - JSON-LD `Product` com uma `Offer` por variante, `BreadcrumbList` e `Organization`;
  - categoria filtrada e busca não são indexadas;
  - o ambiente de teste (`hosted-test`) inteiro é `noindex`, e o `robots.txt` dele bloqueia tudo.
- **V11, sitemap:** cada loja tem o seu em `/lojas/{loja}/sitemap.xml`, com até 5.000 produtos, categorias do menu e páginas escritas. Loja em rascunho dá 404. Não há índice de todas as lojas: o sitemap é enviado por loja.
- **308 real:** o `proxy.ts` pergunta ao backend (`GET …/p/{endereço}/address`) se o endereço mudou e responde 308 antes da página, preservando a query. Em caso de erro, a página segue e faz o redirecionamento dentro do HTML.
- **Rota original do produto** (`/produtos/{id}`): continua respondendo, com `canonical` para `/p/{endereço}`.

**SC-046 (2026-10-28):**
- **V10, limites:** por hora, 20 consultas por endereço, 10 por número de pedido com o mesmo e-mail e 20 por e-mail na loja. A tentativa conta mesmo quando o pedido não existe, então o limite não revela quais números existem.
  - **Ajuste da SC-048:** a versão inicial contava só o número. Com isso, quem soubesse o número podia bloquear o comprador por uma hora. Contar o número junto com o e-mail evita esse bloqueio. O limite por e-mail impede percorrer, de vários endereços, os números sequenciais de um comprador conhecido.
- **V10, sem pagamento:** a consulta por número e e-mail mostra situação, itens, entrega e valores, mas não o botão de pagar. Pagar continua exigindo o token do navegador da compra; a página orienta a usar esse navegador ou falar com a loja.
- **Rota só com checkout:** "Acompanhar pedido", e o atalho no cabeçalho, só existem onde o checkout está ligado (D6), como o checkout.
- **Pedido anonimizado:** sem e-mail guardado, nunca é encontrado.
- **Restyle:** o carrinho ganhou a foto de cada produto. O checkout usa cabeçalho reduzido, sem busca, menu ou carrinho, com o contato da loja (WhatsApp, telefone ou e-mail, nessa ordem) e o resumo lateral fixo no desktop.
