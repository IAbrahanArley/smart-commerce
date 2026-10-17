import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { setTimeout as delay } from "node:timers/promises";
import { decimalInput, formatBRL } from "../src/lib/catalog.ts";
import { reservedStockMessage, wholeQuantity } from "../src/lib/inventory.ts";
import { storefrontUrl } from "../src/lib/tenant-api.ts";

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
  assert.match(html, /<h1[^>]*>Administração do Smart Commerce<\/h1>/);
  for (const [path, title, field] of [
    ["/cadastro", "Criar conta", 'name="email"'],
    ["/entrar", "Entrar", 'autoComplete="current-password"'],
    ["/confirmar-email", "Confirmar e-mail e definir senha", 'autoComplete="new-password"'],
    ["/conta", "Minha conta", "Consultando sua conta"],
    ["/lojas/00000000-0000-0000-0000-000000000000", "Minha loja", "Consultando sua loja"],
    ["/lojas/00000000-0000-0000-0000-000000000000/produtos", "Produtos", "Consultando catálogo"],
    ["/lojas/00000000-0000-0000-0000-000000000000/produtos/00000000-0000-0000-0000-000000000001", "Editar produto", "Consultando catálogo"],
    ["/lojas/00000000-0000-0000-0000-000000000000/produtos/00000000-0000-0000-0000-000000000001/estoque", "Estoque do produto", "Consultando estoque"],
  ]) {
    const response = await fetch(`${baseUrl}${path}`, { signal: AbortSignal.timeout(5000) });
    assert.equal(response.status, 200, path);
    assert.equal(response.headers.get("referrer-policy"), "no-referrer", path);
    const page = await response.text();
    assert.ok(page.includes(title), `${path}: título`);
    assert.ok(page.includes(field), `${path}: conteúdo semântico`);
  }
  const missing = await fetch(`${baseUrl}/pagina-inexistente-sc001`, { signal: AbortSignal.timeout(5000) });
  assert.equal(missing.status, 404);
  assert.match(await missing.text(), /Página não encontrada/);
  console.log("Smoke aprovado: home, cadastro, login, confirmação, conta, detalhe de loja, catálogo, edição, estoque (mensagem RESERVED_STOCK), validações de valores, headers de privacidade e 404. Não substitui teste de browser/API.");
} finally {
  server.kill();
}
