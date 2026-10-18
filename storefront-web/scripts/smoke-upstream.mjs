import { spawn } from "node:child_process";
import { createServer } from "node:http";
import { setTimeout as delay } from "node:timers/promises";

// Ephemeral synthetic upstream: verifies SSR projection and credential separation, without a real account.
export const id = "00000000-0000-0000-0000-000000000001";
export const nextId = "00000000-0000-0000-0000-000000000002";
export const missingId = "00000000-0000-0000-0000-000000000003";
export const orderId = "00000000-0000-0000-0000-0000000000a7";
export const orderToken = "SyntheticOrderToken_0123456789abcdefghijklm";
export const png = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j1ioAAAAASUVORK5CYII=", "base64");
export const upstreamRequests = [];
export const orderRequests = [];
export const orderReads = [];

const store = { name: "Loja de teste da vitrine", slug: "loja-teste" };
const item = { id, name: "Produto público de teste", price: "49.90", currency: "BRL", available: true, kind: "SIMPLE", descriptionHtml: "<p>Descrição <strong>segura</strong>.</p>", variants: [{ id, price: "49.90", currency: "BRL", available: true, options: [] }] };
const variantItem = { ...item, id: nextId, name: "Camiseta com opções", kind: "VARIABLE", variants: [{ id, price: "59.90", currency: "BRL", available: false, options: [{ name: "Cor", value: "Azul" }, { name: "Tamanho", value: "M" }], sku: "PRIVATE_SKU_FIXTURE", cost: "PRIVATE_COST_FIXTURE", quantity: 784321 }] };
const syntheticOrder = { id: orderId, number: 7, status: "PENDING_PAYMENT", currency: "BRL", subtotal: "99.80", shipping: "0.00", total: "99.80",
  items: [{ productName: "Produto público de teste", options: [], unitPrice: "49.90", quantity: 2, lineTotal: "99.80", sku: "PRIVATE_SKU_FIXTURE" }],
  fulfillment: { type: "PICKUP", text: "Rua Sintética, 10" }, reservationExpiresAt: "2026-10-18T15:30:00Z", createdAt: "2026-10-18T15:00:00Z" };
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

const ROUTES = {
  "/api/public/stores/loja-teste": () => ({ ...store, products: [{ ...item, descriptionHtml: "", variants: [], cost: "PRIVATE_COST_FIXTURE", sku: "PRIVATE_SKU_FIXTURE", quantity: 784321 }], nextCursor: id }),
  [`/api/public/stores/loja-teste?after=${id}`]: () => ({ ...store, products: [{ ...item, descriptionHtml: "", variants: [], id: nextId, name: "Outro produto publicado", available: false }], nextCursor: null }),
  [`/api/public/stores/loja-teste/products/${id}/images`]: () => ({ images: [{ id, alt: "Foto de teste", width: 300, height: 300 }] }),
  [`/api/public/stores/loja-teste/products/${id}`]: () => ({ store, product: item }),
  [`/api/public/stores/loja-variavel/products/${nextId}`]: () => ({ store: { ...store, slug: "loja-variavel" }, product: variantItem }),
  [`/api/public/stores/loja-variavel/products/${nextId}/images`]: () => ({ images: [] }),
  "/api/public/stores/loja-vazia": () => ({ name: "Loja vazia de teste", slug: "loja-vazia", products: [], nextCursor: null }),
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
    if (method !== "GET") return json(response, 403, { message: "Proibido" });
    if (url === `/api/public/stores/loja-teste/products/${id}/images/primary/content` || url === `/api/public/stores/loja-teste/products/${id}/images/${id}/content`) {
      response.setHeader("Content-Type", "image/png"); response.setHeader("Set-Cookie", "PRIVATE_UPSTREAM_COOKIE=synthetic; Path=/"); return response.end(png);
    }
    if (url === `/api/public/stores/loja-teste/products/${id}/images/${nextId}/content`) {
      response.setHeader("Content-Type", "text/html"); return response.end("PRIVATE_UPSTREAM_ERROR_FIXTURE");
    }
    if (url === "/api/public/stores/loja-indisponivel") return json(response, 503, { message: "PRIVATE_UPSTREAM_ERROR_FIXTURE" });
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
