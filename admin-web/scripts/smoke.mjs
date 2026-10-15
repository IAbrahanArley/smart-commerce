import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { setTimeout as delay } from "node:timers/promises";

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
  const missing = await fetch(`${baseUrl}/pagina-inexistente-sc001`, { signal: AbortSignal.timeout(5000) });
  assert.equal(missing.status, 404);
  assert.match(await missing.text(), /Página não encontrada/);
  console.log("Smoke aprovado: home 200, HTML pt-BR, conteúdo administrativo e página 404.");
} finally {
  server.kill();
}
