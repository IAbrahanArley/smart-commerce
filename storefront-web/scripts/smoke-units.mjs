import assert from "node:assert/strict";
import { createHmac } from "node:crypto";
import { addMoney, formatBRL } from "../src/lib/price.ts";
import { checkoutEnabled, emptyValues, validateCheckout } from "../src/lib/checkout-input.ts";
import { orderCookieName, orderCookieOptions, validOrderToken } from "../src/lib/order-cookie.ts";
import { signedClientIp, trustedClientIp } from "../src/lib/client-ip.ts";
import { addLine, CART_MAX_LINES, parseCart, serializeCart } from "../src/lib/cart-cookie.ts";
import { checkoutPage } from "../src/lib/payment-url.ts";
import { categoryPath, parseMenu } from "../src/lib/menu.ts";
import { formatPhone, pagePath, parseFrame } from "../src/lib/frame.ts";
import { bannerHref, parseHome, sectionHref, sectionQuery } from "../src/lib/home.ts";
import { orderNumber, validateLookup } from "../src/lib/order-lookup.ts";
import { parseOrder } from "../src/lib/orders-view.ts";
import { indexable, jsonLd, parseSitemap, plainText, productLd, publicOrigin, sitemapXml, storeTag } from "../src/lib/seo.ts";
import { verifyRevalidation } from "../src/lib/revalidation.ts";

// Pure rules checked before any server starts.
export const forwardingSecret = "synthetic-forwarding-secret-for-smoke-0001";

assert.equal(formatBRL("99999999999999999.99").replace(/\s/g, " "), "R$ 99.999.999.999.999.999,99");
assert.equal(formatBRL("0.01").replace(/\s/g, " "), "R$ 0,01");

// SC-013: the buyer is only ever redirected to an HTTPS Mercado Pago page.
for (const url of ["https://www.mercadopago.com.br/checkout/v1/redirect?id=1", "https://sandbox.mercadopago.com.br/x", "https://mercadopago.com.ar/x", "https://www.mercadopago.com/x"])
  assert.equal(checkoutPage(url), true, url);
for (const url of [undefined, "", "http://www.mercadopago.com.br/x", "https://mercadopago.com.br.evil.example/x", "https://evilmercadopago.com.br/x", "https://user@www.mercadopago.com.br/x",
  "https://www.mercadopago.com.br:8443/x", "https://www.mercadopago.com.br/x#frag", "javascript:alert(1)", "/lojas/loja-teste", `https://www.mercadopago.com.br/${"x".repeat(2048)}`])
  assert.equal(checkoutPage(url), false, String(url));
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
for (const value of [undefined, "", "1", "TRUE", "yes"]) assert.equal(checkoutEnabled(value, "hosted-test", true), false, String(value));
assert.equal(checkoutEnabled("true", undefined, false), true, "Desenvolvimento local.");
// D6 + ADR-0014 H2: a production build only checks out in the hosted test environment.
assert.equal(checkoutEnabled("true", "hosted-test", true), true);
for (const deployment of [undefined, "", "staging", "PRODUCTION", "HOSTED-TEST"]) assert.equal(checkoutEnabled("true", deployment, true), false, String(deployment));
// MVP block 2b: production checkout needs both the flag and the explicit deployment.
assert.equal(checkoutEnabled("true", "production", true), true);
assert.equal(checkoutEnabled(undefined, "production", true), false);
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

// D4: X-Forwarded-For is trusted only behind a declared proxy; the signature matches the backend HMAC.
assert.equal(trustedClientIp("203.0.113.99", undefined), null);
assert.equal(trustedClientIp("203.0.113.99", "1"), null);
assert.equal(trustedClientIp("203.0.113.99, 198.51.100.7", "true"), "198.51.100.7");
assert.equal(trustedClientIp("evil<script>", "true"), null);
assert.deepEqual(signedClientIp("198.51.100.7", forwardingSecret, 1760000000000), { "X-SC-Client-IP": "198.51.100.7", "X-SC-Client-IP-Timestamp": "1760000000",
  "X-SC-Client-IP-Signature": createHmac("sha256", forwardingSecret).update("198.51.100.7\n1760000000").digest("hex") });
assert.deepEqual(signedClientIp("198.51.100.7", "short", 1760000000000), {});
assert.deepEqual(signedClientIp(null, forwardingSecret, 1760000000000), {});

// SC-044: the category menu keeps only well-formed entries up to 3 levels; anything else empties it.
const menu = { menu: [{ name: "Feminino", slug: "feminino", children: [{ name: "Camisetas", slug: "camisetas", children: [] }] }, { name: "Outlet", slug: "outlet", children: [] }] };
assert.deepEqual(parseMenu(menu).map(item => item.slug), ["feminino", "outlet"]);
assert.equal(parseMenu(menu)[0].children[0].name, "Camisetas");
assert.deepEqual(parseMenu({ menu: [{ name: "X", slug: "Não vale", children: [] }] }), []);
assert.deepEqual(parseMenu({ menu: [{ name: "", slug: "vazio", children: [] }] }), []);
const deep = (level) => level > 4 ? [] : [{ name: `N${level}`, slug: `n${level}`, children: deep(level + 1) }];
assert.deepEqual(parseMenu({ menu: deep(1) }), []);
assert.deepEqual(parseMenu(null), []); assert.deepEqual(parseMenu({}), []);
assert.equal(categoryPath("loja-a", "camisetas"), "/lojas/loja-a/c/camisetas");

