# SC-047 — SEO, cache e imagens: validação

Data: 2026-10-28. Base: SC-047 da [SPRINT_08.md](../product/SPRINT_08.md), decisões V9 e V11 do [ADR-0018](../architecture/adr/0018-storefront-v1.md) e o [ADR-0010](../architecture/adr/0010-public-storefront.md), que foi alterado. A branch parte da SC-046. Antes de mudar código, a documentação do Next 16.4 instalada foi lida: cache sem Cache Components, `revalidateTag`, `proxy.ts`, metadados e imagens. Os desvios estão no ADR-0018, em "Implementação e desvios registrados".

## O que mudou

### Cache (V9)

- **Leituras em cache:**
  - catálogo, página, produto e sitemap ficam 60 s em cache no servidor da vitrine, com a etiqueta `store:{loja}`;
  - a chave é a URL, que sempre contém a loja;
  - as páginas de navegação deixaram `force-dynamic`, que forçaria `no-store` em todo `fetch`.
- **Sem cache, como antes:**
  - o HTML continua `no-store`, porque traz a contagem do carrinho;
  - carrinho, checkout, pedido e "Acompanhar pedido" continuam sem cache.
- **Invalidação no backend:**
  - 33 métodos de serviço chamam `StorefrontChanges`, e o aviso só sai depois do commit (`@TransactionalEventListener(AFTER_COMMIT)`);
  - esses métodos cobrem produto, foto, cartão "Vitrine", categoria, marca, empresa, páginas, banners, seções, perfil, entrega e publicação da loja;
  - um cliente HTTP faz `POST /interno/revalidar` em segundo plano (thread virtual, 2 s de timeout), com HMAC-SHA256 sobre loja e horário.
- **Configuração:**
  - backend: `STOREFRONT_REVALIDATION_URL` e `STOREFRONT_REVALIDATION_SECRET`;
  - vitrine: `STOREFRONT_REVALIDATION_SECRET`.
- **Na vitrine:**
  - a rota confere a assinatura em tempo constante e aceita só horário dentro de 60 s e loja com endereço válido;
  - depois chama `revalidateTag(tag, { expire: 0 })`;
  - sem segredo configurado, a rota responde 404.

### Imagens

- **Otimização:** o otimizador do Next gera os tamanhos (`sizes` por posição: card, galeria, miniatura, carrinho), só para `/midia/lojas/**` e com qualidade fixa de 75.
- **Cache:**
  - a foto pelo id fica `public, max-age=86400, immutable`;
  - `primary`, logo e banner ficam `public, max-age=60`;
  - o card recebe `imageId` da capa, então a URL muda quando a foto muda.

### SEO (V11)

- **Título e descrição:** cada página tem o próprio título, com o nome da loja e sem o sufixo da plataforma, e a própria descrição (texto da descrição do produto, faixa de aviso ou categorias).
- **Canonical:**
  - categoria filtrada aponta para a categoria pura;
  - a rota original `/produtos/{id}` aponta para `/p/{endereço}`.
- **Open Graph:** com foto do produto ou logo.
- **JSON-LD:**
  - `Product`, com uma `Offer` por variante (preço, moeda, disponibilidade) e marca;
  - `BreadcrumbList` no produto e na categoria;
  - `Organization` na home, com razão social, telefone, e-mail, logo e redes.
- **Indexação:**
  - busca e categoria filtrada são `noindex, follow`;
  - o ambiente de teste inteiro é `noindex`, e o `robots.txt` dele bloqueia tudo.
- **`/lojas/{loja}/sitemap.xml`:** home, categorias do menu, páginas escritas e até 5.000 produtos publicados com `lastmod`; loja em rascunho dá 404.
- **`robots.txt` em produção:** libera `/lojas/` e bloqueia carrinho, checkout, pedidos, "Acompanhar pedido", busca e `/interno/`.
- **Origem pública:** sem `STOREFRONT_PUBLIC_ORIGIN`, ficam de fora `canonical`, Open Graph e JSON-LD, em vez de apontar para um host adivinhado.

