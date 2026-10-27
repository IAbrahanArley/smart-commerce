import { spawn } from "node:child_process";
import { createServer } from "node:http";
import { setTimeout as delay } from "node:timers/promises";

// Ephemeral synthetic upstream: verifies SSR projection and credential separation, without a real account.
export const id = "00000000-0000-0000-0000-000000000001";
export const nextId = "00000000-0000-0000-0000-000000000002";
export const missingId = "00000000-0000-0000-0000-000000000003";
// MVP block 1c: one public category; the category routes check the filter and pagination keep it.
export const categoryId = "00000000-0000-0000-0000-0000000000c1";
const categories = [{ id: categoryId, name: "Camisetas" }];
export const orderId = "00000000-0000-0000-0000-0000000000a7";
export const orderToken = "SyntheticOrderToken_0123456789abcdefghijklm";
export const png = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j1ioAAAAASUVORK5CYII=", "base64");
export const upstreamRequests = [];
export const orderRequests = [];
export const orderReads = [];
export const paymentRequests = [];
export const checkoutUrl = "https://sandbox.mercadopago.com.br/checkout/v1/redirect?id=ORD-7";
// SC-013: answer of the synthetic payment start; smoke steps swap it to exercise failures.
export const paymentReply = { status: 200, body: { checkoutUrl } };

// MVP block 1b: public contacts and logo; the hostile e-mail must be dropped by the storefront.
const store = { name: "Loja de teste da vitrine", slug: "loja-teste", profile: { contactEmail: "contato@loja.example", whatsapp: "5511988887777", instagram: "loja.teste", logo: true } };
const item = { id, name: "Produto público de teste", price: "49.90", currency: "BRL", available: true, kind: "SIMPLE", descriptionHtml: "<p>Descrição <strong>segura</strong>.</p>", variants: [{ id, price: "49.90", currency: "BRL", available: true, options: [] }] };
const variantItem = { ...item, id: nextId, name: "Camiseta com opções", kind: "VARIABLE", variants: [{ id, price: "59.90", currency: "BRL", available: false, options: [{ name: "Cor", value: "Azul" }, { name: "Tamanho", value: "M" }], sku: "PRIVATE_SKU_FIXTURE", cost: "PRIVATE_COST_FIXTURE", quantity: 784321 }] };
const syntheticOrder = { id: orderId, number: 7, status: "PENDING_PAYMENT", currency: "BRL", subtotal: "99.80", shipping: "0.00", total: "99.80",
  items: [{ productName: "Produto público de teste", options: [], unitPrice: "49.90", quantity: 2, lineTotal: "99.80", sku: "PRIVATE_SKU_FIXTURE" }],
  fulfillment: { type: "PICKUP", text: "Rua Sintética, 10" }, reservationExpiresAt: "2026-10-18T15:30:00Z", createdAt: "2026-10-18T15:00:00Z", payable: true };
// requestId -> payload fingerprint of the order the synthetic backend committed.
const createdOrders = new Map();

function json(response, status, body) {
  response.statusCode = status;
  response.end(JSON.stringify(body));
}

// Synthetic checkout backend, behavior chosen by the buyer name. Mirrors the SC-008 contract: a key
// already committed replays (same payload, no token) or answers REQUEST_CONFLICT (other payload).
function checkout(order, headers, response) {
  orderRequests.push({ body: order, headers });
  const name = order.buyer.name;
  const fingerprint = JSON.stringify({ ...order, requestId: undefined });
  const firstTry = orderRequests.filter(entry => entry.body.requestId === order.requestId && entry.body.buyer.name === name).length === 1;
  if (name === "Preco Mudou") return json(response, 409, { code: "PRICE_CHANGED", currentTotal: "1.00" });
  if (name === "Falha Rede" && firstTry) return json(response, 503, {});
  const previous = createdOrders.get(order.requestId);
  if (previous !== undefined && previous !== fingerprint) return json(response, 409, { code: "REQUEST_CONFLICT" });
  createdOrders.set(order.requestId, fingerprint);
  // Committed, but the answer is lost: the storefront sees an ambiguous failure.
  if (name === "Resposta Perdida" && previous === undefined) return json(response, 503, {});
  json(response, previous === undefined ? 201 : 200, { order: syntheticOrder, accessToken: previous === undefined ? orderToken : null, replayed: previous !== undefined, buyerEmail: "PRIVATE_BUYER_FIXTURE" });
}

