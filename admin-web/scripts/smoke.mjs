import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { setTimeout as delay } from "node:timers/promises";
import { coverUrl, decimalInput, formatBRL, photoCountText } from "../src/lib/catalog.ts";
import { fulfillmentFields, fulfillmentPayload, fulfillmentText } from "../src/lib/fulfillment.ts";
import { reservedStockMessage, wholeQuantity } from "../src/lib/inventory.ts";
import { storefrontUrl } from "../src/lib/tenant-api.ts";
import { backendOrigin, hostedTestEnvironment } from "../src/lib/deployment.ts";
import { alertLink, alertText, parseAlerts } from "../src/lib/alerts.ts";
import { MAX_EXCHANGES, askError, explainHref, explainQuestion, historyFor, outcomeText, parseAnswer, parseStatus, questionError, quotaText, sourceText } from "../src/lib/advisor.ts";
import { authorizationTarget, callbackMessage, parseConnection } from "../src/lib/payments.ts";
import { legalDraft, operatorText, updatedText } from "../src/lib/legal.ts";
import { accountItems, parseCollapsed, searchScore, storeItems } from "../src/lib/navigation.ts";
import { isProtectedPath } from "../src/lib/session-events.ts";
import { categoryError, categoryName, categoryNameError, parseCategories, productCountText } from "../src/lib/categories.ts";
import { instagramHandle, logoFileError, profileErrors, profileFields, profilePayload, whatsappText } from "../src/lib/store-profile.ts";

for (const path of ["/conta", "/lojas", "/lojas/uuid/despesas"]) assert.equal(isProtectedPath(path), true, path);
// Store data (MVP block 1b): contacts shown the way the owner typed them, checked like the backend.
assert.equal(whatsappText("5511988887777"), "(11) 98888-7777"); assert.equal(whatsappText("552133334444"), "(21) 3333-4444");
assert.deepEqual(profileFields({ name: "Loja", contactEmail: null, whatsapp: "5511988887777", instagram: "loja.ana", hasLogo: false, version: 1 }),
  { name: "Loja", contactEmail: "", whatsapp: "(11) 98888-7777", instagram: "@loja.ana" });
assert.deepEqual(profileErrors({ name: "Loja da Ana", contactEmail: "contato@ana.example", whatsapp: "(11) 98888-7777", instagram: "https://instagram.com/loja_ana/" }), {});
assert.deepEqual(profileErrors({ name: "Loja", contactEmail: "", whatsapp: "", instagram: "" }), {}, "Contatos em branco são opcionais.");
const profileProblems = profileErrors({ name: " ", contactEmail: "sem-arroba", whatsapp: "+1 555 010 0000", instagram: "loja ana" });
assert.deepEqual(Object.keys(profileProblems).sort(), ["contactEmail", "instagram", "name", "whatsapp"]);
assert.equal(instagramHandle("@Loja"), "Loja"); assert.equal(instagramHandle("www.instagram.com/loja/"), "loja");
assert.deepEqual(profilePayload({ name: " Loja ", contactEmail: " ", whatsapp: "11988887777", instagram: "" }, 3),
  { name: "Loja", contactEmail: null, whatsapp: "11988887777", instagram: null, expectedVersion: 3 });
assert.equal(logoFileError(null), "Selecione uma imagem.");
assert.equal(logoFileError({ type: "image/gif", size: 10 }), "Use uma imagem JPEG ou PNG.");
assert.equal(logoFileError({ type: "image/png", size: 6 * 1024 * 1024 }), "Use uma imagem de até 5 MiB.");
assert.equal(logoFileError({ type: "image/png", size: 1024 }), null);
// MVP block 1c: category names follow the backend rule; the list parser rejects foreign shapes.
assert.equal(categoryNameError("  Camisetas   de  verão "), null); assert.equal(categoryName("  Camisetas   de  verão "), "Camisetas de verão");
assert.equal(categoryNameError("   "), "Informe o nome da categoria.");
assert.equal(categoryNameError("Linha\nquebrada"), "Use uma única linha.");
assert.equal(categoryNameError("a".repeat(61)), "Use até 60 caracteres.");
assert.deepEqual(parseCategories([{ id: "c-1", name: "Camisetas", products: 2, extra: true }]), [{ id: "c-1", name: "Camisetas", products: 2 }]);
assert.equal(parseCategories([{ id: "c-1", name: "Camisetas" }]), null);
assert.equal(parseCategories({}), null);
assert.equal(productCountText(0), "Nenhum produto"); assert.equal(productCountText(1), "1 produto"); assert.equal(productCountText(3), "3 produtos");
assert.match(categoryError(409, "NAME_CONFLICT"), /Já existe/); assert.match(categoryError(409, "LIMIT"), /50 categorias/);

