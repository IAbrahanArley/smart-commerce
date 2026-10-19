import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { setTimeout as delay } from "node:timers/promises";
import { decimalInput, formatBRL } from "../src/lib/catalog.ts";
import { fulfillmentFields, fulfillmentPayload, fulfillmentText } from "../src/lib/fulfillment.ts";
import { reservedStockMessage, wholeQuantity } from "../src/lib/inventory.ts";
import { storefrontUrl } from "../src/lib/tenant-api.ts";
import { cancelReason, formatDateTime, formatPhone, formatPostalCode, orderCursor, orderStatus, ordersApiPath, ordersPagePath } from "../src/lib/orders.ts";

// Orders (SC-010): URL values are validated before reaching the API; the reason mirrors the backend.
assert.equal(orderStatus("EXPIRED"), "EXPIRED");
for (const invalid of [undefined, "", "PAID", "expired"]) assert.equal(orderStatus(invalid), null, String(invalid));
assert.equal(orderCursor("21"), "21");
for (const invalid of [undefined, "0", "-1", "01", "1e3", "1234567890123456789"]) assert.equal(orderCursor(invalid), null, String(invalid));
assert.equal(ordersApiPath("t/1", "CANCELLED", "21"), "/api/tenants/t%2F1/orders?status=CANCELLED&after=21");
assert.equal(ordersApiPath("t", null, null), "/api/tenants/t/orders");
assert.equal(ordersPagePath("t", "EXPIRED", "40"), "/lojas/t/pedidos?situacao=EXPIRED&apos=40");
assert.deepEqual(cancelReason("  Cliente desistiu  "), { reason: "Cliente desistiu" });
for (const invalid of ["", "ab", "   ", "x".repeat(301), "linha\nquebrada", "zero​width"]) assert.ok("error" in cancelReason(invalid), JSON.stringify(invalid));
assert.ok("reason" in cancelReason("é".repeat(300)));
assert.equal(formatDateTime("2026-10-18T15:30:00Z").replace(/,/g, ""), "18/10/2026 12:30");
assert.equal(formatDateTime(null), "—");
assert.equal(formatPhone("11988887777"), "(11) 98888-7777");
assert.equal(formatPostalCode("01001000"), "01001-000");

const storefrontOrigin = process.env.NEXT_PUBLIC_STOREFRONT_ORIGIN;
delete process.env.NEXT_PUBLIC_STOREFRONT_ORIGIN;
assert.equal(storefrontUrl("minha-loja"), "http://localhost:3001/lojas/minha-loja");
process.env.NEXT_PUBLIC_STOREFRONT_ORIGIN = "javascript:alert(1)";
assert.equal(storefrontUrl("minha-loja"), null);
process.env.NEXT_PUBLIC_STOREFRONT_ORIGIN = "https://lojas.example.test/";
assert.equal(storefrontUrl("minha-loja"), "https://lojas.example.test/lojas/minha-loja");
if (storefrontOrigin === undefined) delete process.env.NEXT_PUBLIC_STOREFRONT_ORIGIN;
else process.env.NEXT_PUBLIC_STOREFRONT_ORIGIN = storefrontOrigin;

assert.equal(wholeQuantity("1"), 1);
assert.equal(wholeQuantity("2147483647"), 2147483647);
for (const invalid of ["0", "-1", "1.5", "1,5", "1e2", "2.000", "2147483648", "", "NaN"]) assert.equal(wholeQuantity(invalid), null, invalid);
assert.match(reservedStockMessage(1200), /^Há 1\.200 unidades reservadas por pedidos pendentes\./);
assert.match(reservedStockMessage(1), /^Há 1 unidade reservada /);
for (const invalid of [undefined, "3", -1, 1.5]) assert.match(reservedStockMessage(invalid), /^Há unidades reservadas /);

assert.equal(decimalInput("49,90"), "49.90");
assert.equal(decimalInput("0"), "0.00");
assert.equal(decimalInput("99999999999999999,99"), "99999999999999999.99");
for (const invalid of ["1,001", "1.000,00", "1e2", "-1", "NaN", "100000000000000000"]) {
  assert.equal(decimalInput(invalid), null, invalid);
}
assert.equal(formatBRL("99999999999999999.99").replace(/\s/g, " "), "R$ 99.999.999.999.999.999,99");
assert.equal(formatBRL("0.01").replace(/\s/g, " "), "R$ 0,01");

