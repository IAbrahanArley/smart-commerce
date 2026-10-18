import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { createServer } from "node:http";
import { setTimeout as delay } from "node:timers/promises";
import { createHmac } from "node:crypto";
import { addMoney, formatBRL } from "../src/lib/price.ts";
import { checkoutEnabled, emptyValues, validateCheckout } from "../src/lib/checkout-input.ts";
import { orderCookieName, orderCookieOptions, validOrderToken } from "../src/lib/order-cookie.ts";
import { signedClientIp, trustedClientIp } from "../src/lib/client-ip.ts";
import { addLine, CART_MAX_LINES, parseCart, serializeCart } from "../src/lib/cart-cookie.ts";

assert.equal(formatBRL("99999999999999999.99").replace(/\s/g, " "), "R$ 99.999.999.999.999.999,99");
assert.equal(formatBRL("0.01").replace(/\s/g, " "), "R$ 0,01");
assert.equal(formatBRL("1.001"), "Preço indisponível");
assert.equal(formatBRL("19999999999999999980.00").replace(/\s/g, " "), "R$ 19.999.999.999.999.999.980,00");

// Tampered cart cookies are sanitized, never trusted and never fatal.
const cartId = "00000000-0000-0000-0000-00000000000a";
const many = count => Array.from({ length: count }, (_, i) => ({ variantId: `00000000-0000-0000-0000-${String(i).padStart(12, "0")}`, quantity: 1 }));
assert.deepEqual(parseCart(undefined), []);
assert.deepEqual(parseCart("{"), []);
assert.deepEqual(parseCart("{\"variantId\":\"x\"}"), []);
assert.deepEqual(parseCart("x".repeat(4097)), []);
assert.deepEqual(parseCart(JSON.stringify(many(21))), []);
assert.equal(parseCart(JSON.stringify(many(20))).length, 20);
assert.deepEqual(parseCart(JSON.stringify([
  { variantId: cartId.toUpperCase(), quantity: 2, price: "0.01", sku: "PRIVATE" },
  { variantId: cartId, quantity: 3 },
  ...[0, 11, -1, 1.5, "1", null].map(quantity => ({ variantId: "00000000-0000-0000-0000-00000000000b", quantity })),
  { variantId: "not-a-uuid", quantity: 1 },
  null, [], "text",
])), [{ variantId: cartId, quantity: 2 }]);
assert.ok(encodeURIComponent(serializeCart(many(CART_MAX_LINES).map(line => ({ ...line, quantity: 10 })))).length < 4096);
assert.equal(addLine(many(CART_MAX_LINES), cartId, 1), null);
assert.deepEqual(addLine([{ variantId: cartId, quantity: 9 }], cartId.toUpperCase(), 5), [{ variantId: cartId, quantity: 10 }]);

