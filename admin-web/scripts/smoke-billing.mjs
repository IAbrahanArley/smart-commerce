import assert from "node:assert/strict";
import {
  backendCycle, backendPlan, billingNotice, billingRefusal, canSubscribe, checkoutMessage, checkoutTarget, dateText,
  parseAccount, portalTarget, trialDaysLeft,
} from "../src/lib/billing.ts";
import { accountItems } from "../src/lib/navigation.ts";
import { isProtectedPath } from "../src/lib/session-events.ts";

// SC-033 (ADR-0017): pure helpers of "Plano e cobrança". Imported by smoke.mjs.
const raw = {
  status: "TRIAL", plan: null, cycle: null, trialEndsAt: "2026-11-10T15:00:00.123456Z", currentPeriodEnd: null,
  enforced: true, limits: { companies: 2, productsPerStore: 2000, monthlyQuestions: 60 }, companies: 1,
};
const trial = parseAccount(raw);
assert.ok(trial);
assert.equal(parseAccount({ ...raw, status: "PAID" }), null, "Status desconhecido é recusado");
assert.equal(parseAccount({ ...raw, limits: { companies: -1, productsPerStore: 1, monthlyQuestions: 0 } }), null);
assert.equal(parseAccount({ ...raw, plan: "PREMIUM" })?.plan, null, "Plano desconhecido não é exibido");

assert.equal(trialDaysLeft(trial, new Date("2026-11-06T15:00:01Z")), 4);
assert.equal(trialDaysLeft(trial, new Date("2026-12-01T00:00:00Z")), 0);
assert.equal(billingNotice(trial, new Date("2026-11-01T00:00:00Z")), null, "Teste longe do fim não avisa");
assert.match(billingNotice(trial, new Date("2026-11-07T15:00:01Z"))?.text ?? "", /termina em 3 dias/);
assert.match(billingNotice(trial, new Date("2026-11-10T12:00:00Z"))?.text ?? "", /termina hoje/);
assert.equal(billingNotice({ ...trial, enforced: false }, new Date("2026-11-10T12:00:00Z")), null, "Cobrança desligada não avisa");
for (const status of ["TRIAL_EXPIRED", "PAST_DUE", "SUSPENDED", "CANCELED"])
  assert.equal(billingNotice({ ...trial, status }, new Date())?.tone, "danger", status);
assert.equal(billingNotice({ ...trial, status: "ACTIVE" }, new Date()), null);
assert.match(billingNotice({ ...trial, status: "SUSPENDED" }, new Date())?.text ?? "", /Nada foi apagado/);

assert.equal(canSubscribe(trial), true);
assert.equal(canSubscribe({ ...trial, status: "CANCELED" }), true);
assert.equal(canSubscribe({ ...trial, status: "PAST_DUE" }), false, "Com assinatura em andamento, só o portal");
assert.equal(backendPlan("profissional"), "PROFISSIONAL"); assert.equal(backendCycle("semiannual"), "SEMIANNUAL");
assert.equal(dateText("2026-11-10T02:00:00Z"), "09/11/2026", "Datas no fuso de São Paulo");

// The browser only leaves for Stripe's own pages.
assert.equal(checkoutTarget({ url: "https://checkout.stripe.com/c/pay/cs_test_1" }), "https://checkout.stripe.com/c/pay/cs_test_1");
for (const url of ["http://checkout.stripe.com/c/pay/x", "https://checkout.stripe.com.evil.example/x", "https://user@checkout.stripe.com/x", "https://billing.stripe.com/p/x", "javascript:alert(1)"])
  assert.equal(checkoutTarget({ url }), null, url);
assert.equal(portalTarget({ url: "https://billing.stripe.com/p/session/test_1" }), "https://billing.stripe.com/p/session/test_1");
assert.equal(portalTarget({ url: "https://checkout.stripe.com/c/pay/x" }), null);
assert.equal(checkoutTarget(null), null);

assert.match(checkoutMessage("sucesso")?.text ?? "", /confirmando/);
assert.equal(checkoutMessage("cancelado")?.error, true);
assert.equal(checkoutMessage("qualquer"), null);

assert.equal(billingRefusal(409, { code: "PLAN_LIMIT", message: "Seu plano permite até 2 lojas." }), "Seu plano permite até 2 lojas.");
assert.match(billingRefusal(402, null) ?? "", /não está ativa/);
assert.equal(billingRefusal(409, { code: "SKU_CONFLICT" }), null, "Outros conflitos seguem para a tela");
assert.equal(billingRefusal(409, null), null);

assert.deepEqual(accountItems("/conta/plano").filter(item => item.current).map(item => item.href), ["/conta/plano"]);
assert.equal(isProtectedPath("/conta/plano"), true);
