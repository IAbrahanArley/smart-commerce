import assert from "node:assert/strict";
import { forwardingSecret } from "./smoke-units.mjs";
import { checkoutSmoke } from "./smoke-checkout.mjs";
import { id, missingId, nextId, orderToken, png, startNext, startUpstream, upstreamRequests } from "./smoke-upstream.mjs";

const port = Number(process.env.SMOKE_PORT ?? 3002);
assert.ok(Number.isInteger(port) && port > 0 && port < 65536, "SMOKE_PORT deve ser válida.");
const base = `http://127.0.0.1:${port}`;
for (const candidate of [port, port + 1, port + 2]) {
  try {
    await fetch(`http://127.0.0.1:${candidate}`, { signal: AbortSignal.timeout(1000) });
    throw new Error(`A porta ${candidate} já está em uso. Escolha outra SMOKE_PORT (usa SMOKE_PORT, +1 e +2).`);
  } catch (error) {
    if (error instanceof Error && error.message.includes("já está em uso")) throw error;
  }
}

const upstream = await startUpstream();
const upstreamPort = upstream.port;
let output = "";
let server;

async function page(path, cartCookie) {
  const response = await fetch(`${base}${path}`, {
    signal: AbortSignal.timeout(10000),
    headers: { Cookie: ["ADMIN_SESSION=synthetic-not-a-credential", cartCookie].filter(Boolean).join("; "), Authorization: "Bearer synthetic-not-a-credential" },
  });
  // React separates adjacent text with <!-- --> markers in SSR HTML; compare the visible text.
  const html = (await response.text()).replaceAll("<!-- -->", "");
  assert.equal(response.headers.get("referrer-policy"), "no-referrer", path);
  assert.equal(response.headers.get("set-cookie"), null, path);
  assert.match(response.headers.get("cache-control") ?? "", /no-store/, path);
  assert.match(html, /<html[^>]*lang="pt-BR"/, path);
  for (const privateContent of ["PRIVATE_COST_FIXTURE", "PRIVATE_SKU_FIXTURE", "PRIVATE_BUYER_FIXTURE", orderToken, "PRIVATE_UPSTREAM_ERROR_FIXTURE", "ADMIN_SESSION", "Administração do Smart Commerce", "current-password"]) {
    assert.ok(!html.includes(privateContent), `${path}: informação privada não deve ser renderizada`);
  }
  return { response, html };
}