// Checkout rules (SC-009): exact money, D6 flag, field validation and path-scoped order cookie.
assert.equal(addMoney("99.80", "12.50"), "112.30");
assert.equal(addMoney("0.01", "0.00"), "0.01");
assert.equal(addMoney("19999999999999999999.99", "0.01"), "20000000000000000000.00");
assert.equal(addMoney("1.001", "0.00"), null);
for (const value of [undefined, "", "1", "TRUE", "yes"]) assert.equal(checkoutEnabled(value), false, String(value));
assert.equal(checkoutEnabled("true"), true);
const requestId = "11111111-1111-4111-8111-111111111111";
const totals = { PICKUP: "99.80", FIXED_SHIPPING: "112.30" };
const valid = { ...emptyValues(), fulfillment: "PICKUP", name: " Comprador  Sintético ", email: "pessoa@example.test", phone: "(11) 98888-7777" };
assert.deepEqual(validateCheckout(valid, { requestId, totals }), { payload: { requestId, fulfillment: { type: "PICKUP" }, buyer: { name: "Comprador  Sintético", email: "pessoa@example.test", phone: "11988887777" }, expectedTotal: "99.80" } });
const shipped = validateCheckout({ ...valid, fulfillment: "FIXED_SHIPPING", line1: "Rua A, 1", district: "Centro", city: "São Paulo", state: "sp", postalCode: "01001-000" }, { requestId, totals });
assert.deepEqual(shipped.payload.fulfillment, { type: "FIXED_SHIPPING", address: { line1: "Rua A, 1", district: "Centro", city: "São Paulo", state: "SP", postalCode: "01001000" } });
assert.equal(shipped.payload.expectedTotal, "112.30");
const errors = validateCheckout({ ...emptyValues(), fulfillment: "FIXED_SHIPPING", name: "Linha\nquebrada", email: "sem-arroba", phone: "123", state: "S", postalCode: "123" }, { requestId, totals }).errors;
for (const field of ["name", "email", "phone", "line1", "district", "city", "state", "postalCode"]) assert.ok(errors[field], field);
assert.ok(validateCheckout({ ...valid, fulfillment: "TELEPORT" }, { requestId, totals }).errors.fulfillment);
assert.ok(validateCheckout({ ...valid, fulfillment: "PICKUP" }, { requestId, totals: {} }).errors.fulfillment);
assert.ok("errors" in validateCheckout(valid, { requestId: "not-a-uuid", totals }));
assert.equal(orderCookieName("AAAAAAAA-0000-0000-0000-000000000001"), "sc-order-aaaaaaaa-0000-0000-0000-000000000001");
assert.deepEqual(orderCookieOptions("loja-teste", "AAAAAAAA-0000-0000-0000-000000000001", true), { httpOnly: true, sameSite: "lax", secure: true, path: "/lojas/loja-teste/pedidos/aaaaaaaa-0000-0000-0000-000000000001", maxAge: 2592000 });
assert.equal(validOrderToken("a".repeat(43)), true);
assert.equal(validOrderToken("a".repeat(42)), false);
const forwardingSecret = "synthetic-forwarding-secret-for-smoke-0001";
// D4: X-Forwarded-For is trusted only behind a declared proxy; the signature matches the backend HMAC.
assert.equal(trustedClientIp("203.0.113.99", undefined), null);
assert.equal(trustedClientIp("203.0.113.99", "1"), null);
assert.equal(trustedClientIp("203.0.113.99, 198.51.100.7", "true"), "198.51.100.7");
assert.equal(trustedClientIp("evil<script>", "true"), null);
const signed = signedClientIp("198.51.100.7", forwardingSecret, 1760000000000);
assert.deepEqual(signed, { "X-SC-Client-IP": "198.51.100.7", "X-SC-Client-IP-Timestamp": "1760000000",
  "X-SC-Client-IP-Signature": createHmac("sha256", forwardingSecret).update("198.51.100.7\n1760000000").digest("hex") });
assert.deepEqual(signedClientIp("198.51.100.7", "short", 1760000000000), {});
assert.deepEqual(signedClientIp(null, forwardingSecret, 1760000000000), {});

const port = Number(process.env.SMOKE_PORT ?? 3002);
assert.ok(Number.isInteger(port) && port > 0 && port < 65536, "SMOKE_PORT deve ser válida.");
const base = `http://127.0.0.1:${port}`;
try {
  await fetch(base, { signal: AbortSignal.timeout(1000) });
  throw new Error(`A porta ${port} já está em uso. Escolha outra SMOKE_PORT.`);
} catch (error) {
  if (error instanceof Error && error.message.includes("já está em uso")) throw error;
}

