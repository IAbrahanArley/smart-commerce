import assert from "node:assert/strict";
import { createHmac } from "node:crypto";
import { forwardingSecret } from "./smoke-units.mjs";
import { id, orderId, orderReads, orderRequests, orderToken, startNext } from "./smoke-upstream.mjs";

const text = async response => (await response.text()).replaceAll("<!-- -->", "");
const hiddenValue = (html, name) => html.match(new RegExp(`name="${name}" value="([^"]*)"`))?.[1];
const decode = value => value.replaceAll("&quot;", '"').replaceAll("&#x27;", "'").replaceAll("&lt;", "<").replaceAll("&gt;", ">").replaceAll("&amp;", "&");

// Checkout (SC-009) against the synthetic upstream: no-JS server action, idempotent retries,
// path-scoped order cookie, status page and visitor IP handling.
export async function checkoutSmoke({ base, page, cartCookie, port, upstreamPort }) {
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
  const formRequestId = hiddenValue(checkout.html, "requestId");
  assert.match(formRequestId ?? "", /^[0-9a-f-]{36}$/);
  assert.equal(hiddenValue(checkout.html, "total-PICKUP"), "99.80");
  assert.equal(hiddenValue(checkout.html, "total-FIXED_SHIPPING"), "112.30");
  const actionFields = [...checkout.html.matchAll(/<input type="hidden" name="(\$ACTION_[^"]+)"(?: value="([^"]*)")?/g)].map(match => [match[1], decode(match[2] ?? "")]);
  assert.ok(actionFields.length > 0, "Checkout deve funcionar sem JavaScript pela server action.");
  const checkoutPost = async (fields, { target = base, headers = {} } = {}) => {
    const form = new FormData();
    for (const [name, value] of actionFields) form.set(name, value);
    for (const [name, value] of Object.entries({ slug: "loja-teste", requestId: formRequestId, "total-PICKUP": "99.80", "total-FIXED_SHIPPING": "112.30", ...fields })) form.set(name, value);
    return fetch(`${target}/lojas/loja-teste/checkout`, { method: "POST", body: form, redirect: "manual",
      headers: { Origin: target, Cookie: `${buyable}; ADMIN_SESSION=synthetic-not-a-credential`, Authorization: "Bearer synthetic-not-a-credential", ...headers } });
  };
  const buyer = { fulfillment: "PICKUP", name: "Comprador Sintético", email: "pessoa@example.test", phone: "(11) 98888-7777" };

  const invalidCheckout = await checkoutPost({ ...buyer, email: "sem-arroba", phone: "123" });
  const invalidHtml = await text(invalidCheckout);
  assert.equal(invalidCheckout.status, 200);
  assert.match(invalidHtml, /Confira os campos destacados/);
  assert.match(invalidHtml, /id="checkout-email"[^>]*aria-invalid="true"[^>]*aria-describedby="email-error"/);
  assert.match(invalidHtml, /Informe o telefone com DDD/);
  assert.equal(orderRequests.length, 0, "Dados inválidos não chegam ao backend.");
  const noDelivery = await text(await checkoutPost({ ...buyer, fulfillment: "" }));
  assert.match(noDelivery, /href="#checkout-fulfillment-PICKUP"/, "Erro de entrega leva ao primeiro rádio focável.");
  assert.match(noDelivery, /id="checkout-fulfillment-PICKUP"[^>]*aria-describedby="fulfillment-error"/);

  const changed = await checkoutPost({ ...buyer, name: "Preco Mudou" });
  assert.equal(changed.status, 200);
  assert.match(await text(changed), /Os valores mudaram/);
  const ambiguous = await checkoutPost({ ...buyer, name: "Falha Rede" });
  assert.match(await text(ambiguous), /a mesma tentativa será reaproveitada/);
  const retried = await checkoutPost({ ...buyer, name: "Falha Rede" });
  assert.equal(retried.status, 303);
  const sameKey = orderRequests.filter(entry => entry.body.buyer.name === "Falha Rede");
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

  // Order committed but the answer lost: the retry replays it (no token), with a link and no new key.
  const lostKey = "33333333-3333-4333-8333-333333333333";
  const lost = await text(await checkoutPost({ ...buyer, name: "Resposta Perdida", requestId: lostKey }));
  assert.match(lost, /a mesma tentativa será reaproveitada/);
  assert.equal(hiddenValue(lost, "requestId"), lostKey);
  const replay = await checkoutPost({ ...buyer, name: "Resposta Perdida", requestId: lostKey });
  const replayHtml = await text(replay);
  assert.equal(replay.status, 200);
  assert.match(replayHtml, /Seu pedido nº 7 já foi registrado/);
  assert.match(replayHtml, new RegExp(`href="/lojas/loja-teste/pedidos/${orderId}">Ver acompanhamento do pedido`));
  assert.ok(!replay.headers.getSetCookie().some(cookie => cookie.startsWith("sc-order-")), "Replay não reemite token.");
  // Same key, other data after an ambiguous failure: the backend proves an order exists, so the key is kept.
  const conflictKey = "44444444-4444-4444-8444-444444444444";
  await checkoutPost({ ...buyer, name: "Resposta Perdida", requestId: conflictKey });
  const conflict = await text(await checkoutPost({ ...buyer, name: "Resposta Perdida", phone: "(11) 97777-6666", requestId: conflictKey }));
  assert.match(conflict, /já foi registrado com os dados enviados antes/);
  assert.equal(hiddenValue(conflict, "requestId"), conflictKey, "REQUEST_CONFLICT não gera chave nova.");
  assert.match(conflict, /href="\/lojas\/loja-teste\/checkout">Fazer um pedido separado/);
  const again = await text(await checkoutPost({ ...buyer, name: "Resposta Perdida", phone: "(11) 97777-6666", requestId: hiddenValue(conflict, "requestId") }));
  assert.match(again, /já foi registrado com os dados enviados antes/, "Reenviar não cria segundo pedido.");

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
  await checkoutPost({ ...buyer, name: "Ip Forjado", requestId: "22222222-2222-4222-8222-222222222222" }, { headers: { "X-Forwarded-For": "203.0.113.99" } });
  const spoofed = orderRequests.find(entry => entry.body.buyer.name === "Ip Forjado");
  assert.ok(spoofed, "Tentativa com X-Forwarded-For forjado deve chegar ao backend.");
  assert.equal(spoofed.headers["x-sc-client-ip"], undefined, "X-Forwarded-For do visitante não pode virar o IP assinado.");
  const foreignCheckout = await fetch(`${base}/lojas/loja-teste/checkout`, { method: "POST", body: new FormData(), redirect: "manual", headers: { Origin: "https://foreign.example", Cookie: buyable } });
  assert.notEqual(foreignCheckout.status, 303);

  // Behind a declared trusted proxy, the last hop is signed with the shared secret (same HMAC as the backend).
  const trustedPort = port + 2;
  const trusted = await startNext(trustedPort, { ...process.env, STOREFRONT_API_ORIGIN: `http://127.0.0.1:${upstreamPort}`, STOREFRONT_CHECKOUT_ENABLED: "true",
    STOREFRONT_FORWARDING_SECRET: forwardingSecret, STOREFRONT_TRUST_FORWARDED_FOR: "true" });
  try {
    await checkoutPost({ ...buyer, name: "Proxy Confiavel", requestId: "55555555-5555-4555-8555-555555555555" },
      { target: `http://127.0.0.1:${trustedPort}`, headers: { "X-Forwarded-For": "203.0.113.99, 198.51.100.7" } });
  } finally {
    trusted.kill();
  }
  const proxied = orderRequests.find(entry => entry.body.buyer.name === "Proxy Confiavel");
  assert.ok(proxied, "Checkout atrás de proxy confiável deve chegar ao backend.");
  assert.equal(proxied.headers["x-sc-client-ip"], "198.51.100.7", "Somente o último salto (do proxy) é assinado.");
  const timestamp = proxied.headers["x-sc-client-ip-timestamp"];
  assert.ok(Math.abs(Number(timestamp) - Date.now() / 1000) < 60);
  assert.equal(proxied.headers["x-sc-client-ip-signature"], createHmac("sha256", forwardingSecret).update(`198.51.100.7\n${timestamp}`).digest("hex"));
  return buyable;
}
