import assert from "node:assert/strict";
import { createHmac } from "node:crypto";
import { addMoney, formatBRL } from "../src/lib/price.ts";
import { checkoutEnabled, emptyValues, validateCheckout } from "../src/lib/checkout-input.ts";
import { orderCookieName, orderCookieOptions, validOrderToken } from "../src/lib/order-cookie.ts";
import { signedClientIp, trustedClientIp } from "../src/lib/client-ip.ts";
import { addLine, CART_MAX_LINES, parseCart, serializeCart } from "../src/lib/cart-cookie.ts";
import { checkoutPage } from "../src/lib/payment-url.ts";

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
for (const deployment of [undefined, "", "production", "HOSTED-TEST"]) assert.equal(checkoutEnabled("true", deployment, true), false, String(deployment));
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