// Ephemeral synthetic upstream verifies SSR projection and credential separation, without a real account.
const id = "00000000-0000-0000-0000-000000000001";
const nextId = "00000000-0000-0000-0000-000000000002";
const store = { name: "Loja de teste da vitrine", slug: "loja-teste" };
const item = { id, name: "Produto público de teste", price: "49.90", currency: "BRL", available: true, kind: "SIMPLE", descriptionHtml: "<p>Descrição <strong>segura</strong>.</p>", variants: [{ id, price: "49.90", currency: "BRL", available: true, options: [] }] };
const variantItem = { ...item, id: nextId, name: "Camiseta com opções", kind: "VARIABLE", variants: [{ id, price: "59.90", currency: "BRL", available: false, options: [{ name: "Cor", value: "Azul" }, { name: "Tamanho", value: "M" }], sku: "PRIVATE_SKU_FIXTURE", cost: "PRIVATE_COST_FIXTURE", quantity: 784321 }] };
const png = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j1ioAAAAASUVORK5CYII=", "base64");
const upstreamRequests = [];
const missingId = "00000000-0000-0000-0000-000000000003";
const orderId = "00000000-0000-0000-0000-0000000000a7";
const orderToken = "SyntheticOrderToken_0123456789abcdefghijklm";
const syntheticOrder = { id: orderId, number: 7, status: "PENDING_PAYMENT", currency: "BRL", subtotal: "99.80", shipping: "0.00", total: "99.80",
  items: [{ productName: "Produto público de teste", options: [], unitPrice: "49.90", quantity: 2, lineTotal: "99.80", sku: "PRIVATE_SKU_FIXTURE" }],
  fulfillment: { type: "PICKUP", text: "Rua Sintética, 10" }, reservationExpiresAt: "2026-10-18T15:30:00Z", createdAt: "2026-10-18T15:00:00Z" };
