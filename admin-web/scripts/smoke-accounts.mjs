import assert from "node:assert/strict";
import { accountRefusal, lineError, manualAccounts, parseAccounts, parseSignedAmount, parseStatement, todayInStore } from "../src/lib/accounts.ts";

// SC-050 (ADR-0019 C2): pure helpers of the store accounts. Imported by smoke.mjs.
const id = "11111111-1111-4111-8111-111111111111", other = "22222222-2222-4222-8222-222222222222";
const cash = { id, type: "CASH", name: "Caixa da loja", systemKey: "DEFAULT_CASH", balance: "100.00" };
assert.equal(parseAccounts([cash])?.[0].balance, "100.00");
assert.equal(parseAccounts([{ ...cash, balance: 100 }]), null, "Dinheiro chega como texto, nunca número");
assert.equal(parseAccounts([{ ...cash, type: "CARD" }]), null);
assert.equal(parseAccounts([{ ...cash, systemKey: "OUTRO" }]), null);

const statement = {
  account: cash, from: "2026-08-04", to: "2026-11-01", openingBalance: "0.00", closingBalance: "100.00", truncated: false,
  movements: [
    { id: other, kind: "TRANSFER_OUT", amount: "-50.00", occurredOn: "2026-11-01", description: "Depósito", counterpartAccountId: id, balanceAfter: "100.00", transferId: other, createdAt: "2026-11-01T12:00:00Z" },
    { id, kind: "ADJUSTMENT", amount: "150.00", occurredOn: "2026-11-01", description: "Saldo inicial", counterpartAccountId: null, balanceAfter: "150.00", transferId: null, createdAt: "2026-11-01T11:00:00Z" },
  ],
};
assert.equal(parseStatement(statement)?.movements.length, 2);
assert.equal(parseStatement({ ...statement, closingBalance: "1e2" }), null);
assert.equal(parseStatement({ ...statement, movements: [{ ...statement.movements[0], kind: "OUTRO" }] }), null);

assert.deepEqual(parseSignedAmount("1.234,56", false), { amount: "1234.56" });
assert.deepEqual(parseSignedAmount("R$ 50", false), { amount: "50.00" });
assert.deepEqual(parseSignedAmount("-20,5", true), { amount: "-20.50" });
assert.ok("error" in parseSignedAmount("-20", false), "Transferência não aceita negativo");
for (const value of ["0", "0,00", "abc", "1,234", ""])
  assert.ok("error" in parseSignedAmount(value, true), value);

assert.equal(lineError("ok", "Motivo", 200), "Motivo: use pelo menos 3 caracteres.");
assert.equal(lineError("Saldo inicial", "Motivo", 200), null);
assert.equal(todayInStore(new Date("2026-11-02T02:00:00Z")), "2026-11-01", "Dia no fuso de São Paulo");
assert.equal(accountRefusal(409, { code: "ACCOUNT_LIMIT", message: "A loja chegou ao limite de 20 contas." }), "A loja chegou ao limite de 20 contas.");
assert.equal(accountRefusal(404, null), "Conta ou loja não encontrada.");
assert.equal(accountRefusal(500, null), null);
// Review of SC-050: dots are thousands only in well-formed groups; big balances still parse.
assert.deepEqual(parseSignedAmount("10.500", false), { amount: "10500.00" });
assert.deepEqual(parseSignedAmount("10.5", false), { amount: "10.50" });
assert.deepEqual(parseSignedAmount("1.234.567,89", false), { amount: "1234567.89" });
assert.ok("error" in parseSignedAmount("1234.567", false), "Ponto fora de grupo de milhar é recusado");
assert.ok("error" in parseSignedAmount("12.34.56", false));
assert.equal(parseAccounts([{ ...cash, balance: "12345678901234567.00" }])?.[0].balance, "12345678901234567.00");
// Decided on 2026-11-01: the Mercado Pago account takes no manual movement.
assert.deepEqual(manualAccounts([cash, { ...cash, id: other, type: "PAYMENT_PROVIDER", name: "Mercado Pago", systemKey: "MERCADO_PAGO" }]).map(item => item.name), ["Caixa da loja"]);
assert.match(accountRefusal(409, { code: "PROVIDER_READ_ONLY", message: "A conta do Mercado Pago segue os pagamentos do próprio Mercado Pago e não aceita lançamentos manuais." }) ?? "", /Mercado Pago/);