// Design system Dashtail: navigation shared by the sidebar and the search; strict, accent-insensitive search.
const navigation = storeItems("t 1", "/lojas/t%201/produtos/abc");
assert.equal(navigation.length, 10);
assert.ok(navigation.every(item => item.href.startsWith("/lojas/t%201")), "Links só do tenant da rota");
assert.deepEqual(navigation.filter(item => item.current).map(item => item.label), ["Produtos"]);
assert.deepEqual(storeItems("t", "/lojas/t").filter(item => item.current).map(item => item.label), ["Visão geral"]);
assert.deepEqual(storeItems("t", "/lojas/t/categorias").filter(item => item.current).map(item => item.label), ["Categorias"]);
assert.deepEqual(accountItems("/conta").filter(item => item.current).map(item => item.href), ["/conta"]);
assert.equal(searchScore("produto Boné de algodão SKU-3", "bone"), 1);
assert.equal(searchScore("Visão geral painel dashboard", "bone"), 0, "Sem correspondência difusa");
assert.equal(searchScore("pedido 1042 Maria Silva", "1042 maria"), 1);
assert.equal(searchScore("pedido 1041 João", "1042"), 0);
assert.equal(searchScore("Entrega frete retirada", "  "), 1);
assert.equal(parseCollapsed("true"), true); assert.equal(parseCollapsed(null), false); assert.equal(parseCollapsed("1"), false);
// MVP block 1d: the legal pages stay a draft until the operator is identified.
assert.equal(legalDraft({ operatorName: null, cnpj: null, contactEmail: "a@b.example", updatedAt: "2026-10-26" }), true);
assert.equal(legalDraft({ operatorName: "Empresa Ltda", cnpj: "00.000.000/0001-00", contactEmail: "a@b.example", updatedAt: "2026-10-26" }), false);
assert.equal(operatorText({ operatorName: "Empresa Ltda", cnpj: "00.000.000/0001-00", contactEmail: "a@b.example", updatedAt: "2026-10-26" }), "Empresa Ltda, inscrita no CNPJ sob o nº 00.000.000/0001-00");
assert.match(operatorText({ operatorName: "Empresa Ltda", cnpj: null, contactEmail: "a@b.example", updatedAt: "2026-10-26" }), /a preencher/);
assert.equal(updatedText({ operatorName: null, cnpj: null, contactEmail: "a@b.example", updatedAt: "2026-10-26" }), "Atualizado em 26/10/2026.");
for (const path of ["/", "/entrar", "/cadastro", "/termos", "/privacidade", "/lojas-publicas"]) assert.equal(isProtectedPath(path), false, path);

// ADR-0014: the /api rewrite only targets HTTPS (or loopback HTTP); the notice needs the exact flag.
assert.equal(backendOrigin(undefined), "http://127.0.0.1:8080");
assert.equal(backendOrigin("https://api.example.test"), "https://api.example.test");
assert.equal(backendOrigin(" https://api.example.test/ "), "https://api.example.test");
for (const invalid of ["http://api.example.test", "https://user:pw@api.example.test", "https://api.example.test/v1", "https://api.example.test?x=1", "javascript:alert(1)", "not a url"]) assert.throws(() => backendOrigin(invalid), invalid);
assert.equal(hostedTestEnvironment("hosted-test"), true);
for (const value of [undefined, "", "production", "HOSTED-TEST"]) assert.equal(hostedTestEnvironment(value), false);
import { cancelReason, formatDateTime, formatPhone, formatPostalCode, orderCursor, orderStatus, ordersApiPath, ordersPagePath, paymentDivergence } from "../src/lib/orders.ts";
import { missingText, percentText, reportPeriod } from "../src/lib/finance.ts";
import { bars, optionsText, shortDay } from "../src/lib/dashboard.ts";

