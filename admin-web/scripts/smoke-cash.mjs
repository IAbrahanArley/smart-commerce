import assert from "node:assert/strict";
import { differenceText, otherAccounts, parseCount } from "../src/lib/cash.ts";
import { parseStatement } from "../src/lib/accounts.ts";

// SC-052 (ADR-0019 C5): pure helpers of the cash register. Imported by smoke.mjs.
for (const [input, expected] of [["0", "0.00"], ["0,00", "0.00"], ["150", "150.00"], ["1.234,50", "1234.50"]]) assert.deepEqual(parseCount(input), { amount: expected }, input);
for (const input of ["", "  ", "-5", "1,234", "abc"]) assert.ok("error" in parseCount(input), input);
const brl = value => `R$ ${value}`;
assert.equal(differenceText("-5.00", brl), "Falta de R$ 5.00");
assert.equal(differenceText("2.50", brl), "Sobra de R$ 2.50");
assert.equal(differenceText("0.00", brl), "Sem diferença");
const drawer = "11111111-1111-4111-8111-111111111111", bank = "22222222-2222-4222-8222-222222222222";
assert.deepEqual(otherAccounts([{ id: drawer, name: "Caixa", type: "CASH" }, { id: bank, name: "Banco", type: "BANK" }, { id: "x", name: "MP", type: "PAYMENT_PROVIDER" }], drawer).map(item => item.name), ["Banco"]);
// The drawer statement keeps reading the movements the cash register writes.
const account = { id: drawer, type: "CASH", name: "Caixa da loja", systemKey: "DEFAULT_CASH", balance: "95.00" };
const movement = kind => ({ id: bank, kind, amount: "-5.00", occurredOn: "2026-11-03", description: "Fechamento", counterpartAccountId: null, balanceAfter: "95.00" });
for (const kind of ["CASH_DIFFERENCE", "OWNER_WITHDRAWAL"]) assert.equal(parseStatement({ account, from: "2026-11-01", to: "2026-11-03", openingBalance: "100.00", closingBalance: "95.00", truncated: false, movements: [movement(kind)] })?.movements[0].kind, kind);
