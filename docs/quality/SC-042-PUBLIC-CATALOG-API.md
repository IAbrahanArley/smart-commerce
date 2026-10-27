# SC-042 — API pública de navegação: validação

Data: 2026-10-27. Base: SC-042 da [SPRINT_08.md](../product/SPRINT_08.md) e decisões V3, V4, V6 e V7 do [ADR-0018](../architecture/adr/0018-storefront-v1.md). Desvios e simplificações estão em "Implementação e desvios registrados" no ADR.

## Comportamento

**Catálogo, `GET /api/public/stores/{loja}/catalog`** (público, só GET, `no-store`):
- **Parâmetros:**
  - `categoria` (endereço; inclui as subcategorias);
  - `marca` (repetível ou separada por vírgula);
  - `preco_min` e `preco_max`;
  - `opcao` ("Tamanho:M", repetível; valores do mesmo nome somam, nomes diferentes filtram juntos);
  - `q` (busca);
  - `ordem`: `relevancia`, `recentes`, `mais-vendidos`, `menor-preco`, `maior-preco`, `maior-desconto`;
  - `pagina`: 24 por página, até 500.
- **Validação antes de qualquer SQL:** endereços no formato de slug, valores com até 2 casas, opções curtas de uma linha, busca de até 100 caracteres, no máximo 20 marcas e 20 opções. Qualquer outra coisa: 400.
- **Busca:** sem acento e sem diferenciar maiúsculas ("oculos" acha "Óculos"), por prefixo ("camis" acha "Camiseta"), no nome do produto e no nome da marca. O texto digitado vira só letras e números, então não altera a consulta. Com busca, a ordem padrão é a relevância; sem busca, os mais recentes.
- **Resposta:**
  - a loja;
  - o menu: árvore de categorias publicadas com produtos, já sem as ocultas e as de pai oculto;
  - a categoria atual com o caminho;
  - o total e os cards: endereço, nome, marca, menor preço, preço "de" e percentual arredondado para baixo, disponibilidade;
  - as facetas:
    - subcategorias da categoria atual, somando os produtos das filhas;
    - marcas;
    - faixas de preço com limites fixos (50, 100, 200, 300, 500, 1.000, 2.000 e 5.000);
    - opções de variante;
  - `maxInstallments`.
- **Mais vendidos:** unidades de pedidos pagos nos últimos 90 dias, sem nenhum dado de comprador. Empate pelos mais recentes; toda ordem termina pelo id, então a paginação não repete produto.
- **Isolamento:** toda consulta parte da loja publicada resolvida pelo endereço. Produto, categoria, marca ou venda de outra loja nunca entram em contagem. Categoria oculta, de pai oculto ou inexistente: 404.

**Produto:**
- `GET /api/public/stores/{loja}/products/{id}` ganhou: endereço, marca, caminho de categorias (vazio se alguma categoria do caminho estiver oculta), preço "de" e percentual do produto (pela variante mais barata) e de cada variante (só quando maior que o preço), até 8 relacionados (mesma categoria principal, disponíveis, mais vendidos primeiro) e `maxInstallments`.
- `GET /api/public/stores/{loja}/p/{endereço}` devolve o produto pelo endereço. Para um endereço antigo de até 90 dias, devolve `movedTo` com o endereço atual, para a vitrine redirecionar (SC-045). Nos demais casos: 404.
- **Contrato antigo:** a listagem `GET /api/public/stores/{loja}` (ADR-0010) não mudou nenhum campo. O detalhe ganhou 5 campos e `related`; o teste de isolamento confere a lista exata de campos públicos.

**Parcelamento:**
- `GET`/`PUT /api/tenants/{id}/payments/installments`: só o OWNER, CSRF na escrita, inteiro de 1 a 12 ou `null`. Outra loja: 404.
- **Admin:** cartão "Parcelamento" na tela Pagamentos, com "Padrão do Mercado Pago (não mostrar na vitrine)" e de "somente à vista" a "em até 12×". O texto explica por que a vitrine não promete "sem juros".
- **Checkout:** com o limite definido, a preferência do Checkout Pro leva `payment_methods.installments`. Sem limite, nada muda em relação a antes.
- **Vitrine:** `maxInstallments` só aparece com o Mercado Pago conectado.

## Testes e verificação

| Verificação | Resultado |
| --- | --- |
| `CatalogQueryTest` (4, unitário) | busca vira prefixos sem acento e sem sintaxe; relevância só com busca; filtros normalizados (marcas sem repetição, decimal com vírgula, opções agrupadas); limites e formatos inválidos; árvore pública esconde subárvore de pai oculto; caminho e total somado |
| `PreferenceInstallmentsTest` (1, unitário) | limite enviado só quando definido; 0 e 13 recusados; a assinatura antiga do cliente continua sem limite |
| `PublicCatalogIT` (7, PostgreSQL real, duas lojas com nomes e endereços iguais) | <ul><li>busca sem acento, por marca e por prefixo; tentativa de injeção na busca vira texto</li><li>facetas de marca, categoria, opção e preço</li><li>filtros por subárvore, marca, faixa de preço e opção; caminho da categoria; categoria oculta e inexistente 404; 8 parâmetros inválidos 400</li><li>as 6 ordens, inclusive "mais vendidos" sem contar a venda da outra loja; 29 produtos em 2 páginas sem repetição</li><li>detalhe com marca, caminho, preço "de" e percentual, relacionados da mesma categoria; caminho vazio em categoria oculta; preço "de" igual ao preço não aparece</li><li>busca por endereço, endereço antigo com `movedTo`, esquecido após 90 dias</li><li>parcelamento só com conta conectada</li><li>loja não publicada: 404</li></ul> |
| `StorefrontIsolationIT`, `CheckoutPaymentIT`, `ProductStorefrontIT` | lista de campos públicos explícita e atualizada; preferência com `installments` quando a loja define; rota do admin com dono, outra loja (404), valores inválidos (400) e CSRF |
| `mvnw verify` completo (JDK 21, 2026-10-27) | formatação, 112 unitários e 183 de integração passando (BUILD SUCCESS). O 1 ignorado é a avaliação paga da SC-029, opcional. Na primeira rodada, o teste de migração da V9/V10 (`CatalogIsolationIT`) copiava `store` com `SELECT *` para um esquema migrado só até a V8 e quebrou com a coluna nova; passou a listar as colunas da V8 |
| Admin: `lint`, `typecheck`, `build`, `test:smoke` | aprovados; o smoke cobre a leitura do limite e o texto das opções |
| Vitrine atual: `lint`, `typecheck`, `build`, `test:smoke` | aprovados, sem mudança de código: a listagem antiga continua igual e os campos novos do detalhe são ignorados pelo parser |

Corrigido durante os testes: a leitura do limite de parcelas usava `findFirst()`, que não aceita valor nulo. Com o padrão (nulo), o checkout respondia erro; o `CheckoutPaymentIT` pegou o problema antes do commit.

## Limitações

- Nenhuma tela da vitrine usa a rota nova ainda; isso é a SC-045.
- O desempenho foi medido só com os dados dos testes (dezenas de produtos). O plano de execução com catálogo grande fica para a SC-048.
- A extensão `unaccent` precisa existir no PostgreSQL de produção. Ela é contrib confiável, criada pelo dono do banco, mas a confirmação no ambiente hospedado é pendência antes do deploy.