### 308 real

`proxy.ts` pergunta ao backend `GET /api/public/stores/{loja}/p/{endereço}/address`. Se o endereço mudou, responde 308 antes de a página começar a ser enviada, preservando a query. Em erro ou timeout, a página segue e faz o redirecionamento dentro do HTML.

## Testes e verificação

| Verificação | Resultado |
| --- | --- |
| `StorefrontSeoIT` (3, novo) | aprovado. Cobre: <ul><li>sitemap só com produtos publicados com endereço, categorias do menu (não a oculta nem a vazia) e páginas escritas; loja em rascunho dá 404</li><li>endereço atual, antigo e desconhecido; outra loja e endereço inválido dão 404</li><li>mudança confirmada (ocultar categoria, despublicar loja) chega à vitrine sintética com corpo, horário e assinatura HMAC corretos</li><li>operações revertidas (tenant alheio, categoria inexistente, loja de outro dono) não enviam nada</li></ul> |
| `PublicCatalogIT` (8), `ArchitectureTest` | aprovados com o `imageId` no card e o novo pacote `storefront/client` |
| Backend `verify` completo | aprovado: formatação, 115 testes de unidade e 194 de integração (1 ignorado, como antes), BUILD SUCCESS |
| Vitrine: `lint`, `typecheck`, `build` | aprovados; `proxy.ts`, `/interno/revalidar`, `sitemap.xml` e `robots.txt` aparecem no build |
| Vitrine: `test:smoke` | aprovado. Cobre: <ul><li>título, `canonical`, `og:image`, `noindex` no ambiente de teste, JSON-LD `Product` (preço, moeda, disponibilidade, marca, descrição, sem campos privados) e `BreadcrumbList`</li><li>home com `Organization`; categoria filtrada com `canonical` da categoria pura; busca `noindex`</li><li>sitemap com endereços absolutos, `lastmod` e entrada malformada descartada; loja inexistente dá 404; `robots.txt` do ambiente de teste fechado</li><li>revalidação recusada sem assinatura, com segredo errado, com horário vencido e com assinatura de outra loja; loja inválida dá 400</li><li>cache: depois da invalidação, a loja é lida uma vez e as visitas seguintes vêm do cache; outra loja tem entrada própria; nova invalidação força nova leitura</li><li>308 com query preservada</li><li>foto otimizada com `srcset`; id com cache de 1 dia e `primary` com 60 s</li><li>fora do ambiente de teste: `index, follow`, sem `canonical` sem origem pública, `robots.txt` com as regras e sem rota de revalidação</li><li>unidades: texto da descrição, escape do JSON-LD, origem, sitemap, ofertas por variante, assinatura</li></ul> |

Corrigido durante a verificação:
- **Texto da descrição:** saía "segura ." por causa da tag em linha; agora só tags de bloco separam palavras.
- **Rota do sitemap:** estava sendo pré-gerada no build; agora é dinâmica.

## Limitações e pendências

- **Validador do Google:** o JSON-LD não passou pelo validador de dados estruturados, que é externo e exige a página publicada. A estrutura foi conferida pelo smoke. Fica para a SC-048 ou para depois do deploy.
- **Fora da invalidação:** estoque, logo e parcelamento do Mercado Pago atualizam só pelo prazo de 60 s.
- **Foto de produto ocultado:** continua acessível pelo endereço com id por até 1 dia (V9).
- **Sitemap:** não há índice de todas as lojas; cada loja envia o seu.
- **Custo do 308:** cada visita de produto faz uma consulta curta ao backend no `proxy.ts`.
- **Configuração no deploy:** `STOREFRONT_PUBLIC_ORIGIN` e os segredos de revalidação precisam ser configurados (documentado em `HOSTED-TEST-ENVIRONMENT.md` e `.env.example`).