// Dashboard (SC-022): bars scale to the busiest day, an empty period draws flat, labels in pt-BR.
const chart = bars([{ day: "2026-10-01", revenue: "50.00" }, { day: "2026-10-02", revenue: "100.00" }, { day: "2026-10-03", revenue: "0.00" }], 300, 100);
assert.deepEqual(chart.map(bar => bar.height), [50, 100, 0]);
assert.deepEqual(chart.map(bar => bar.y), [50, 0, 100]);
assert.ok(chart.every(bar => bar.x >= 0 && bar.x + bar.width <= 300));
assert.ok(bars([{ day: "2026-10-01", revenue: "0.00" }], 300, 100).every(bar => bar.height === 0), "Período vazio não divide por zero.");
assert.deepEqual(bars([], 300, 100), []);
assert.equal(shortDay("2026-10-05"), "05/10");
assert.equal(optionsText([{ name: "Cor", value: "Azul" }, { name: "Tamanho", value: "M" }]), "Cor: Azul · Tamanho: M");
import { expenseDateErrors, expenseMonth, formatDay, lineText, monthLabel, monthOf, parseAmount, shiftMonth } from "../src/lib/expenses.ts";

// Expense dates: same boundaries as the backend, at São Paulo midnight and on leap days.
const expenseNow = new Date("2026-10-23T02:30:00Z"); // still October 22 in São Paulo
assert.deepEqual(expenseDateErrors("2027-10-22", "2026-10-22", expenseNow), {});
assert.ok(expenseDateErrors("1999-12-31", "", expenseNow).competenceDate);
assert.ok(expenseDateErrors("2027-10-23", "", expenseNow).competenceDate);
assert.ok(expenseDateErrors("2026-02-30", "", expenseNow).competenceDate);
assert.ok(expenseDateErrors("2026-10-22", "2026-10-23", expenseNow).paidOn);
assert.ok(expenseDateErrors("2026-10-22", "1999-12-31", expenseNow).paidOn);
assert.deepEqual(expenseDateErrors("2025-02-28", "", new Date("2024-02-29T15:00:00Z")), {});
assert.ok(expenseDateErrors("2025-03-01", "", new Date("2024-02-29T15:00:00Z")).competenceDate);

// Finance report (SC-021): period from the URL, missing data and percentages in Portuguese.
assert.equal(reportPeriod("LAST_MONTH"), "LAST_MONTH");
for (const invalid of [undefined, "", "last_month", "FOREVER"]) assert.equal(reportPeriod(invalid), "THIS_MONTH");
assert.equal(missingText({ code: "FEES_UNAVAILABLE", count: 1 }), "1 venda sem a taxa do Mercado Pago informada");
assert.equal(missingText({ code: "COST_MISSING", count: 2 }), "2 pedidos com produto sem custo cadastrado");
assert.equal(percentText("15.00"), "15,00%"); assert.equal(percentText(null), "—");