try {
  server = await startNext(port, { ...process.env, STOREFRONT_API_ORIGIN: `http://127.0.0.1:${upstreamPort}`, STOREFRONT_CHECKOUT_ENABLED: "true", STOREFRONT_FORWARDING_SECRET: forwardingSecret, STOREFRONT_TRUST_FORWARDED_FOR: "", DEPLOYMENT_ENVIRONMENT: "hosted-test" },
    data => { output = (output + data).slice(-8000); });
  const home = await page("/");
  assert.equal(home.response.status, 200);
  const catalogPage = await page("/lojas/loja-teste");
  assert.match(catalogPage.html, /class="test-environment-banner" role="note"><strong>Ambiente de teste\.<\/strong>/, "Aviso de ambiente de teste em toda página.");
  assert.match(catalogPage.html, /Pedidos feitos aqui são de teste/);
  assert.match(home.html, /Vitrines de lojas/);
  assert.match(home.html, /href="#conteudo"/);
  const catalog = await page("/lojas/loja-teste");
  assert.equal(catalog.response.status, 200);
  assert.match(catalog.html, /Loja de teste da vitrine/);
  assert.match(catalog.html, /Produto público de teste/);
  assert.match(catalog.html, /49,90/);
  assert.match(catalog.html, /Disponível/);
  assert.ok(catalog.html.includes(`/lojas/loja-teste/produtos/${id}`));
  assert.ok(catalog.html.includes(`?after=${id}`));
  // MVP block 1b: logo beside the name and the store's own contacts.
  assert.match(catalog.html, /Fale com a loja/);
  assert.ok(catalog.html.includes('href="https://wa.me/5511988887777"') && catalog.html.includes("(11) 98888-7777"), "WhatsApp formatado.");
  assert.ok(catalog.html.includes('href="mailto:contato@loja.example"'));
  assert.ok(catalog.html.includes('href="https://www.instagram.com/loja.teste/"'));
  assert.ok(catalog.html.includes('src="/midia/lojas/loja-teste/logo"'));
  const logo = await fetch(`${base}/midia/lojas/loja-teste/logo`);
  assert.equal(logo.status, 200); assert.equal(logo.headers.get("content-type"), "image/png");
  const detail = await page(`/lojas/loja-teste/produtos/${id}`);
  assert.equal(detail.response.status, 200);
  assert.match(detail.html, /id="product-title">Produto público de teste/);
  assert.match(detail.html, /Voltar aos produtos/);
  assert.ok(!detail.html.includes('id="public-variant"'), "Produto SIMPLE com variante padrão não deve exibir seletor de combinações.");
  assert.match(detail.html, /<strong>segura<\/strong>/);
  assert.ok(detail.html.includes(`/midia/lojas/loja-teste/produtos/${id}/${id}`));
  assert.ok(!detail.html.includes("/_next/image?"), "Imagens públicas não devem usar cache do otimizador.");
  const variable = await page(`/lojas/loja-variavel/produtos/${nextId}`);
  assert.match(variable.html, /Escolha a combinação/);
  assert.match(variable.html, /Cor: Azul/);
  assert.match(variable.html, /<legend>Cor<\/legend>/);
  assert.match(variable.html, /type="radio"[^>]*checked=""[^>]*value="Azul"/);
  assert.ok(!variable.html.includes('id="public-variant"'), "Variações com atributos comuns usam botões por atributo.");
  assert.match(variable.html, /59,90/);
  const photo = await fetch(`${base}/midia/lojas/loja-teste/produtos/${id}/${id}`, { headers: { Cookie: "ADMIN_SESSION=synthetic-not-a-credential", Authorization: "Bearer synthetic-not-a-credential" } });
  assert.equal(photo.status, 200); assert.equal(photo.headers.get("content-type"), "image/png");
  assert.equal(photo.headers.get("set-cookie"), null); assert.match(photo.headers.get("cache-control"), /no-store/);
  assert.deepEqual(Buffer.from(await photo.arrayBuffer()), png);
  const invalidPhoto = await fetch(`${base}/midia/lojas/loja-teste/produtos/${id}/${nextId}`);
  assert.equal(invalidPhoto.status, 503); assert.ok(!(await invalidPhoto.text()).includes("PRIVATE_UPSTREAM_ERROR_FIXTURE"));
  const invalidPath = await fetch(`${base}/midia/lojas/loja-teste/produtos/invalido/${id}`);
  assert.equal(invalidPath.status, 404);
  const next = await page(`/lojas/loja-teste?after=${id}`);
  assert.match(next.html, /Outro produto publicado/);
  assert.match(next.html, /Indisponível no momento/);
  assert.match(next.html, /Voltar ao início da lista/);
  assert.ok(!next.html.includes("Ver mais produtos"));
  const empty = await page("/lojas/loja-vazia");
  assert.match(empty.html, /Nenhum produto publicado/);
  assert.ok(!empty.html.includes("Fale com a loja") && !empty.html.includes("<script>alert"), "Contatos inválidos são descartados.");
  assert.ok(!empty.html.includes("/midia/lojas/loja-vazia/logo"), "Sem logo, só o nome.");
  for (const path of ["/pagina-inexistente", "/entrar", "/api/auth/me"]) {
    const missing = await page(path);
    assert.equal(missing.response.status, 404, path);
    assert.match(missing.html, /Página não encontrada/);
  }
  for (const path of ["/lojas/loja-inexistente", `/lojas/loja-teste/produtos/${nextId}`, "/lojas/loja-teste?after=invalido"]) {
    const missing = await page(path);
    assert.ok([200, 404].includes(missing.response.status), path);
    // Next can stream the loading boundary before notFound; its browser result is still uniform.
    assert.match(missing.html, /Página não encontrada/);
  }
  // Cart: SSR quote from the backend, sanitized cookie, per-store scope and no forwarded credentials.
  const cartCookie = value => `sc-cart-loja-teste=${encodeURIComponent(value)}`;
  const quotes = () => upstreamRequests.filter(request => request.method === "POST");
  const emptyCart = await page("/lojas/loja-teste/carrinho");
  assert.equal(emptyCart.response.status, 200);
  assert.match(emptyCart.html, /Seu carrinho está vazio/);
  assert.equal(quotes().length, 0, "Carrinho vazio não deve consultar a cotação.");
  const cart = await page("/lojas/loja-teste/carrinho", cartCookie(JSON.stringify([
    { variantId: id, quantity: 2, price: "0.01", unitPrice: "0.01" }, { variantId: nextId, quantity: 3 }, { variantId: missingId, quantity: 1 },
  ])));
  assert.equal(cart.response.status, 200);
  assert.match(cart.html, /Produto público de teste/);
  assert.match(cart.html, /49,90/);
  assert.match(cart.html, /99,80/);
  assert.match(cart.html, /Quantidade indisponível/);
  assert.match(cart.html, /Cor: Azul/);
  assert.match(cart.html, /Item indisponível/);
  assert.match(cart.html, /Indisponível no momento/);
  assert.match(cart.html, /Remover Produto público de teste do carrinho/);
  assert.ok(!cart.html.includes("0,01"), "Preço do cookie nunca deve ser exibido.");
  assert.ok(!cart.html.includes("784321"), "Quantidade exata não deve ser exibida.");
  assert.ok(!/finalizar compra|comprar agora|checkout/i.test(cart.html), "Carrinho não oferece checkout nesta etapa.");
  assert.deepEqual(JSON.parse(quotes().at(-1).body), { items: [{ variantId: id, quantity: 2 }, { variantId: nextId, quantity: 3 }, { variantId: missingId, quantity: 1 }] });
  assert.equal(quotes().at(-1).type, "application/json");
  for (const tampered of ["{", "x".repeat(5000), JSON.stringify(Array.from({ length: 21 }, () => ({ variantId: id, quantity: 1 }))),
    JSON.stringify([{ variantId: id, quantity: 0 }]), JSON.stringify([{ variantId: id, quantity: 11 }]), JSON.stringify([{ variantId: id, quantity: -1 }]),
    JSON.stringify([{ variantId: "not-a-uuid", quantity: 1 }])]) {
    const before = quotes().length;
    const result = await page("/lojas/loja-teste/carrinho", cartCookie(tampered));
    assert.equal(result.response.status, 200, tampered.slice(0, 40));
    assert.match(result.html, /Seu carrinho está vazio/, tampered.slice(0, 40));
    assert.equal(quotes().length, before, "Cookie adulterado não deve chegar à cotação.");
  }
  const badEncoding = await page("/lojas/loja-teste/carrinho", "sc-cart-loja-teste=%E0%A4%A");
  assert.equal(badEncoding.response.status, 200);
  assert.match(badEncoding.html, /Seu carrinho está vazio/);
  const otherStore = await page("/lojas/loja-vazia/carrinho", cartCookie(JSON.stringify([{ variantId: id, quantity: 1 }])));
  assert.match(otherStore.html, /Seu carrinho está vazio/, "Cookie da loja A não pode ser lido na loja B.");
  assert.ok(!quotes().some(request => request.url.includes("loja-vazia")));
  const draftCart = await page("/lojas/loja-inexistente/carrinho", cartCookie(JSON.stringify([{ variantId: id, quantity: 1 }])));
  assert.match(draftCart.html, /Página não encontrada/);
  // Add to cart through the progressive-enhancement form of the server action.
  const action = detail.html.match(/name="(\$ACTION_ID_[0-9a-f]+)"/);
  assert.ok(action, "Formulário de adicionar ao carrinho deve usar server action.");
  assert.match(detail.html, /Adicionar ao carrinho/);
  const addForm = () => { const form = new FormData(); form.set(action[1], ""); form.set("slug", "loja-teste"); form.set("variantId", id); form.set("quantity", "2"); return form; };
  const added = await fetch(`${base}/lojas/loja-teste/produtos/${id}`, { method: "POST", body: addForm(), redirect: "manual", headers: { Origin: base } });
  assert.equal(added.status, 303);
  assert.equal(added.headers.get("location"), "/lojas/loja-teste/carrinho?aviso=adicionado");
  const setCookie = added.headers.get("set-cookie") ?? "";
  assert.match(setCookie, /^sc-cart-loja-teste=/);
  assert.match(setCookie, /HttpOnly/i); assert.match(setCookie, /SameSite=lax/i); assert.match(setCookie, /Secure/i);
  assert.match(setCookie, /Path=\/lojas\/loja-teste(;|$)/);
  assert.deepEqual(JSON.parse(decodeURIComponent(setCookie.split(";")[0].split("=")[1])), [{ variantId: id, quantity: 2 }]);
  const foreign = await fetch(`${base}/lojas/loja-teste/produtos/${id}`, { method: "POST", body: addForm(), redirect: "manual", headers: { Origin: "https://foreign.example" } });
  assert.notEqual(foreign.status, 303);
  assert.ok(!(foreign.headers.get("set-cookie") ?? "").includes("sc-cart"), "Origem externa não pode alterar o carrinho.");
  const buyable = await checkoutSmoke({ base, page, cartCookie, port, upstreamPort });
  const unavailable = await page("/lojas/loja-indisponivel");
  assert.ok([200, 500].includes(unavailable.response.status));
  assert.ok(upstreamRequests.length >= 6, "Fluxos públicos precisam consultar o upstream.");
  for (const request of upstreamRequests) {
    assert.ok(request.method === "GET" || (request.method === "POST" && /^\/api\/public\/stores\/[a-z0-9-]+\/(?:cart\/quote|orders|orders\/[0-9a-f-]{36}\/payment)$/.test(request.url)), `${request.method} ${request.url}`);
    assert.ok(request.url.startsWith("/api/public/stores/"));
    assert.equal(request.cookie, undefined, "SSR não deve encaminhar cookies do visitante.");
    assert.equal(request.authorization, undefined, "SSR não deve encaminhar Authorization.");
  }
  console.log("Smoke aprovado (checkout ligado): checkout sem JavaScript, redirecionamento ao Checkout Pro só para página HTTPS do Mercado Pago, nova tentativa pelo pedido, retorno sem confiar na URL, validação por campo, PRICE_CHANGED, retry com a mesma chave após falha ambígua, replay sem novo token, REQUEST_CONFLICT sem chave nova, cookie do pedido por caminho, carrinho limpo, status com token e IP assinado só atrás de proxy confiável; catálogo/detalhe SSR, descrição HTML, variantes, fotos/proxy MIME, preços exatos, paginação, vazio, 404, projeção pública, no-store, carrinho (cookie saneado e por loja, cotação do backend, server action com cookie HttpOnly/SameSite/Secure/Path e rejeição de origem externa) e ausência de cookies/Authorization no upstream. Não substitui browser/API real.");
  // D6: a production server with the flag but outside the hosted test environment has no checkout.
  const offPort = port + 1;
  const offBase = `http://127.0.0.1:${offPort}`;
  const offEnv = { ...process.env, STOREFRONT_API_ORIGIN: `http://127.0.0.1:${upstreamPort}`, STOREFRONT_CHECKOUT_ENABLED: "true" };
  delete offEnv.DEPLOYMENT_ENVIRONMENT;
  const off = await startNext(offPort, offEnv);
  try {
    const offCart = (await (await fetch(`${offBase}/lojas/loja-teste/carrinho`, { headers: { Cookie: buyable } })).text()).replaceAll("<!-- -->", "");
    assert.match(offCart, /Produto público de teste/);
    assert.ok(!offCart.includes("Finalizar pedido"), "Sem configuração, o carrinho não oferece checkout.");
    assert.match(offCart, /A finalização da compra não está disponível no momento/);
    const offCheckout = (await (await fetch(`${offBase}/lojas/loja-teste/checkout`, { headers: { Cookie: buyable } })).text()).replaceAll("<!-- -->", "");
    assert.match(offCheckout, /Página não encontrada/);
    assert.ok(!offCheckout.includes("Confirmar pedido"));
    assert.ok(!offCart.includes("test-environment-banner"), "Fora do ambiente de teste não há aviso.");
  } finally {
    off.kill();
  }
  console.log("Smoke aprovado (checkout desligado): build de produção com a flag mas fora do ambiente de teste não tem link nem rota de checkout (D6), nem aviso.");
} catch (error) {
  if (output) console.error(output);
  throw error;
} finally {
  server?.kill();
  await upstream.close();
}