// SC-043: the frame keeps valid company fields and pages only.
const frame = parseFrame({ menu: [], company: { cnpj: "11222333000181", phone: "123", facebook: "a b", announcement: "Aviso" }, pages: [{ name: "Sobre", slug: "sobre" }, { name: "X", slug: "../x" }] });
assert.equal(frame.company.cnpj, "11222333000181"); assert.equal(frame.company.phone, null); assert.equal(frame.company.facebook, null);
assert.deepEqual(frame.pages, [{ name: "Sobre", slug: "sobre" }]);
assert.equal(parseFrame({ company: { phone: "x" } }).company, null);
assert.equal(formatPhone("5583999998888"), "(83) 99999-8888"); assert.equal(pagePath("loja-a", "sobre"), "/lojas/loja-a/paginas/sobre");

// SC-043: banners keep internal links only; sections keep their known kinds.
const home = parseHome({
  banners: [
    { id: "8c1d2e3f-4a5b-4c6d-8e7f-90a1b2c3d4e5", alt: "Ok", title: null, linkKind: "SEARCH", linkTarget: "tênis azul", mobileImage: false },
    { id: "8c1d2e3f-4a5b-4c6d-8e7f-90a1b2c3d4e6", alt: "X", linkKind: "CATEGORY", linkTarget: "../admin" },
    { id: "nao-e-id", alt: "X", linkKind: "NONE" },
    { id: "8c1d2e3f-4a5b-4c6d-8e7f-90a1b2c3d4e7", alt: "", linkKind: "NONE" },
  ],
  sections: [{ kind: "BEST", title: "Mais vendidos" }, { kind: "CATEGORY", title: "Sem categoria" }, { kind: "OTHER", title: "X" }],
});
assert.equal(home.banners.length, 1);
assert.equal(bannerHref("loja-teste", home.banners[0]), "/lojas/loja-teste/busca?q=t%C3%AAnis+azul");
assert.equal(bannerHref("loja-teste", { ...home.banners[0], linkKind: "NONE", linkTarget: null }), null);
assert.deepEqual(home.sections, [{ kind: "BEST", title: "Mais vendidos", categorySlug: null }]);
assert.equal(sectionQuery({ kind: "FEATURED", title: "D", categorySlug: null }).toString(), "destaque=1&ordem=recentes");
assert.equal(sectionHref("loja-teste", { kind: "CATEGORY", title: "C", categorySlug: "camisetas" }), "/lojas/loja-teste/c/camisetas");
assert.deepEqual(parseHome(null), { banners: [], sections: [] });

// SC-046: order number and e-mail for "Acompanhar pedido".
assert.equal(orderNumber("#1024"), 1024); assert.equal(orderNumber("nº 7"), 7); assert.equal(orderNumber(" 7 "), 7);
assert.equal(orderNumber("0"), null); assert.equal(orderNumber("7a"), null); assert.equal(orderNumber("1".repeat(13)), null);
assert.deepEqual(validateLookup({ number: "7", email: " Pessoa@Example.test " }), { number: 7, email: "Pessoa@Example.test" });
assert.deepEqual(Object.keys(validateLookup({ number: "", email: "x" }).errors).sort(), ["email", "number"]);
assert.equal(parseOrder({ id: "nao-e-id" }), null);

// SC-047: SEO helpers and the signed revalidation check.
assert.equal(plainText("<p>Camiseta <strong>leve</strong>.</p><ul><li>100% algodão</li></ul>"), "Camiseta leve. 100% algodão");
assert.equal(plainText(`<p>${"palavra ".repeat(40)}</p>`, 30).length <= 30, true);
assert.ok(!jsonLd({ name: "</script><script>alert(1)</script>" }).includes("<"), "JSON-LD não fecha a tag.");
assert.equal(publicOrigin("https://vitrine.example/"), "https://vitrine.example"); assert.equal(publicOrigin("https://x.example/caminho"), null); assert.equal(publicOrigin(undefined), null);
assert.equal(indexable("hosted-test"), false); assert.equal(indexable(undefined), true);
assert.equal(storeTag("loja-a"), "store:loja-a");
assert.ok(sitemapXml("https://v.example", "loja-a", { products: [], categories: [], pages: [] }).includes("<loc>https://v.example/lojas/loja-a</loc>"));
assert.deepEqual(parseSitemap({ products: [{ slug: "ok", updatedAt: "2026-10-20T12:00:00Z" }, { slug: "<x>", updatedAt: "2026-10-20" }], categories: ["a", 1], pages: [] }),
  { products: [{ slug: "ok", updatedAt: "2026-10-20T12:00:00.000Z" }], categories: ["a"], pages: [] });
const offer = productLd("https://v.example", { name: "P", description: "", path: "/lojas/a/p/p", images: [], brand: null, currency: "BRL", variants: [{ name: "M", price: "10.00", available: false }, { name: "G", price: "12.00", available: true }] });
assert.equal(offer.offers.length, 2); assert.equal(offer.offers[0].availability, "https://schema.org/OutOfStock");
const secret = "s".repeat(32), now = Date.UTC(2026, 9, 28, 12), stamp = String(now / 1000);
const signed = createHmac("sha256", secret).update(`loja-a\n${stamp}`).digest("hex");
assert.deepEqual(verifyRevalidation(secret, '{"store":"loja-a"}', stamp, signed, now), { ok: true, store: "loja-a" });
assert.equal(verifyRevalidation(secret, '{"store":"loja-b"}', stamp, signed, now).status, 401);
assert.equal(verifyRevalidation(secret, '{"store":"loja-a"}', stamp, signed, now + 120000).status, 401);
assert.equal(verifyRevalidation(undefined, '{"store":"loja-a"}', stamp, signed, now).status, 404);
assert.equal(verifyRevalidation("curto", '{"store":"loja-a"}', stamp, signed, now).status, 404);
