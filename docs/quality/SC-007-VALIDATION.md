# SC-007 — Carrinho na vitrine e cotação: validação

Data: 2026-10-17. Branch `claude/sc007-cart-quote-f2cc79` (worktree isolada). Base: [ADR-0012](../architecture/adr/0012-stock-reservation-cart-orders.md) §2 e §7, D5 aprovada; plano em [SPRINT_03.md](../product/SPRINT_03.md).

## Entrega

**Backend (módulo storefront, classes novas):**

- `POST /api/public/stores/{slug}/cart/quote` — `CartQuoteController` → `CartQuoteService` → `CartQuoteRepository`; DTOs `CartQuoteRequest`, `CartQuoteView`, `CartQuoteLineView`; domínio `CartLine`, `CartLineStatus`, `QuotedVariant`; erros em `CartQuoteErrorHandler` (escopo apenas deste controller).
- Sem efeito colateral: transação `readOnly`/`REPEATABLE_READ`, só `SELECT`, sem lock nem reserva.
- Entrada estrita: `Content-Type: application/json`; leitura limitada a 16 KiB (413 acima disso, sem bufferizar o excedente); objeto com exatamente `items`; 1–20 linhas; cada linha com exatamente `variantId` (UUID canônico) e `quantity` inteiro 1–10; chaves duplicadas, tokens extras, campos extras (inclusive `price`) e variantes repetidas → 400 genérico.
- Tenant derivado do slug `PUBLISHED`; cada variante é revalidada na consulta (loja publicada + produto publicado + variante do mesmo tenant). Slug em rascunho, inexistente ou inválido → 404 uniforme.
- Resposta: `currency`, `lines[]` (`variantId`, `quantity` pedida, `status`, `productId`, `productName`, `options`, `unitPrice`, `lineTotal`), `subtotal` (somente linhas `AVAILABLE`) e `purchasable` (todas `AVAILABLE`). Linha `UNAVAILABLE` devolve só seletor, quantidade e estado, com os demais campos nulos, para qualquer causa (outra loja, despublicada, inexistente, sem saldo). Sem custo, SKU, saldo, tenant ou store IDs. Dinheiro em `BigDecimal`, string com 2 casas, multiplicação exata.
- **Ponto de troca da disponibilidade (SC-005):** `CartQuoteRepository.SELLABLE_QUANTITY` (hoje `COALESCE(b.quantity,0)`, saldo físico) é a única expressão de quantidade vendável; `CartLineStatus.of(sellable, requested)` é a única comparação (`≥ pedido` → AVAILABLE; `> 0` → INSUFFICIENT; senão UNAVAILABLE). A integração com SC-005 troca a expressão pelo disponível com reservas.
- **Ponto de extensão de entrega (SC-006):** documentado em `CartQuoteService`/`CartQuoteView`; a cotação **não** inclui opções de entrega nesta entrega.
- `BootstrapSecurityConfiguration`: a cadeia pública ganhou apenas `POST /api/public/stores/*/cart/quote` (3 linhas + comentário). Continua `STATELESS`, `NullSecurityContextRepository`, sem cookies do backend e sem CSRF (sem autoridade ambiente). Nenhuma outra rota mudou.
- `StorefrontRepository`, `StorefrontService` e views públicas existentes não foram alterados (o serviço novo apenas reutiliza `StorefrontRepository.store`).

**storefront-web:**

- Cookie `sc-cart-{slug}`: `HttpOnly`, `SameSite=Lax`, `Secure` fora de `NODE_ENV=development`, `Path=/lojas/{slug}`, 7 dias, conteúdo `[{variantId, quantity}]` (< 4 KiB garantido em `serializeCart`). O slug no nome e o caminho isolam o carrinho por loja.
- `lib/cart-cookie.ts`: parse saneador — cookie > 4096, JSON inválido, não-array ou > 20 linhas → carrinho vazio; linha com UUID inválido, quantidade fora de 1–10/não inteira ou duplicada → descartada; campos extras (ex.: `price`) ignorados. Nunca lança.
- `lib/cart-actions.ts` (server actions `addToCart`, `updateCartItem`, `removeCartItem`): mesma origem, verificação de origem do Next, validação de slug/UUID/quantidade, limite de 20 linhas (aviso) e teto de 10 por linha; redirecionam ao carrinho.
- `lib/cart-quote.ts`: chamada SSR da cotação com `credentials: "omit"`, `redirect: "error"`, timeout 5 s, somente `Accept`/`Content-Type`; validação estrita e projeção somente dos campos públicos; 404 → página de ausência.
- Página `/lojas/[slug]/carrinho` (SSR): estado vazio, linhas com nome, opções, preço unitário, total da linha, estado (Disponível / Quantidade indisponível / Indisponível no momento), atualização de quantidade, remoção, subtotal dos itens disponíveis e aviso de que entrega, retirada e finalização não estão disponíveis. Sem checkout nem botão de compra (SC-009/D6).
- Página do produto: quantidade 1–10 e "Adicionar ao carrinho" com a combinação selecionada (desabilitado se indisponível). Cabeçalho ganhou o link "Carrinho". `formatBRL` aceita até 20 dígitos inteiros (totais de carrinho).
- Estilos apenas com tokens `--store-*` existentes, alvos ≥ 44 px, foco visível, `role="status"` para avisos, rótulos específicos nos botões de cada linha. Nenhuma dependência nova.

## Comandos e resultados

Ambiente: Windows 11, JDK Corretto 21.0.9 (`JAVA_HOME`), Docker para Testcontainers, Node 24.12.0, npm 11.21.0. Servidores locais em 8080/3000/3001 não foram tocados.