// Expenses (SC-020): month from the URL, Brazilian amounts and one-line texts mirror the backend.
assert.equal(expenseMonth("2026-10", new Date()), "2026-10");
for (const invalid of [undefined, "", "2026-13", "2026-1", "26-10", "2026-10-01"]) assert.equal(expenseMonth(invalid, new Date("2026-10-21T12:00:00Z")), "2026-10");
assert.equal(monthOf(new Date("2026-11-01T02:00:00Z")), "2026-10", "Mês no fuso de São Paulo.");
assert.equal(shiftMonth("2026-01", -1), "2025-12"); assert.equal(shiftMonth("2026-12", 1), "2027-01");
assert.equal(monthLabel("2026-10"), "Outubro de 2026"); assert.equal(formatDay("2026-10-05"), "05/10/2026"); assert.equal(formatDay(null), "—");
for (const [input, expected] of [["12,5", "12.50"], ["1.234,56", "1234.56"], ["1234.56", "1234.56"], ["R$ 80", "80.00"], ["0,01", "0.01"]]) assert.deepEqual(parseAmount(input), { amount: expected }, input);
for (const input of ["0", "0,00", "-5", "1,234", "abc", "1.2.3", ""]) assert.ok("error" in parseAmount(input), input);
assert.deepEqual(lineText("  Aluguel  ", 200, "a descrição"), { text: "Aluguel" });
for (const input of ["ab", "linha\nquebrada", "x".repeat(201)]) assert.ok("error" in lineText(input, 200, "a descrição"), input);

// Orders (SC-010): URL values are validated before reaching the API; the reason mirrors the backend.
assert.equal(orderStatus("EXPIRED"), "EXPIRED");
assert.equal(orderStatus("PAID"), "PAID");
// SC-015: divergences recorded by the reconciliation become plain explanations, never raw codes.
assert.equal(paymentDivergence(null), null);
assert.match(paymentDivergence("PROVIDER_ERROR"), /consulta será repetida/);
assert.match(paymentDivergence("ACCOUNT_CHANGED"), /outra conta Mercado Pago/);
assert.match(paymentDivergence("NOT_CONNECTED"), /reconecte/);
assert.ok(!paymentDivergence("SOMETHING_ELSE").includes("SOMETHING_ELSE"));
assert.equal(orderStatus("REFUNDED"), "REFUNDED");
for (const invalid of [undefined, "", "CHARGEBACK", "paid", "expired"]) assert.equal(orderStatus(invalid), null, String(invalid));
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
assert.equal(formatBRL("-15.00").replace(/\s/g, " "), "-R$ 15,00");
assert.equal(formatBRL("-0.01").replace(/\s/g, " "), "-R$ 0,01");
assert.equal(formatBRL("-99999999999999999.99").replace(/\s/g, " "), "-R$ 99.999.999.999.999.999,99");
assert.equal(formatBRL("invalid"), "Valor indisponível");

// SC-012: only Mercado Pago's HTTPS authorization page is a navigation target; unknown data is dropped.
assert.equal(authorizationTarget({ authorizationUrl: "https://auth.mercadopago.com/authorization?client_id=1&state=x" }), "https://auth.mercadopago.com/authorization?client_id=1&state=x");
for (const url of ["http://auth.mercadopago.com/authorization", "https://auth.mercadopago.com.evil.test/authorization", "https://evil.test/authorization", "https://user:pw@auth.mercadopago.com/authorization", "https://auth.mercadopago.com/other", "javascript:alert(1)", 1])
  assert.equal(authorizationTarget({ authorizationUrl: url }), null, String(url));
assert.equal(authorizationTarget(null), null);
assert.deepEqual(parseConnection({ status: "CONNECTED", accountId: "987654", connectedAt: "2026-10-20T13:00:00.123456Z", accessExpiresAt: "2027-04-18T13:00:00Z", refreshedAt: null, closedAt: null, accessToken: "APP_USR-x" }),
  { status: "CONNECTED", accountId: "987654", connectedAt: "2026-10-20T13:00:00.123456Z", accessExpiresAt: "2027-04-18T13:00:00Z", refreshedAt: null, closedAt: null });
assert.equal(parseConnection({ status: "OTHER" }), null);
assert.equal(parseConnection({ status: "CONNECTED", accountId: "<b>1</b>" }).accountId, null);
assert.equal(callbackMessage("conectado").error, false);
for (const word of ["negado", "expirado", "indisponivel", "erro"]) assert.equal(callbackMessage(word).error, true, word);
assert.equal(callbackMessage("<script>"), null);
assert.equal(callbackMessage(null), null);