const fulfillment = { pickupEnabled: true, pickupInstructions: "  Rua A, 10  ", fixedShippingEnabled: true, fixedShippingAmount: "12,5", fixedShippingArea: "Centro" };
assert.deepEqual(fulfillmentPayload(fulfillment, 3), { body: { pickupEnabled: true, pickupInstructions: "Rua A, 10", fixedShippingEnabled: true, fixedShippingAmount: "12.50", fixedShippingArea: "Centro", version: 3 } });
assert.equal(fulfillmentPayload({ ...fulfillment, fixedShippingAmount: "0" }, 0).body.fixedShippingAmount, "0.00");
assert.deepEqual(fulfillmentPayload({ pickupEnabled: false, pickupInstructions: "", fixedShippingEnabled: false, fixedShippingAmount: "", fixedShippingArea: "" }, 0).body,
  { pickupEnabled: false, pickupInstructions: null, fixedShippingEnabled: false, fixedShippingAmount: null, fixedShippingArea: null, version: 0 });
for (const amount of ["-1", "1,999", "1.000,00", "1e2", ""]) assert.ok(fulfillmentPayload({ ...fulfillment, fixedShippingAmount: amount }, 0).errors?.fixedShippingAmount, amount);
for (const text of ["Rua\nA", "Rua\u200bA", "x".repeat(501), "   "]) assert.ok(fulfillmentPayload({ ...fulfillment, pickupInstructions: text }, 0).errors?.pickupInstructions, JSON.stringify(text));
assert.deepEqual(fulfillmentText("😀".repeat(500)), { text: "😀".repeat(500) });
assert.deepEqual(fulfillmentFields({ pickupEnabled: false, pickupInstructions: null, fixedShippingEnabled: true, fixedShippingAmount: "0.00", currency: "BRL", fixedShippingArea: "Centro", version: 1 }),
  { pickupEnabled: false, pickupInstructions: "", fixedShippingEnabled: true, fixedShippingAmount: "0,00", fixedShippingArea: "Centro" });

const port = Number(process.env.SMOKE_PORT ?? 3100);
assert.ok(Number.isInteger(port) && port > 0 && port < 65536, "SMOKE_PORT deve ser uma porta válida.");
const baseUrl = `http://127.0.0.1:${port}`;

// Detect a conflicting listener before starting so another app cannot satisfy this check.
try {
  await fetch(baseUrl, { signal: AbortSignal.timeout(1000) });
  throw new Error(`A porta ${port} já está em uso. Escolha outra SMOKE_PORT.`);
} catch (error) {
  if (error instanceof Error && error.message.includes("já está em uso")) throw error;
}

const server = spawn(process.execPath, ["node_modules/next/dist/bin/next", "start", "--hostname", "127.0.0.1", "--port", String(port)], {
  cwd: new URL("..", import.meta.url),
  env: { ...process.env, NEXT_TELEMETRY_DISABLED: "1" },
  stdio: ["ignore", "pipe", "pipe"],
  windowsHide: true,
});
let output = "";
let spawnError;
server.on("error", (error) => { spawnError = error; });
for (const stream of [server.stdout, server.stderr]) {
  stream.on("data", (data) => { output = (output + data.toString()).slice(-8000); });
}

