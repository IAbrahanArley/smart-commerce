import assert from "node:assert/strict";
import { createHmac } from "node:crypto";
import { id, upstreamRequests } from "./smoke-upstream.mjs";

// SC-047 against the synthetic upstream: metadata, canonical and JSON-LD, store sitemap and robots,
// the 60 s cache of backend reads keyed by store and its signed invalidation.

export const revalidationSecret = "synthetic-revalidation-secret-for-smoke-01";

const text = async response => (await response.text()).replaceAll("<!-- -->", "");

function ld(html, type) {
  for (const match of html.matchAll(/<script type="application\/ld\+json">([\s\S]*?)<\/script>/g)) {
    const data = JSON.parse(match[1]);
    if (data["@type"] === type) return data;
  }
  return null;
}

function revalidate(base, body, { secret = revalidationSecret, timestamp = Math.floor(Date.now() / 1000), signature } = {}) {
  const store = JSON.parse(body).store ?? "";
  return fetch(`${base}/interno/revalidar`, {
    method: "POST", body, headers: {
      "Content-Type": "application/json", "X-SC-Revalidate-Timestamp": String(timestamp),
      "X-SC-Revalidate-Signature": signature ?? createHmac("sha256", secret).update(`${store}\n${timestamp}`).digest("hex"),
    },
  });
}

const reads = url => upstreamRequests.filter(request => request.method === "GET" && request.url === url).length;

export async function seoSmoke(base, product, home) {
  // Product: own title, canonical by address, Open Graph photo, Product and breadcrumb JSON-LD.
  assert.match(product.html, /<title>Produto público de teste \| Loja de teste da vitrine<\/title>/);
  assert.ok(product.html.includes('<link rel="canonical" href="https://vitrine.example/lojas/loja-teste/p/produto-publico"/>'));
  assert.ok(product.html.includes(`<meta property="og:image" content="https://vitrine.example/midia/lojas/loja-teste/produtos/${id}/${id}"/>`));
  // The hosted test environment is never indexed.
  assert.match(product.html, /<meta name="robots" content="noindex, follow"\/>/);
  const item = ld(product.html, "Product");
  assert.ok(item, "JSON-LD Product.");
  assert.equal(item.name, "Produto público de teste");
  assert.equal(item.url, "https://vitrine.example/lojas/loja-teste/p/produto-publico");
  assert.deepEqual(item.brand, { "@type": "Brand", name: "Marca Ágil" });
  assert.equal(item.offers.price, "49.90"); assert.equal(item.offers.priceCurrency, "BRL");
  assert.equal(item.offers.availability, "https://schema.org/InStock");
  assert.equal(item.description, "Descrição segura.");
  assert.ok(!JSON.stringify(item).includes("PRIVATE_"), "JSON-LD sem campos privados.");
  const crumbs = ld(product.html, "BreadcrumbList");
  assert.deepEqual(crumbs.itemListElement.map(entry => entry.name), ["Loja de teste da vitrine", "Camisetas", "Produto público de teste"]);

  // Home: store title and Organization with its public contacts.
  assert.match(home.html, /<title>Loja de teste da vitrine<\/title>/);
  const organization = ld(home.html, "Organization");
  assert.equal(organization.url, "https://vitrine.example/lojas/loja-teste");
  assert.equal(organization.legalName, "Loja Teste LTDA");
  assert.equal(organization.contactPoint.telephone, "+558333334444");
  assert.ok(organization.sameAs.includes("https://www.instagram.com/loja.teste/"));

  // Category: breadcrumb; a filtered view is not indexed and points to the plain category.
  const category = await text(await fetch(`${base}/lojas/loja-teste/c/camisetas?marca=agil`));
  assert.ok(category.includes('<link rel="canonical" href="https://vitrine.example/lojas/loja-teste/c/camisetas"/>'));
  assert.equal(ld(category, "BreadcrumbList").itemListElement.at(-1).item, "https://vitrine.example/lojas/loja-teste/c/camisetas");
  assert.match(await text(await fetch(`${base}/lojas/loja-teste/busca?q=produto`)), /<meta name="robots" content="noindex, follow"\/>/);

  // Sitemap: absolute addresses of the store's own content only; malformed entries are dropped.
  const sitemap = await fetch(`${base}/lojas/loja-teste/sitemap.xml`);
  assert.equal(sitemap.status, 200);
  assert.match(sitemap.headers.get("content-type"), /application\/xml/);
  const xml = await sitemap.text();
  for (const loc of ["https://vitrine.example/lojas/loja-teste", "https://vitrine.example/lojas/loja-teste/c/camisetas",
    "https://vitrine.example/lojas/loja-teste/paginas/trocas-e-devolucoes", "https://vitrine.example/lojas/loja-teste/p/produto-publico"])
    assert.ok(xml.includes(`<loc>${loc}</loc>`), loc);
  assert.ok(xml.includes("<lastmod>2026-10-20T12:00:00.000Z</lastmod>"));
  assert.ok(!xml.includes("fora"), "Endereço malformado fica fora do sitemap.");
  assert.equal((await fetch(`${base}/lojas/loja-inexistente/sitemap.xml`)).status, 404);
  assert.match(await (await fetch(`${base}/robots.txt`)).text(), /Disallow: \/\s*$/m, "Ambiente de teste fechado para buscadores.");

  // Invalidation: refused without a valid, fresh signature for that exact store.
  const body = JSON.stringify({ store: "loja-teste" });
  assert.equal((await fetch(`${base}/interno/revalidar`, { method: "POST", body })).status, 401);
  assert.equal((await revalidate(base, body, { secret: "x".repeat(40) })).status, 401);
  assert.equal((await revalidate(base, body, { timestamp: Math.floor(Date.now() / 1000) - 300 })).status, 401);
  const forOther = createHmac("sha256", revalidationSecret).update(`loja-vazia\n${Math.floor(Date.now() / 1000)}`).digest("hex");
  assert.equal((await revalidate(base, body, { signature: forOther })).status, 401);
  assert.equal((await revalidate(base, JSON.stringify({ store: "../x" }))).status, 400);

  // Cache: a store's reads are served from this server's cache until the store is invalidated, and
  // another store with the same relative path has its own entry.
  const homeRead = "/api/public/stores/loja-teste/catalog?ordem=recentes";
  const otherRead = "/api/public/stores/loja-vazia/catalog?ordem=recentes";
  assert.equal((await revalidate(base, body)).status, 204);
  await fetch(`${base}/lojas/loja-teste`);
  const afterInvalidation = reads(homeRead);
  await fetch(`${base}/lojas/loja-teste`);
  await fetch(`${base}/lojas/loja-teste`);
  assert.equal(reads(homeRead), afterInvalidation, "Leituras da loja vêm do cache (60 s).");
  const otherBefore = reads(otherRead);
  await fetch(`${base}/lojas/loja-vazia`);
  assert.ok(reads(otherRead) <= otherBefore + 1);
  assert.match(await text(await fetch(`${base}/lojas/loja-vazia`)), /Nenhum produto publicado/, "Outra loja nunca recebe o cache desta.");
  assert.equal((await revalidate(base, body)).status, 204);
  await fetch(`${base}/lojas/loja-teste`);
  assert.equal(reads(homeRead), afterInvalidation + 1, "Depois da invalidação, a loja é lida de novo.");
}
