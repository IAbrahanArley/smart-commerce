import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { createServer } from "node:http";
import { setTimeout as delay } from "node:timers/promises";
import { formatBRL } from "../src/lib/price.ts";

assert.equal(formatBRL("99999999999999999.99").replace(/\s/g, " "), "R$ 99.999.999.999.999.999,99");
assert.equal(formatBRL("0.01").replace(/\s/g, " "), "R$ 0,01");
assert.equal(formatBRL("1.001"), "Preço indisponível");

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
const upstream = createServer((request, response) => {
  upstreamRequests.push({ url: request.url, method: request.method, cookie: request.headers.cookie, authorization: request.headers.authorization });
  response.setHeader("Content-Type", "application/json");
  if (request.url === "/api/public/stores/loja-teste") {
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
  env: { ...process.env, NEXT_TELEMETRY_DISABLED: "1", STOREFRONT_API_ORIGIN: `http://127.0.0.1:${upstreamPort}` },
  stdio: ["ignore", "pipe", "pipe"], windowsHide: true,
});
let output = "";
let spawnError;
server.on("error", error => { spawnError = error; });
for (const stream of [server.stdout, server.stderr]) stream.on("data", data => { output = (output + data.toString()).slice(-8000); });

async function page(path) {
  const response = await fetch(`${base}${path}`, {
    signal: AbortSignal.timeout(10000),
    headers: { Cookie: "ADMIN_SESSION=synthetic-not-a-credential", Authorization: "Bearer synthetic-not-a-credential" },
  });
  const html = await response.text();
  assert.equal(response.headers.get("referrer-policy"), "no-referrer", path);
  assert.equal(response.headers.get("set-cookie"), null, path);
  assert.match(response.headers.get("cache-control") ?? "", /no-store/, path);
  assert.match(html, /<html[^>]*lang="pt-BR"/, path);
  for (const privateContent of ["PRIVATE_COST_FIXTURE", "PRIVATE_SKU_FIXTURE", "PRIVATE_UPSTREAM_ERROR_FIXTURE", "ADMIN_SESSION", "Administração do Smart Commerce", "current-password"]) {
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
  const unavailable = await page("/lojas/loja-indisponivel");
  assert.ok([200, 500].includes(unavailable.response.status));
  assert.ok(upstreamRequests.length >= 6, "Fluxos públicos precisam consultar o upstream.");
  for (const request of upstreamRequests) {
    assert.equal(request.method, "GET");
    assert.ok(request.url.startsWith("/api/public/stores/"));
    assert.equal(request.cookie, undefined, "SSR não deve encaminhar cookies do visitante.");
    assert.equal(request.authorization, undefined, "SSR não deve encaminhar Authorization.");
  }
  console.log("Smoke aprovado: catálogo/detalhe SSR, descrição HTML, variantes, fotos/proxy MIME, preços exatos, paginação, vazio, 404, projeção pública, no-store e ausência de cookies/Authorization no upstream. Não substitui browser/API real.");
} finally {
  server.kill();
  upstream.closeAllConnections();
  await new Promise(resolve => upstream.close(resolve));
}