// SC-024: alerts keep known codes, severities and targets only, and never word an unknown code.
const parsedAlerts = parseAlerts({ alerts: [
  { code: "LOW_STOCK", severity: "ATTENTION", count: 1, amount: null, target: "PRODUCTS" },
  { code: "MARGIN_NEGATIVE", severity: "CRITICAL", count: null, amount: "-25.00", target: "FINANCE" },
  { code: "UNKNOWN_RULE", severity: "INFO", count: 1, amount: null, target: "PRODUCTS" },
  { code: "LOW_STOCK", severity: "URGENT", count: 1, amount: null, target: "PRODUCTS" },
  { code: "LOW_STOCK", severity: "INFO", count: 1, amount: null, target: "https://evil.test" },
  { code: "MARGIN_NEGATIVE", severity: "CRITICAL", count: null, amount: "abc", target: "FINANCE" },
] });
assert.deepEqual(parsedAlerts.map(alert => alert.code), ["LOW_STOCK", "MARGIN_NEGATIVE"]);
assert.equal(alertText(parsedAlerts[0]), "1 item com estoque baixo (5 unidades ou menos).");
assert.equal(alertText(parsedAlerts[1]).replace(/\s/g, " "), "A margem de contribuição deste mês está negativa: -R$ 25,00.");
for (const [raw, text] of [["-25.00", "-R$ 25,00"], ["-0.50", "-R$ 0,50"], ["-0.00", "R$ 0,00"], ["1234567.89", "R$ 1.234.567,89"], ["--1.00", "Valor indisponível"]])
  assert.equal(formatBRL(raw).replace(/\s/g, " "), text, raw);
assert.equal(alertText({ code: "ORDERS_EXPIRING", severity: "ATTENTION", count: 2, amount: null, target: "ORDERS" }), "2 pedidos aguardando pagamento com reserva terminando em até 10 minutos.");
assert.equal(alertLink(parsedAlerts[0], "t-1").href, "/lojas/t-1/produtos");
assert.equal(alertLink({ ...parsedAlerts[0], target: "ORDERS" }, "t-1").href, "/lojas/t-1/pedidos?situacao=PENDING_PAYMENT");
assert.equal(parseAlerts(null), null);

// Assistant (SC-027): answers keep only known sources, outcomes are worded, the conversation is short.
assert.deepEqual(parseStatus({ enabled: true, monthlyQuota: 100, usedThisMonth: 3, remaining: 97 }), { enabled: true, monthlyQuota: 100, usedThisMonth: 3, remaining: 97 });
for (const invalid of [null, {}, { enabled: "yes", monthlyQuota: 1, usedThisMonth: 0, remaining: 1 }, { enabled: true, monthlyQuota: -1, usedThisMonth: 0, remaining: 0 }]) assert.equal(parseStatus(invalid), null);
const advisorAnswer = parseAnswer({ outcome: "ANSWERED", answer: "Receita de R$ 150,00.", sources: [
  { tool: "relatorio_periodo", period: "THIS_MONTH", metric: "receita bruta" },
  { tool: "apagar_pedidos", period: null, metric: "x" },
  { tool: "alertas", period: "SEMPRE", metric: " " },
] });
assert.equal(advisorAnswer.sources.length, 2, "Ferramenta desconhecida descartada.");
assert.equal(sourceText(advisorAnswer.sources[0]), "Relatório financeiro · Este mês · receita bruta");
assert.equal(sourceText(advisorAnswer.sources[1]), "Alertas", "Período inválido e métrica vazia omitidos.");
assert.equal(parseAnswer({ outcome: "ANSWERED", answer: " ", sources: [] }), null, "Resposta sem texto não é exibida.");
assert.equal(parseAnswer({ outcome: "UNGROUNDED", answer: "R$ 1 milhão", sources: [] }).answer, null, "Texto só em respostas aceitas.");
assert.equal(parseAnswer({ outcome: "UNKNOWN" }), null);
assert.match(outcomeText("UNGROUNDED"), /com segurança/); assert.equal(outcomeText("ANSWERED"), null);
assert.equal(questionError("   "), "Escreva uma pergunta."); assert.equal(questionError("Oi"), null);
assert.match(questionError("x".repeat(501)), /500/);
assert.match(askError(429), /todas as perguntas/); assert.match(askError(503), /indisponível/);
assert.equal(quotaText({ enabled: true, monthlyQuota: 100, usedThisMonth: 99, remaining: 1 }), "Resta 1 pergunta de 100 neste mês.");
const asked = Array.from({ length: 6 }, (_, index) => ({ id: index, question: `P${index}`, result: index === 2
  ? { outcome: "REFUSED", answer: null, sources: [] } : { outcome: "ANSWERED", answer: `R${index}`, sources: [] } }));