// SC-045: the catalog API (SC-042) behind the new home, category, search and product pages.
const menu = [{ name: "Camisetas", slug: "camisetas", children: [{ name: "Manga longa", slug: "manga-longa", children: [] }] }, { name: "Outlet", slug: "outlet", children: [] }];
const card = { id, slug: "produto-publico", name: "Produto público de teste", brand: "Marca Ágil", price: "49.90", compareAtPrice: "62.40", discountPercent: 20, currency: "BRL", available: true, sku: "PRIVATE_SKU_FIXTURE", cost: "PRIVATE_COST_FIXTURE" };
const soldOut = { ...card, id: nextId, slug: "outro-produto", name: "Outro produto publicado", brand: null, compareAtPrice: null, discountPercent: null, available: false };
const facets = { categories: [{ slug: "manga-longa", name: "Manga longa", count: 1 }], brands: [{ slug: "agil", name: "Marca Ágil", count: 1 }],
  prices: [{ min: "0.00", max: "50.00", count: 1 }, { min: "5000.00", max: null, count: 1 }], options: [{ name: "Tamanho", values: [{ value: "M", count: 2 }] }] };
const noFacets = { categories: [], brands: [], prices: [], options: [] };
const catalog = (overrides = {}) => ({ store, menu, category: null, search: null, sort: "recentes", page: 1, pageSize: 24, total: 2, products: [card, soldOut], facets, maxInstallments: 6, ...overrides });
const camisetas = { name: "Camisetas", slug: "camisetas", path: [{ name: "Camisetas", slug: "camisetas" }] };

const ROUTES = {
  "/api/public/stores/loja-teste/catalog": () => catalog(),
  "/api/public/stores/loja-teste/catalog?ordem=recentes": () => catalog(),
  "/api/public/stores/loja-teste/catalog?categoria=camisetas": () => catalog({ category: camisetas, total: 30, products: [card] }),
  "/api/public/stores/loja-teste/catalog?categoria=camisetas&marca=agil": () => catalog({ category: camisetas, total: 1, products: [card] }),
  "/api/public/stores/loja-teste/catalog?q=produto": () => catalog({ search: "produto", sort: "relevancia", total: 1, products: [card], facets: noFacets }),
  "/api/public/stores/loja-teste/catalog?q=nada": () => catalog({ search: "nada", sort: "relevancia", total: 0, products: [], facets: noFacets }),
  "/api/public/stores/loja-teste/p/produto-publico": () => ({ movedTo: null, product: { store, product: { ...item, slug: "produto-publico", brand: { name: "Marca Ágil", slug: "agil" },
    categoryPath: [{ name: "Camisetas", slug: "camisetas" }], compareAtPrice: "62.40", discountPercent: 20, variants: [{ ...item.variants[0], compareAtPrice: "62.40" }] }, related: [soldOut], maxInstallments: 6 } }),
  "/api/public/stores/loja-teste/p/endereco-antigo": () => ({ movedTo: "produto-publico", product: null }),
  "/api/public/stores/loja-vazia/catalog?ordem=recentes": () => ({ ...catalog({ menu: [], total: 0, products: [], facets: noFacets, maxInstallments: null }),
    store: { name: "Loja vazia de teste", slug: "loja-vazia", profile: { contactEmail: "x@y.example\"><script>alert(1)</script>", whatsapp: "123", instagram: "a b", logo: false } } }),
  "/api/public/stores/loja-teste": () => ({ ...store, products: [{ ...item, descriptionHtml: "", variants: [], cost: "PRIVATE_COST_FIXTURE", sku: "PRIVATE_SKU_FIXTURE", quantity: 784321 }], categories, category: null, nextCursor: id }),
  [`/api/public/stores/loja-teste/products/${id}/images`]: () => ({ images: [{ id, alt: "Foto de teste", width: 300, height: 300 }] }),
  [`/api/public/stores/loja-teste/products/${id}`]: () => ({ store, product: item }),
  [`/api/public/stores/loja-variavel/products/${nextId}`]: () => ({ store: { ...store, slug: "loja-variavel" }, product: variantItem }),
  [`/api/public/stores/loja-variavel/products/${nextId}/images`]: () => ({ images: [] }),
  "/api/public/stores/loja-vazia": () => ({ name: "Loja vazia de teste", slug: "loja-vazia", profile: { contactEmail: "x@y.example\"><script>alert(1)</script>", whatsapp: "123", instagram: "a b", logo: false }, products: [], categories: [], category: null, nextCursor: null }),
};