try {
  let home;
  for (let attempt = 0; attempt < 60; attempt++) {
    if (spawnError) throw spawnError;
    assert.equal(server.exitCode, null, `O servidor encerrou antes do teste.\n${output}`);
    try {
      home = await fetch(baseUrl, { signal: AbortSignal.timeout(1000) });
      break;
    } catch {
      await delay(500);
    }
  }
  assert.ok(home, `O servidor não respondeu.\n${output}`);
  assert.equal(home.status, 200);
  assert.match(home.headers.get("content-type") ?? "", /text\/html/);
  const html = await home.text();
  assert.match(html, /<html[^>]*lang="pt-BR"/);
  assert.match(html, /<main[^>]*id="conteudo"/);
  assert.match(html, /<h1[^>]*>Sua loja\.<br\s*\/?>\s*<span>Tudo conectado\.<\/span><\/h1>/);
  assert.ok(html.includes('class="presentation landing-page"'), "Direção visual restrita à home");
  assert.equal((html.match(/<h1[\s>]/g) ?? []).length, 1);
  assert.ok(!html.includes('class="site-header"'), "Landing sem cabeçalho operacional");
  for (const [path, title] of [["/componentes", "Fundamentos e componentes"], ["/admin/produtos", "Demonstração do admin"], ["/loja/produto", "Demonstração da loja"]]) {
    const response = await fetch(`${baseUrl}${path}`, { signal: AbortSignal.timeout(5000) });
    assert.equal(response.status, 200, path);
    const page = await response.text();
    assert.ok(page.includes(title), `${path}: título`);
    assert.ok(!page.includes('class="presentation landing-page"'), `${path}: sem tema da landing`);
    assert.equal((page.match(/<main[\s>]/g) ?? []).length, 1, `${path}: main único`);
    assert.equal((page.match(/<h1[\s>]/g) ?? []).length, 1, `${path}: h1 único`);
    assert.equal(response.headers.get("referrer-policy"), "no-referrer", path);
  }
  for (const [path, title, field] of [
    ["/cadastro", "Criar conta", 'name="email"'],
    ["/entrar", "Entrar", 'autoComplete="current-password"'],
    ["/confirmar-email", "Confirmar e-mail e definir senha", 'autoComplete="new-password"'],
    ["/conta", "Minha conta", 'aria-label="Navegação da conta"'],
    ["/lojas", "Minhas lojas", "Consultando suas lojas"],
    ["/lojas/00000000-0000-0000-0000-000000000000", "Minha loja", "Consultando sua loja"],
    ["/lojas/00000000-0000-0000-0000-000000000000/entrega", "Entrega", "Consultando a configuração de entrega"],
    ["/lojas/00000000-0000-0000-0000-000000000000/pedidos?situacao=EXPIRED", "Pedidos", 'name="situacao"'],
    ["/lojas/00000000-0000-0000-0000-000000000000/pedidos/00000000-0000-0000-0000-000000000001", "Consultando pedido", 'aria-label="Trilha de navegação"'],
    ["/lojas/00000000-0000-0000-0000-000000000000/produtos", "Produtos", "Consultando catálogo"],
    ["/lojas/00000000-0000-0000-0000-000000000000/produtos/novo", "Novo produto", 'aria-label="Navegação da loja"'],
    ["/lojas/00000000-0000-0000-0000-000000000000/produtos/00000000-0000-0000-0000-000000000001", "Editar produto", 'aria-label="Trilha de navegação"'],
    ["/lojas/00000000-0000-0000-0000-000000000000/produtos/00000000-0000-0000-0000-000000000001/estoque", "Estoque do produto", 'aria-label="Trilha de navegação"'],
  ]) {
    const response = await fetch(`${baseUrl}${path}`, { signal: AbortSignal.timeout(5000) });
    assert.equal(response.status, 200, path);
    assert.equal(response.headers.get("referrer-policy"), "no-referrer", path);
    const page = await response.text();
    assert.ok(page.includes(title), `${path}: título`);
    assert.ok(page.includes(field), `${path}: conteúdo semântico`);
    assert.ok(!page.includes('class="presentation landing-page"'), `${path}: sem tema da landing`);
  }
  // Route ids must be canonical UUIDs before any API path is built from them.
  for (const path of ["/lojas/nao-e-uuid/pedidos", "/lojas/00000000-0000-0000-0000-000000000000/pedidos/nao-e-uuid"]) {
    const invalid = await fetch(`${baseUrl}${path}`, { signal: AbortSignal.timeout(5000) });
    // The root loading boundary streams before notFound(), so the status may be 200; the result
    // is still the not-found page and the orders screen (which would call the API) never renders.
    assert.ok([200, 404].includes(invalid.status), path);
    const html = await invalid.text();
    assert.match(html, /Página não encontrada/, path);
    assert.ok(!html.includes('name="situacao"') && !html.includes("Consultando pedido"), path);
  }
  const missing = await fetch(`${baseUrl}/pagina-inexistente-sc001`, { signal: AbortSignal.timeout(5000) });
  assert.equal(missing.status, 404);
  assert.match(await missing.text(), /Página não encontrada/);
  console.log("Smoke aprovado: home, cadastro, login, confirmação, conta com navegação da conta, minhas lojas, detalhe de loja, página de entrega, pedidos (lista com filtro na URL e detalhe), catálogo, novo produto com navegação da loja, edição e estoque com trilha de navegação, estoque (mensagem RESERVED_STOCK), validações de valores, headers de privacidade e 404. Não substitui teste de browser/API.");
} finally {
  server.kill();
}