| Comando | Resultado |
| --- | --- |
| `backend: mvnw.cmd -B -ntp verify` | BUILD SUCCESS: Spotless ok; 33 testes de unidade (inclui ArchitectureTest e BootstrapSecurityTest) e 55 de integração, 0 falhas (CartQuoteIT 3/3, StorefrontIsolationIT 4/4) |
| `storefront-web: npm ci` | 354 pacotes do lockfile, sem alteração de `package*.json` |
| `storefront-web: npm run lint` | sem erros/avisos |
| `storefront-web: npm run typecheck` | sem erros |
| `storefront-web: npm run build` | sucesso; rota nova `ƒ /lojas/[slug]/carrinho` |
| `storefront-web: SMOKE_PORT=3012 npm run test:smoke` | aprovado |

## Cobertura dos testes exigidos

`backend/src/test/java/com/smartcommerce/storefront/CartQuoteIT.java` (PostgreSQL/Testcontainers):

- Preço sempre do catálogo (15,90 × 3 = 47,70; 20,00 × 5 = 100,00), subtotal só de `AVAILABLE`, `purchasable`; conjunto exato de campos na resposta e por linha; ausência de SKU, custo, saldo exato, tenant/store; `no-store`; sem `Set-Cookie` mesmo com cookie de sessão enviado.
- **A/B:** variante da loja B cotada no slug A → `UNAVAILABLE` com JSON idêntico (exceto o seletor) ao de variante inexistente e de produto despublicado; a mesma variante é `AVAILABLE` no slug B; slug em rascunho, inexistente, maiúsculo ou curto → 404; despublicar o produto torna a linha `UNAVAILABLE` na cotação seguinte.
- Adulteração → 400 sem 500 nem detalhes internos: JSON inválido, `[]`, `null`, `{}`, `items` vazio/objeto, tokens após o JSON, chave duplicada, campo extra, quantidade 0/11/−1/1,5/"1"/null/fora de `int`, UUID inválido/numérico/nulo/com espaço, `price` na linha, variante repetida, 21 linhas; corpo > 16 KiB → 413; `text/plain` → recusado (403, ver observação); `GET` → 403. Saldo e ledger inalterados após as chamadas.

`storefront-web/scripts/smoke.mjs` (upstream sintético efêmero):

- Unidade do cookie: JSON inválido, objeto, > 4 KiB, 21 linhas → vazio; quantidade 0/11/−1/1,5/"1"/null, UUID inválido e entradas não-objeto descartadas; UUID normalizado, duplicata ignorada, `price`/`sku` ignorados; 20 linhas × 10 serializam < 4 KiB; 21ª linha recusada; soma limitada a 10.
- SSR do carrinho: vazio sem chamada de cotação; com cookie contendo `price: "0.01"`, a página mostra 49,90/99,80 do upstream e nunca 0,01; corpo enviado ao upstream contém só `variantId`/`quantity`; estados INSUFFICIENT/UNAVAILABLE exibidos; nenhum campo privado, saldo exato ou botão de checkout; cookie adulterado (JSON inválido, 5 000 bytes, 21 linhas, 0/11/−1, UUID inválido, percent-encoding inválido) → 200, carrinho vazio, sem cotação.
- **A/B:** cookie `sc-cart-loja-teste` enviado a `/lojas/loja-vazia/carrinho` não é lido nem cotado; carrinho de loja inexistente → página 404.
- Server action via formulário progressivo: `303` para `/lojas/loja-teste/carrinho?aviso=adicionado` com `Set-Cookie` `HttpOnly`, `SameSite=lax`, `Secure`, `Path=/lojas/loja-teste` e valor `[{variantId, quantity}]`; `Origin` externa → sem 303 e sem cookie.
- Todas as chamadas ao upstream: somente `GET` público ou `POST …/cart/quote`, sem `Cookie` nem `Authorization` do visitante.

## Revisão de segurança multi-tenant

- Tenant vem só do slug publicado resolvido no servidor; IDs do cookie são seletores e a consulta filtra por `store.slug`, `status='PUBLISHED'`, `published`, e junções por `tenant_id` (variante → produto → loja). Não há caminho em que uma variante de B gere nome/preço no slug A.
- Respostas `UNAVAILABLE` idênticas para todas as causas (sem enumeração de variantes de outras lojas). Inferência de saldo por tentativas continua limitada a 1–10 por linha — risco residual já registrado no ADR-0012 (D4).
- Cadeia pública: um único `POST` adicionado, sem sessão/contexto de segurança, sem cookies emitidos; mutações administrativas mantêm CSRF.
- Vitrine: cookie não é confiável nem assinado (ADR-0012); nunca contém preço; SSR não encaminha credenciais; mutações apenas por server actions de mesma origem.

## Observações e pendências

- `Content-Type` diferente de JSON é recusado, mas o 415 do Spring é despachado para `/error`, que nenhuma cadeia libera; o visitante recebe 403 genérico. Comportamento aceito no teste (`403` ou `415`); ajuste fica para revisão, se desejado.
- Revisão visual/WCAG no navegador **não executada**: o backend em 8080 é o da checkout principal (sem esta rota) e não foi reiniciado, conforme instrução. Pendente para a integração: conferir 320/390/768/1440 px, teclado, foco e leitor de tela no carrinho e no botão do produto. Skill `web-design-guidelines` não aplicada nesta sessão.
- `storefront-web/README.md` ainda diz "Sem carrinho"; não editado nesta sessão para evitar conflito — atualizar na integração.
- Integrações previstas: SC-005 troca `SELLABLE_QUANTITY`; SC-006 acrescenta opções de entrega na cotação e na página do carrinho. ARCHITECTURE/DATA_MODEL/ADR-0012 não foram alterados (SC-011).