function quote(body) {
  // Synthetic backend quote: prices differ from anything the browser could send.
  const lines = JSON.parse(body).items.map(({ variantId, quantity }) => variantId === id
    ? { variantId, quantity, status: "AVAILABLE", productId: id, productName: "Produto público de teste", options: [], unitPrice: "49.90", lineTotal: (4990 * quantity / 100).toFixed(2), sku: "PRIVATE_SKU_FIXTURE", cost: "PRIVATE_COST_FIXTURE" }
    : variantId === nextId
      ? { variantId, quantity, status: "INSUFFICIENT", productId: nextId, productName: "Camiseta com opções", options: [{ name: "Cor", value: "Azul" }], unitPrice: "59.90", lineTotal: (5990 * quantity / 100).toFixed(2), available: 784321 }
      : { variantId, quantity, status: "UNAVAILABLE", productId: null, productName: null, options: null, unitPrice: null, lineTotal: null });
  const subtotal = lines.filter(line => line.status === "AVAILABLE").reduce((sum, line) => sum + Math.round(Number(line.lineTotal) * 100), 0);
  return { currency: "BRL", lines, subtotal: (subtotal / 100).toFixed(2), purchasable: lines.every(line => line.status === "AVAILABLE"),
    delivery: [{ type: "PICKUP", instructions: "Rua Sintética, 10" }, { type: "FIXED_SHIPPING", amount: "12.50", currency: "BRL", area: "Centro (área informativa)" }] };
}

export async function startUpstream() {
  const upstream = createServer(async (request, response) => {
    let body = "";
    for await (const chunk of request) body += chunk;
    upstreamRequests.push({ url: request.url, method: request.method, cookie: request.headers.cookie, authorization: request.headers.authorization, type: request.headers["content-type"], body });
    response.setHeader("Content-Type", "application/json");
    const { method, url } = request;
    if (method === "POST" && url === "/api/public/stores/loja-teste/cart/quote") return json(response, 200, quote(body));
    if (method === "POST" && url === "/api/public/stores/loja-teste/orders") return checkout(JSON.parse(body), request.headers, response);
    if (method === "GET" && url === `/api/public/stores/loja-teste/orders/${orderId}`) {
      orderReads.push(request.headers["x-order-token"]);
      if (request.headers["x-order-token"] !== orderToken) return json(response, 404, {});
      return json(response, 200, { ...syntheticOrder, buyerPhone: "PRIVATE_BUYER_FIXTURE", sku: "PRIVATE_SKU_FIXTURE" });
    }
    if (method === "POST" && url === `/api/public/stores/loja-teste/orders/${orderId}/payment`) {
      paymentRequests.push({ token: request.headers["x-order-token"], body });
      if (request.headers["x-order-token"] !== orderToken) return json(response, 404, {});
      return json(response, paymentReply.status, paymentReply.body);
    }
    if (method !== "GET") return json(response, 403, { message: "Proibido" });
    if (url === "/api/public/stores/loja-teste/logo") { response.setHeader("Content-Type", "image/png"); return response.end(png); }
    if (url === `/api/public/stores/loja-teste/products/${id}/images/primary/content` || url === `/api/public/stores/loja-teste/products/${id}/images/${id}/content`) {
      response.setHeader("Content-Type", "image/png"); response.setHeader("Set-Cookie", "PRIVATE_UPSTREAM_COOKIE=synthetic; Path=/"); return response.end(png);
    }
    if (url === `/api/public/stores/loja-teste/products/${id}/images/${nextId}/content`) {
      response.setHeader("Content-Type", "text/html"); return response.end("PRIVATE_UPSTREAM_ERROR_FIXTURE");
    }
    if (url.startsWith("/api/public/stores/loja-indisponivel")) return json(response, 503, { message: "PRIVATE_UPSTREAM_ERROR_FIXTURE" });
    if (ROUTES[url]) return json(response, 200, ROUTES[url]());
    json(response, 404, { message: "Não encontrado" });
  });
  await new Promise(resolve => upstream.listen(0, "127.0.0.1", resolve));
  return {
    port: upstream.address().port,
    close: async () => { upstream.closeAllConnections(); await new Promise(resolve => upstream.close(resolve)); },
  };
}

// Starts `next start` for this build and waits until it answers.
export async function startNext(port, env, onOutput) {
  const child = spawn(process.execPath, ["node_modules/next/dist/bin/next", "start", "--hostname", "127.0.0.1", "--port", String(port)], {
    cwd: new URL("..", import.meta.url), env: { ...env, NEXT_TELEMETRY_DISABLED: "1" }, stdio: ["ignore", "pipe", "pipe"], windowsHide: true,
  });
  let spawnError;
  child.on("error", error => { spawnError = error; });
  for (const stream of [child.stdout, child.stderr]) stream.on("data", data => onOutput?.(data.toString()));
  for (let attempt = 0; attempt < 60; attempt++) {
    if (spawnError) throw spawnError;
    if (child.exitCode !== null) throw new Error(`Servidor na porta ${port} encerrou antes do teste.`);
    try { await fetch(`http://127.0.0.1:${port}`, { signal: AbortSignal.timeout(1000) }); return child; } catch { await delay(500); }
  }
  child.kill();
  throw new Error(`Servidor na porta ${port} não respondeu.`);
}