const orderRequests = [];
const orderReads = [];
const createdOrders = new Map();
const upstream = createServer(async (request, response) => {
  let body = "";
  for await (const chunk of request) body += chunk;
  upstreamRequests.push({ url: request.url, method: request.method, cookie: request.headers.cookie, authorization: request.headers.authorization, type: request.headers["content-type"], body });
  response.setHeader("Content-Type", "application/json");
  if (request.method === "POST" && request.url === "/api/public/stores/loja-teste/cart/quote") {
    // Synthetic backend quote: prices differ from anything the browser could send.
    const lines = JSON.parse(body).items.map(({ variantId, quantity }) => variantId === id
      ? { variantId, quantity, status: "AVAILABLE", productId: id, productName: "Produto público de teste", options: [], unitPrice: "49.90", lineTotal: (4990 * quantity / 100).toFixed(2), sku: "PRIVATE_SKU_FIXTURE", cost: "PRIVATE_COST_FIXTURE" }
      : variantId === nextId
        ? { variantId, quantity, status: "INSUFFICIENT", productId: nextId, productName: "Camiseta com opções", options: [{ name: "Cor", value: "Azul" }], unitPrice: "59.90", lineTotal: (5990 * quantity / 100).toFixed(2), available: 784321 }
        : { variantId, quantity, status: "UNAVAILABLE", productId: null, productName: null, options: null, unitPrice: null, lineTotal: null });
    const subtotal = lines.filter(line => line.status === "AVAILABLE").reduce((sum, line) => sum + Math.round(Number(line.lineTotal) * 100), 0);
    response.end(JSON.stringify({ currency: "BRL", lines, subtotal: (subtotal / 100).toFixed(2), purchasable: lines.every(line => line.status === "AVAILABLE"),
      delivery: [{ type: "PICKUP", instructions: "Rua Sintética, 10" }, { type: "FIXED_SHIPPING", amount: "12.50", currency: "BRL", area: "Centro (área informativa)" }] }));
  } else if (request.method === "POST" && request.url === "/api/public/stores/loja-teste/orders") {
    // Synthetic checkout backend: behavior chosen by the buyer name, private fields added to test projection.
    const order = JSON.parse(body);
    orderRequests.push({ body: order, headers: request.headers });
    const name = order.buyer.name;
    if (name === "Preco Mudou") { response.statusCode = 409; response.end(JSON.stringify({ code: "PRICE_CHANGED", currentTotal: "1.00" })); return; }
    if (name === "Falha Rede" && orderRequests.filter(item => item.body.requestId === order.requestId && item.body.buyer.name === name).length === 1) { response.statusCode = 503; response.end("{}"); return; }
    const replay = createdOrders.has(order.requestId);
    createdOrders.set(order.requestId, true);
    response.statusCode = replay ? 200 : 201;
    response.end(JSON.stringify({ order: syntheticOrder, accessToken: replay ? null : orderToken, replayed: replay, buyerEmail: "PRIVATE_BUYER_FIXTURE" }));
  } else if (request.method === "GET" && request.url === `/api/public/stores/loja-teste/orders/${orderId}`) {
    orderReads.push(request.headers["x-order-token"]);
    if (request.headers["x-order-token"] !== orderToken) { response.statusCode = 404; response.end("{}"); return; }
    response.end(JSON.stringify({ ...syntheticOrder, buyerPhone: "PRIVATE_BUYER_FIXTURE", sku: "PRIVATE_SKU_FIXTURE" }));
  } else if (request.method !== "GET") {
    response.statusCode = 403;
    response.end(JSON.stringify({ message: "Proibido" }));
  } else if (request.url === "/api/public/stores/loja-teste") {
    response.end(JSON.stringify({ ...store, products: [{ ...item, descriptionHtml: "", variants: [], cost: "PRIVATE_COST_FIXTURE", sku: "PRIVATE_SKU_FIXTURE", quantity: 784321 }], nextCursor: id }));
  } else if (request.url === `/api/public/stores/loja-teste?after=${id}`) {
    response.end(JSON.stringify({ ...store, products: [{ ...item, descriptionHtml: "", variants: [], id: nextId, name: "Outro produto publicado", available: false }], nextCursor: null }));
  } else if (request.url === `/api/public/stores/loja-teste/products/${id}/images`) {
    response.end(JSON.stringify({ images: [{ id, alt: "Foto de teste", width: 300, height: 300 }] }));
  } else if (request.url === `/api/public/stores/loja-teste/products/${id}/images/primary/content` || request.url === `/api/public/stores/loja-teste/products/${id}/images/${id}/content`) {
    response.setHeader("Content-Type", "image/png"); response.setHeader("Set-Cookie", "PRIVATE_UPSTREAM_COOKIE=synthetic; Path=/"); response.end(png);
  } else if (request.url === `/api/public/stores/loja-teste/products/${id}/images/${nextId}/content`) {
    response.setHeader("Content-Type", "text/html"); response.end("PRIVATE_UPSTREAM_ERROR_FIXTURE");
  } else if (request.url === `/api/public/stores/loja-teste/products/${id}`) {
    response.end(JSON.stringify({ store, product: item }));
  } else if (request.url === `/api/public/stores/loja-variavel/products/${nextId}`) {
    response.end(JSON.stringify({ store: { ...store, slug: "loja-variavel" }, product: variantItem }));
  } else if (request.url === `/api/public/stores/loja-variavel/products/${nextId}/images`) {
    response.end(JSON.stringify({ images: [] }));
  } else if (request.url === "/api/public/stores/loja-vazia") {
    response.end(JSON.stringify({ name: "Loja vazia de teste", slug: "loja-vazia", products: [], nextCursor: null }));
  } else if (request.url === "/api/public/stores/loja-indisponivel") {
    response.statusCode = 503;
    response.end(JSON.stringify({ message: "PRIVATE_UPSTREAM_ERROR_FIXTURE" }));
  } else {
    response.statusCode = 404;
    response.end(JSON.stringify({ message: "Não encontrado" }));
  }
});
await new Promise(resolve => upstream.listen(0, "127.0.0.1", resolve));
const upstreamPort = upstream.address().port;
const server = spawn(process.execPath, ["node_modules/next/dist/bin/next", "start", "--hostname", "127.0.0.1", "--port", String(port)], {
  cwd: new URL("..", import.meta.url),
  env: { ...process.env, NEXT_TELEMETRY_DISABLED: "1", STOREFRONT_API_ORIGIN: `http://127.0.0.1:${upstreamPort}`, STOREFRONT_CHECKOUT_ENABLED: "true", STOREFRONT_FORWARDING_SECRET: forwardingSecret },
  stdio: ["ignore", "pipe", "pipe"], windowsHide: true,
});
let output = "";
let spawnError;
server.on("error", error => { spawnError = error; });
for (const stream of [server.stdout, server.stderr]) stream.on("data", data => { output = (output + data.toString()).slice(-8000); });

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
  for (let attempt = 0; attempt < 60; attempt++) {
    if (spawnError) throw spawnError;
    assert.equal(server.exitCode, null, `Servidor encerrou antes do teste.\n${output}`);
    try { await fetch(base, { signal: AbortSignal.timeout(1000) }); break; } catch { await delay(500); }
  }
  const home = await page("/");
  assert.equal(home.response.status, 200);
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
  // Checkout (SC-009): purchasable cart links to the checkout; the form posts through the server action.
  const buyable = cartCookie(JSON.stringify([{ variantId: id, quantity: 2 }]));
  const buyableCart = await page("/lojas/loja-teste/carrinho", buyable);
  assert.match(buyableCart.html, /href="\/lojas\/loja-teste\/checkout"[^>]*>Finalizar pedido/);
  const checkout = await page("/lojas/loja-teste/checkout", buyable);
  assert.equal(checkout.response.status, 200);
  assert.match(checkout.html, /<h1>Finalizar pedido<\/h1>/);
  assert.match(checkout.html, /Retirar na loja/);
  assert.match(checkout.html, /Frete R\$\s12,50 · Total R\$\s112,30/);
  assert.match(checkout.html, /Sem custo de entrega · Total R\$\s99,80/);
  assert.match(checkout.html, /são usados apenas pela <span translate="no">Loja de teste da vitrine<\/span>/);
  assert.match(checkout.html, /anonimizados em até 90 dias/);
  assert.match(checkout.html, /autoComplete="email"|autocomplete="email"/);
  const hidden = name => checkout.html.match(new RegExp(`name="${name}" value="([^"]*)"`))?.[1];
  const formRequestId = hidden("requestId");
  assert.match(formRequestId ?? "", /^[0-9a-f-]{36}$/);
  assert.equal(hidden("total-PICKUP"), "99.80");
  assert.equal(hidden("total-FIXED_SHIPPING"), "112.30");
  const decode = value => value.replaceAll("&quot;", '"').replaceAll("&#x27;", "'").replaceAll("&lt;", "<").replaceAll("&gt;", ">").replaceAll("&amp;", "&");
  const actionFields = [...checkout.html.matchAll(/<input type="hidden" name="(\$ACTION_[^"]+)"(?: value="([^"]*)")?/g)].map(match => [match[1], decode(match[2] ?? "")]);
  assert.ok(actionFields.length > 0, "Checkout deve funcionar sem JavaScript pela server action.");
  const checkoutPost = async (fields, cookie = buyable) => {
    const form = new FormData();
    for (const [name, value] of actionFields) form.set(name, value);
    for (const [name, value] of Object.entries({ slug: "loja-teste", requestId: formRequestId, "total-PICKUP": "99.80", "total-FIXED_SHIPPING": "112.30", ...fields })) form.set(name, value);
    return fetch(`${base}/lojas/loja-teste/checkout`, { method: "POST", body: form, redirect: "manual", headers: { Origin: base, Cookie: `${cookie}; ADMIN_SESSION=synthetic-not-a-credential`, Authorization: "Bearer synthetic-not-a-credential" } });
  };
  const buyer = { fulfillment: "PICKUP", name: "Comprador Sintético", email: "pessoa@example.test", phone: "(11) 98888-7777" };
  const invalidCheckout = await checkoutPost({ ...buyer, email: "sem-arroba", phone: "123" });
  const invalidHtml = (await invalidCheckout.text()).replaceAll("<!-- -->", "");
  assert.equal(invalidCheckout.status, 200);
  assert.match(invalidHtml, /Confira os campos destacados/);
  assert.match(invalidHtml, /id="checkout-email"[^>]*aria-invalid="true"[^>]*aria-describedby="email-error"/);
  assert.match(invalidHtml, /Informe o telefone com DDD/);
  assert.equal(orderRequests.length, 0, "Dados inválidos não chegam ao backend.");
  const changed = await checkoutPost({ ...buyer, name: "Preco Mudou" });
  assert.equal(changed.status, 200);
  assert.match((await changed.text()).replaceAll("<!-- -->", ""), /Os valores mudaram/);
  const ambiguous = await checkoutPost({ ...buyer, name: "Falha Rede" });
  assert.match((await ambiguous.text()).replaceAll("<!-- -->", ""), /a mesma tentativa será reaproveitada/);
  const retried = await checkoutPost({ ...buyer, name: "Falha Rede" });
  assert.equal(retried.status, 303);
  const sameKey = orderRequests.filter(item => item.body.buyer.name === "Falha Rede");
  assert.equal(sameKey.length, 2);
  assert.equal(sameKey[0].body.requestId, sameKey[1].body.requestId, "Retry após falha ambígua reutiliza a chave.");
  const sent = orderRequests.at(-1);
  assert.deepEqual(sent.body.items, [{ variantId: id, quantity: 2 }], "Itens vêm do cookie, não do formulário.");
  assert.deepEqual(sent.body.fulfillment, { type: "PICKUP" });
  assert.equal(sent.body.expectedTotal, "99.80");
  assert.deepEqual(sent.body.buyer, { name: "Falha Rede", email: "pessoa@example.test", phone: "11988887777" });
  assert.equal(sent.headers.cookie, undefined); assert.equal(sent.headers.authorization, undefined);
  // Without a declared trusted proxy, no visitor IP is signed: the backend limits by connection.
  assert.equal(sent.headers["x-sc-client-ip"], undefined, "Sem proxy confiável declarado, o IP não é repassado.");
  assert.equal(sent.headers["x-sc-client-ip-signature"], undefined);
  assert.equal(retried.headers.get("location"), `/lojas/loja-teste/pedidos/${orderId}`);
  const cookiesSet = retried.headers.getSetCookie();
  const orderCookie = cookiesSet.find(cookie => cookie.startsWith(`sc-order-${orderId}=`));
  assert.ok(orderCookie, "Token do pedido fica em cookie próprio.");
  assert.match(orderCookie, /HttpOnly/i); assert.match(orderCookie, /SameSite=lax/i); assert.match(orderCookie, /Secure/i);
  assert.match(orderCookie, new RegExp(`Path=/lojas/loja-teste/pedidos/${orderId}(;|$)`));
  assert.ok(!(retried.headers.get("location") ?? "").includes("example.test"), "Dados pessoais nunca vão para a URL.");
  assert.ok(cookiesSet.some(cookie => /^sc-cart-loja-teste=;/.test(cookie) && /Path=\/lojas\/loja-teste(;|$)/.test(cookie)), "Carrinho é limpo após o pedido.");
  const status = await page(`/lojas/loja-teste/pedidos/${orderId}`, `sc-order-${orderId}=${orderToken}`);
  assert.equal(status.response.status, 200);
  assert.match(status.html, /Pedido nº 7/);
  assert.match(status.html, /Aguardando pagamento/);
  assert.match(status.html, /<time dateTime="2026-10-18T15:30:00Z">18\/10\/2026,? 12:30<\/time>/);
  assert.match(status.html, /Rua Sintética, 10/);
  assert.equal(orderReads.at(-1), orderToken);
  for (const [path, cookie] of [[`/lojas/loja-teste/pedidos/${orderId}`, undefined], [`/lojas/loja-teste/pedidos/${orderId}`, `sc-order-${orderId}=${"x".repeat(43)}`], [`/lojas/loja-vazia/pedidos/${orderId}`, `sc-order-${orderId}=${orderToken}`], ["/lojas/loja-teste/pedidos/invalido", undefined]]) {
    const missing = await page(path, cookie);
    assert.match(missing.html, /Página não encontrada/, `${path} ${cookie ? "com cookie" : "sem cookie"}`);
  }
  // A visitor-supplied X-Forwarded-For must not become the signed IP (the per-IP limit would be bypassed).
  const spoofForm = new FormData();
  for (const [name, value] of actionFields) spoofForm.set(name, value);
  for (const [name, value] of Object.entries({ slug: "loja-teste", requestId: "22222222-2222-4222-8222-222222222222", "total-PICKUP": "99.80", ...buyer, name: "Ip Forjado" })) spoofForm.set(name, value);
  await fetch(`${base}/lojas/loja-teste/checkout`, { method: "POST", body: spoofForm, redirect: "manual", headers: { Origin: base, Cookie: buyable, "X-Forwarded-For": "203.0.113.99" } });
  const spoofed = orderRequests.find(item => item.body.buyer.name === "Ip Forjado");
  assert.ok(spoofed, "Tentativa com X-Forwarded-For forjado deve chegar ao backend.");
  assert.equal(spoofed.headers["x-sc-client-ip"], undefined, "X-Forwarded-For do visitante não pode virar o IP assinado.");
  const foreignCheckout = await fetch(`${base}/lojas/loja-teste/checkout`, { method: "POST", body: new FormData(), redirect: "manual", headers: { Origin: "https://foreign.example", Cookie: buyable } });
  assert.notEqual(foreignCheckout.status, 303);
  const unavailable = await page("/lojas/loja-indisponivel");
  assert.ok([200, 500].includes(unavailable.response.status));
  assert.ok(upstreamRequests.length >= 6, "Fluxos públicos precisam consultar o upstream.");
  for (const request of upstreamRequests) {
    assert.ok(request.method === "GET" || (request.method === "POST" && /^\/api\/public\/stores\/[a-z0-9-]+\/(?:cart\/quote|orders)$/.test(request.url)), `${request.method} ${request.url}`);
    assert.ok(request.url.startsWith("/api/public/stores/"));
    assert.equal(request.cookie, undefined, "SSR não deve encaminhar cookies do visitante.");
    assert.equal(request.authorization, undefined, "SSR não deve encaminhar Authorization.");
  }
  console.log("Smoke aprovado (checkout ligado): checkout sem JavaScript, validação por campo, PRICE_CHANGED, retry com a mesma chave após falha ambígua, cookie do pedido por caminho, carrinho limpo e status com token; catálogo/detalhe SSR, descrição HTML, variantes, fotos/proxy MIME, preços exatos, paginação, vazio, 404, projeção pública, no-store, carrinho (cookie saneado e por loja, cotação do backend, server action com cookie HttpOnly/SameSite/Secure/Path e rejeição de origem externa) e ausência de cookies/Authorization no upstream. Não substitui browser/API real.");
  // D6: with the flag absent, no checkout link or route exists, even for a purchasable cart.
  const offPort = port + 1;
  const offBase = `http://127.0.0.1:${offPort}`;
  const offEnv = { ...process.env, NEXT_TELEMETRY_DISABLED: "1", STOREFRONT_API_ORIGIN: `http://127.0.0.1:${upstreamPort}` };
  delete offEnv.STOREFRONT_CHECKOUT_ENABLED;
  const off = spawn(process.execPath, ["node_modules/next/dist/bin/next", "start", "--hostname", "127.0.0.1", "--port", String(offPort)], { cwd: new URL("..", import.meta.url), env: offEnv, stdio: "ignore", windowsHide: true });
  try {
    for (let attempt = 0; attempt < 60; attempt++) {
      try { await fetch(offBase, { signal: AbortSignal.timeout(1000) }); break; } catch { await delay(500); }
    }
    const offCart = (await (await fetch(`${offBase}/lojas/loja-teste/carrinho`, { headers: { Cookie: buyable } })).text()).replaceAll("<!-- -->", "");
    assert.match(offCart, /Produto público de teste/);
    assert.ok(!offCart.includes("Finalizar pedido"), "Sem configuração, o carrinho não oferece checkout.");
    assert.match(offCart, /A finalização da compra não está disponível no momento/);
    const offCheckout = (await (await fetch(`${offBase}/lojas/loja-teste/checkout`, { headers: { Cookie: buyable } })).text()).replaceAll("<!-- -->", "");
    assert.match(offCheckout, /Página não encontrada/);
    assert.ok(!offCheckout.includes("Confirmar pedido"));
  } finally {
    off.kill();
  }
  console.log("Smoke aprovado (checkout desligado): sem link no carrinho e rota de checkout inexistente.");
} finally {
  server.kill();
  upstream.closeAllConnections();
  await new Promise(resolve => upstream.close(resolve));
}
