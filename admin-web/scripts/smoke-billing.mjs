import assert from "node:assert/strict";
import {
  backendCycle, backendPlan, billingNotice, billingRefusal, canSubscribe, checkoutMessage, checkoutTarget, cycleId, dateText,
  moneyText, parseAccount, parsePreview, parseSubscription, planId, portalTarget, trialDaysLeft,
} from "../src/lib/billing.ts";
import { cycleTotalText, planById } from "../src/lib/plans.ts";
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

// ADR-0017 B13: the running subscription and the preview of a plan change, read defensively.
assert.deepEqual(parseSubscription({ status: null, plan: null, cycle: null, currentPeriodEnd: null, cancelAtPeriodEnd: false, pendingChange: null }),
  { plan: null, cycle: null, currentPeriodEnd: null, cancelAtPeriodEnd: false, pendingChange: null });
const running = parseSubscription({ status: "active", plan: "ESCALA", cycle: "ANNUAL", currentPeriodEnd: "2027-11-01T10:00:00Z", cancelAtPeriodEnd: false,
  pendingChange: { plan: "ESSENCIAL", cycle: "MONTHLY", startsAt: "2027-11-01T10:00:00Z" } });
assert.deepEqual(running?.pendingChange, { plan: "ESSENCIAL", cycle: "MONTHLY", startsAt: "2027-11-01T10:00:00Z" });
assert.equal(parseSubscription({ plan: "ESCALA" }), null, "Sem cancelAtPeriodEnd não é uma assinatura");
assert.equal(parseSubscription({ cancelAtPeriodEnd: false, pendingChange: { plan: "PREMIUM", cycle: "MONTHLY", startsAt: "2027-11-01T10:00:00Z" } })?.pendingChange, null);
const upgrade = { timing: "NOW", plan: "PROFISSIONAL", cycle: "MONTHLY", amountDue: 70.12, currency: "BRL", effectiveAt: "2026-11-01T10:00:00Z", prorationDate: 1793532456, exceedsCompanies: false };
assert.equal(parsePreview(upgrade)?.amountDue, 70.12);
assert.equal(parsePreview({ ...upgrade, prorationDate: null }), null, "Upgrade sem data de rateio não pode ser confirmado");
assert.equal(parsePreview({ ...upgrade, amountDue: -1 }), null);
assert.equal(parsePreview({ ...upgrade, currency: "real" }), null);
const downgrade = parsePreview({ ...upgrade, timing: "AT_PERIOD_END", amountDue: null, currency: null, prorationDate: null, exceedsCompanies: true });
assert.equal(downgrade?.amountDue, null); assert.equal(downgrade?.exceedsCompanies, true);
assert.equal(moneyText(70.12, "BRL").replace(/\s/g, " "), "R$ 70,12");
assert.equal(planId("ESCALA"), "escala"); assert.equal(cycleId("SEMIANNUAL"), "semiannual");
assert.equal(billingRefusal(402, { code: "PAYMENT_DECLINED", message: "O cartão recusou." }), "O cartão recusou.");
assert.equal(billingRefusal(409, { code: "PREVIEW_EXPIRED" }), "O valor da mudança precisa ser conferido de novo.");
assert.equal(billingRefusal(409, { code: "CHANGE_NOT_ALLOWED", message: "Esse já é o seu plano atual." }), "Esse já é o seu plano atual.");
assert.equal(cycleTotalText(planById("essencial"), "semiannual").replace(/\s/g, " "), "R$ 318 a cada 6 meses");
assert.equal(cycleTotalText(planById("escala"), "annual").replace(/\s/g, " "), "R$ 2.868 por ano");

assert.deepEqual(accountItems("/conta/plano").filter(item => item.current).map(item => item.href), ["/conta/plano"]);
assert.equal(isProtectedPath("/conta/plano"), true);
