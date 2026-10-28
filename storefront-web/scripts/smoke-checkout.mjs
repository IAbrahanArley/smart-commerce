import assert from "node:assert/strict";
import { createHmac } from "node:crypto";
import { forwardingSecret } from "./smoke-units.mjs";
import { checkoutUrl, id, lookupRequests, orderId, orderReads, orderRequests, orderToken, paymentReply, paymentRequests, startNext } from "./smoke-upstream.mjs";

const text = async response => (await response.text()).replaceAll("<!-- -->", "");
const hiddenValue = (html, name) => html.match(new RegExp(`name="${name}" value="([^"]*)"`))?.[1];
const decode = value => value.replaceAll("&quot;", '"').replaceAll("&#x27;", "'").replaceAll("&lt;", "<").replaceAll("&gt;", ">").replaceAll("&amp;", "&");

// Checkout (SC-009) against the synthetic upstream: no-JS server action, idempotent retries,
// path-scoped order cookie, status page, Checkout Pro redirect and return page (SC-013) and
// visitor IP handling.
export async function checkoutSmoke({ base, page, cartCookie, port, upstreamPort }) {
  const buyable = cartCookie(JSON.stringify([{ variantId: id, quantity: 2 }]));
  const buyableCart = await page("/lojas/loja-teste/carrinho", buyable);
  assert.match(buyableCart.html, /href="\/lojas\/loja-teste\/checkout"[^>]*>Finalizar pedido/);
  const checkout = await page("/lojas/loja-teste/checkout", buyable);
  assert.equal(checkout.response.status, 200);
  assert.match(checkout.html, /<h1>Finalizar pedido<\/h1>/);
  // SC-046: reduced frame in the checkout: no search, category menu or cart link; contact in sight.
  assert.ok(!checkout.html.includes('id="store-search"'), "Checkout sem busca.");
  assert.ok(!checkout.html.includes('aria-label="Categorias"'), "Checkout sem menu.");
  assert.ok(!checkout.html.includes('class="cart-link"'), "Checkout sem atalho do carrinho.");
  assert.match(checkout.html, /class="checkout-header-contact"/);
  assert.match(checkout.html, /Retirar na loja/);
  assert.match(checkout.html, /Frete R\$\s12,50 · Total R\$\s112,30/);
  assert.match(checkout.html, /Sem custo de entrega · Total R\$\s99,80/);
  assert.match(checkout.html, /são usados apenas pela <span translate="no">Loja de teste da vitrine<\/span>/);
  assert.match(checkout.html, /anonimizados em até 90 dias/);
  // MVP block 1d: payment goes to Mercado Pago and the notice links the shopper privacy page.
  assert.match(checkout.html, /O pagamento é feito no Mercado Pago/);
  assert.ok(checkout.html.includes('href="/privacidade"'), "Link da política no checkout.");
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
  // SC-013: a payable order goes straight to Checkout Pro, started server-side with the new token.
  assert.equal(retried.headers.get("location"), checkoutUrl);
  assert.equal(paymentRequests.at(-1)?.token, orderToken);
  assert.equal(paymentRequests.at(-1)?.body, "", "O início do pagamento não envia corpo nem dados do comprador.");
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
  assert.match(replayHtml, /role="status" class="checkout-alert checkout-alert-info"/, "Replay é informativo, não erro.");
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
  assert.match(status.html, /Ir para o pagamento/);
  assert.equal(orderReads.at(-1), orderToken);

  // Pay again from the order page (no JavaScript): the action redirects to the provider page only.
  const tokenCookie = `sc-order-${orderId}=${orderToken}`;
  const payFields = [...status.html.matchAll(/<input type="hidden" name="(\$ACTION_[^"]+)"(?: value="([^"]*)")?/g)].map(match => [match[1], decode(match[2] ?? "")]);
  assert.ok(payFields.length > 0, "Pagar deve funcionar sem JavaScript pela server action.");
  const payPost = (origin = base) => {
    const form = new FormData();
    for (const [name, value] of payFields) form.set(name, value);
    form.set("slug", "loja-teste"); form.set("orderId", orderId);
    return fetch(`${base}/lojas/loja-teste/pedidos/${orderId}`, { method: "POST", body: form, redirect: "manual", headers: { Origin: origin, Cookie: tokenCookie } });
  };
  const paid = await payPost();
  assert.equal(paid.status, 303);
  assert.equal(paid.headers.get("location"), checkoutUrl);
  for (const [status, body, message] of [
    [200, { checkoutUrl: "https://evil.example/checkout" }, /Não foi possível abrir o pagamento agora/],
    [502, { code: "PAYMENT_FAILED" }, /Não foi possível abrir o pagamento agora/],
    [409, { code: "PAYMENT_UNAVAILABLE" }, /O pagamento online não está disponível nesta loja agora/],
    [409, { code: "NOT_PAYABLE" }, /não está mais aguardando pagamento/],
  ]) {
    Object.assign(paymentReply, { status, body });
    const refused = await payPost();
    assert.equal(refused.status, 200, `Pagamento ${status} não redireciona.`);
    assert.match(await text(refused), message);
  }
  Object.assign(paymentReply, { status: 200, body: { checkoutUrl } });
  const payments = paymentRequests.length;
  const foreignPay = await payPost("https://foreign.example");
  assert.notEqual(foreignPay.status, 303);
  assert.equal(paymentRequests.length, payments, "Origem externa não inicia pagamento.");

  // Return page: the provider's query string is never trusted; only the backend view is shown.
  const returned = await page(`/lojas/loja-teste/pedidos/${orderId}/pagamento?status=approved&collection_status=approved&payment_id=1&external_reference=${orderId}`, tokenCookie);
  assert.equal(returned.response.status, 200);
  assert.match(returned.html, /Pagamento em confirmação/);
  // Visible text only: Next serializes the requested URL (with its query) in the RSC payload.
  const visible = returned.html.replace(/<script[\s\S]*?<\/script>/g, "").replace(/<[^>]+>/g, " ");
  assert.ok(!/pagamento (foi )?aprovado|pagamento confirmado|approved/i.test(visible), "Retorno não declara pagamento aprovado pela URL.");
  assert.match(returned.html, /Tentar pagar novamente/);
  assert.match(returned.html, new RegExp(`href="/lojas/loja-teste/pedidos/${orderId}"`));
  const anonymous = await page(`/lojas/loja-teste/pedidos/${orderId}/pagamento?status=approved`, undefined);
  assert.match(anonymous.html, /Página não encontrada/, "Retorno sem o cookie do pedido é 404.");
  for (const [path, cookie] of [[`/lojas/loja-teste/pedidos/${orderId}`, undefined], [`/lojas/loja-teste/pedidos/${orderId}`, `sc-order-${orderId}=${"x".repeat(43)}`], [`/lojas/loja-vazia/pedidos/${orderId}`, `sc-order-${orderId}=${orderToken}`], ["/lojas/loja-teste/pedidos/invalido", undefined]]) {
    const missing = await page(path, cookie);
    assert.match(missing.html, /Página não encontrada/, `${path} ${cookie ? "com cookie" : "sem cookie"}`);
  }

  // SC-046: "Acompanhar pedido" by number and e-mail, no JavaScript; never offers payment.
  const lookupPage = await page("/lojas/loja-teste/acompanhar-pedido", undefined);
  assert.equal(lookupPage.response.status, 200);
  assert.match(lookupPage.html, /<h1>Acompanhar pedido<\/h1>/);
  const lookupFields = [...lookupPage.html.matchAll(/<input type="hidden" name="(\$ACTION_[^"]+)"(?: value="([^"]*)")?/g)].map(match => [match[1], decode(match[2] ?? "")]);
  assert.ok(lookupFields.length > 0, "Consulta deve funcionar sem JavaScript pela server action.");
  const lookupPost = async (fields, headers = {}) => {
    const form = new FormData();
    for (const [name, value] of lookupFields) form.set(name, value);
    for (const [name, value] of Object.entries({ slug: "loja-teste", ...fields })) form.set(name, value);
    return text(await fetch(`${base}/lojas/loja-teste/acompanhar-pedido`, { method: "POST", body: form, redirect: "manual", headers: { Origin: base, ...headers } }));
  };
  const lookupsBefore = lookupRequests.length;
  const badLookup = await lookupPost({ number: "abc", email: "sem-arroba" });
  assert.match(badLookup, /Informe o número do pedido/);
  assert.match(badLookup, /id="lookup-email"[^>]*aria-invalid="true"/);
  assert.equal(lookupRequests.length, lookupsBefore, "Dados inválidos não chegam ao backend.");
  const found = await lookupPost({ number: "#7", email: "  pessoa@example.test " });
  assert.match(found, /Pedido nº 7: Aguardando pagamento/);
  assert.match(found, /Para pagar, use o navegador em que o pedido foi feito/);
  assert.ok(!found.includes("Ir para o pagamento"), "Consulta por e-mail não oferece pagamento.");
  assert.ok(!found.includes("PRIVATE_BUYER_FIXTURE"), "Consulta não mostra dados do comprador.");
  assert.deepEqual(lookupRequests.at(-1), { number: "7", email: "pessoa@example.test", ip: undefined });
  assert.ok(!lookupRequests.some(entry => entry.email === undefined), "E-mail vai no corpo, nunca na URL.");
  assert.match(await lookupPost({ number: "8", email: "pessoa@example.test" }), /Não encontramos um pedido com esse número e esse e-mail/);
  assert.match(await lookupPost({ number: "7", email: "limitado@example.test" }), /Muitas tentativas de consulta/);

  // A visitor-supplied X-Forwarded-For must not become the signed IP (the per-IP limit would be bypassed).
  await checkoutPost({ ...buyer, name: "Ip Forjado", requestId: "22222222-2222-4222-8222-222222222222" }, { headers: { "X-Forwarded-For": "203.0.113.99" } });
  const spoofed = orderRequests.find(entry => entry.body.buyer.name === "Ip Forjado");
  assert.ok(spoofed, "Tentativa com X-Forwarded-For forjado deve chegar ao backend.");
  assert.equal(spoofed.headers["x-sc-client-ip"], undefined, "X-Forwarded-For do visitante não pode virar o IP assinado.");
  // A real action post (valid action id, buyer and cart) from a foreign origin must not run placeOrder.
  const ordersBefore = orderRequests.length;
  const foreignCheckout = await checkoutPost({ ...buyer, name: "Origem Externa", requestId: "66666666-6666-4666-8666-666666666666" }, { headers: { Origin: "https://foreign.example" } });
  assert.notEqual(foreignCheckout.status, 303);
  assert.equal(orderRequests.length, ordersBefore, "Origem externa não pode criar pedido.");
  assert.ok(!foreignCheckout.headers.getSetCookie().some(cookie => /^sc-(order|cart)-/.test(cookie)), "Origem externa não altera cookies.");

  // Behind a declared trusted proxy, the last hop is signed with the shared secret (same HMAC as the backend).
  const trustedPort = port + 2;
  const trusted = await startNext(trustedPort, { ...process.env, STOREFRONT_API_ORIGIN: `http://127.0.0.1:${upstreamPort}`, STOREFRONT_CHECKOUT_ENABLED: "true",
    STOREFRONT_FORWARDING_SECRET: forwardingSecret, STOREFRONT_TRUST_FORWARDED_FOR: "true", DEPLOYMENT_ENVIRONMENT: "hosted-test" });
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