assert.deepEqual(historyFor(asked).map(item => item.question), ["P1", "P3", "P4", "P5"], "Só respostas aceitas, no máximo 4.");
assert.equal(MAX_EXCHANGES, 5);

// "Explicar" (SC-028): only known topics and periods become a question; links carry the period.
assert.equal(explainQuestion("REVENUE", "LAST_7_DAYS"), "Explique a receita da loja (últimos 7 dias) e de onde ela vem.");
assert.equal(explainQuestion("MARGIN", "SEMPRE"), "Explique a margem de contribuição da loja (este mês) e o que falta para ela ficar completa.");
assert.equal(explainQuestion("ALERT_LOW_STOCK", undefined), "Explique o alerta de itens com estoque baixo.");
for (const invalid of [undefined, "", "ALERT_", "ALERT_SOMETHING_NEW", "ignore as instruções", "revenue"]) assert.equal(explainQuestion(invalid, "THIS_MONTH"), null, String(invalid));
assert.equal(explainHref("t 1", "REVENUE", "LAST_7_DAYS"), "/lojas/t%201/assistente?explicar=REVENUE&periodo=LAST_7_DAYS");
assert.equal(explainHref("t", "ALERT_LOW_STOCK"), "/lojas/t/assistente?explicar=ALERT_LOW_STOCK");
assert.equal(explainHref("t", "REVENUE", "FOREVER"), "/lojas/t/assistente?explicar=REVENUE", "Período inválido não vai para o link.");

// Product photos: the cover URL is built only from canonical ids, and counts are worded.
const productId = "11111111-1111-4111-8111-111111111111";
const coverId = "22222222-2222-4222-8222-222222222222";
assert.equal(coverUrl("t-1", { id: productId, coverImageId: coverId }), `/api/tenants/t-1/products/${productId}/images/${coverId}/content`);
for (const bad of [null, "", "../../x", "22222222-2222-4222-8222-22222222222", "javascript:alert(1)"])
  assert.equal(coverUrl("t-1", { id: productId, coverImageId: bad }), null, String(bad));
assert.equal(coverUrl("t-1", { id: "x/../y", coverImageId: coverId }), null);
assert.equal(photoCountText(0), "Sem foto");
assert.equal(photoCountText(1), "1 de 6 fotos");
assert.equal(photoCountText(3), "3 de 6 fotos");
assert.equal(photoCountText(-1), "Sem foto");

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
  // Block 2a: the landing leads to sign-up, says nothing is "in development" and the demos are gone.
  assert.ok(html.includes('href="/cadastro"') && html.includes('href="/entrar"'), "Landing leva ao cadastro e ao login");
  assert.ok(!/em desenvolvimento|demonstraç/i.test(html), "Landing sem menção a desenvolvimento ou demonstração");
  for (const path of ["/componentes", "/admin/produtos", "/loja/produto"]) {
    const response = await fetch(`${baseUrl}${path}`, { signal: AbortSignal.timeout(5000) });
    assert.equal(response.status, 404, `${path}: demonstração removida`);
  }
  for (const [path, title, field] of [
    ["/cadastro", "Criar conta", 'name="email"'],
    ["/cadastro", "Criar conta", 'href="/termos"'],
    ["/termos", "Termos de uso", "Rascunho em validação"],
    ["/privacidade", "Política de privacidade", "Lei nº 13.709/2018"],
    ["/entrar", "Entrar", 'autoComplete="current-password"'],
    ["/confirmar-email", "Confirmar e-mail e definir senha", 'autoComplete="new-password"'],
    ["/esqueci-senha", "Esqueci minha senha", 'name="email"'],
    ["/redefinir-senha", "Redefinir senha", 'autoComplete="new-password"'],
    ["/entrar", "Entrar", 'href="/esqueci-senha"'],
    ["/conta", "Minha conta", 'aria-label="Navegação da conta"'],
    ["/lojas", "Minhas lojas", "Consultando suas lojas"],
    ["/lojas/00000000-0000-0000-0000-000000000000", "Minha loja", "Consultando sua loja"],
    ["/lojas/00000000-0000-0000-0000-000000000000/entrega", "Entrega", "Consultando a configuração de entrega"],
    ["/lojas/00000000-0000-0000-0000-000000000000/dados", "Dados da loja", "Consultando os dados da loja"],
    ["/lojas/00000000-0000-0000-0000-000000000000/pagamentos?mercadoPago=negado", "Pagamentos", "A autorização foi recusada no Mercado Pago"],
    ["/lojas/00000000-0000-0000-0000-000000000000/despesas?mes=2026-09", "Despesas", "Setembro de 2026"],
    ["/lojas/00000000-0000-0000-0000-000000000000/financeiro?periodo=LAST_7_DAYS", "Financeiro", 'value="LAST_7_DAYS" selected'],
    ["/lojas/00000000-0000-0000-0000-000000000000/assistente", "Assistente", "Consultando o assistente"],
    ["/lojas/00000000-0000-0000-0000-000000000000/assistente?explicar=REVENUE&periodo=LAST_7_DAYS", "Assistente", "Consultando o assistente"],
    ["/lojas/00000000-0000-0000-0000-000000000000/pedidos?situacao=EXPIRED", "Pedidos", 'name="situacao"'],
    ["/lojas/00000000-0000-0000-0000-000000000000/pedidos/00000000-0000-0000-0000-000000000001", "Consultando pedido", 'aria-label="Trilha de navegação"'],
    ["/lojas/00000000-0000-0000-0000-000000000000/produtos", "Produtos", "Consultando catálogo"],
    ["/lojas/00000000-0000-0000-0000-000000000000/categorias", "Categorias", "Consultando categorias"],
    ["/lojas/00000000-0000-0000-0000-000000000000/produtos/novo", "Novo produto", 'aria-label="Navegação da loja"'],
    ["/lojas/00000000-0000-0000-0000-000000000000/produtos/00000000-0000-0000-0000-000000000001", "Editar produto", 'aria-label="Trilha de navegação"'],
    ["/lojas/00000000-0000-0000-0000-000000000000/produtos/00000000-0000-0000-0000-000000000001/estoque", "Estoque do produto", 'aria-label="Trilha de navegação"'],
  ]) {
    const response = await fetch(`${baseUrl}${path}`, { signal: AbortSignal.timeout(5000) });
    assert.equal(response.status, 200, path);
    assert.equal(response.headers.get("referrer-policy"), "no-referrer", path);
    const page = await response.text();
    if (isProtectedPath(new URL(path, baseUrl).pathname)) {
      assert.match(page, /<h1[^>]*>Verificando sua sessão<\/h1>/, `${path}: acesso ainda não confirmado`);
      assert.ok(!page.includes('<nav') && !page.includes('<form'), `${path}: conteúdo operacional não montado sem sessão`);
      assert.equal((page.match(/<main[\s>]/g) ?? []).length, 1, `${path}: main único`);
    } else {
      assert.ok(page.includes(title), `${path}: título`);
      assert.ok(page.includes(field), `${path}: conteúdo semântico`);
    }
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
  console.log("Smoke aprovado: páginas públicas, gate de sessão em todas as rotas operacionais, alertas da loja (SC-024), helpers de pagamentos/despesas/financeiro/dashboard/pedidos/estoque, valores e datas, headers de privacidade e 404. Não substitui teste de browser/API autenticado.");
} finally {
  server.kill();
}
