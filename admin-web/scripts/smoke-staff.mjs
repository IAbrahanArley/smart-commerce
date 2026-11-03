import assert from "node:assert/strict";
import { billingNotice } from "../src/lib/billing.ts";
import { staffItems, storeItems } from "../src/lib/navigation.ts";
import { inviteEmailError, invitationToken, parseInvitation, parseStaff, staffLimitText, staffRefusal } from "../src/lib/staff.ts";

// SC-049 (ADR-0019 C1): pure helpers of the store team. Imported by smoke.mjs.
const id = "11111111-1111-4111-8111-111111111111";
const raw = {
  members: [{ userId: id, email: "caixa@example.test", since: "2026-11-01T10:00:00Z" }],
  invitations: [{ id, email: "novo@example.test", createdAt: "2026-11-01T10:00:00.123Z", expiresAt: "2026-11-08T10:00:00Z", expired: false }],
  used: 2, limit: 3, enforced: true,
};
const overview = parseStaff(raw);
assert.equal(overview?.members.length, 1);
assert.equal(parseStaff({ ...raw, members: [{ userId: "x", email: "a", since: "2026-11-01T10:00:00Z" }] }), null, "Id inválido recusa a lista");
assert.equal(parseStaff({ ...raw, limit: -1 }), null);
assert.equal(parseInvitation({ ...raw.invitations[0], expired: "não" }), null);
assert.match(staffLimitText(overview) ?? "", /^2 de 3 funcionários do seu plano/);
assert.match(staffLimitText({ ...overview, limit: 1, used: 1 }) ?? "", /1 de 1 funcionário do seu plano/);
assert.equal(staffLimitText({ ...overview, enforced: false }), null, "Sem cobrança, sem limite");

assert.equal(inviteEmailError("  "), "Informe o e-mail de quem vai trabalhar na loja.");
assert.equal(inviteEmailError("sem-arroba"), "Informe um e-mail válido.");
assert.equal(inviteEmailError(" caixa@example.test "), null);

const token = "A".repeat(43);
assert.equal(invitationToken(`#token=${token}`), token);
for (const hash of ["", "#token=curto", `#token=${token}&x=1`, `#outro=${token}`, `#token=${"A".repeat(42)}!`])
  assert.equal(invitationToken(hash), null, hash);

assert.equal(staffRefusal(409, { code: "PLAN_LIMIT", message: "Seu plano permite até 1 funcionário." }), "Seu plano permite até 1 funcionário.");
assert.match(staffRefusal(410, { code: "INVALID_INVITATION" }) ?? "", /Convite inválido/);
assert.match(staffRefusal(403, { code: "WRONG_ACCOUNT" }) ?? "", /outro e-mail/);
assert.equal(staffRefusal(404, null), "Loja não encontrada.");
assert.match(staffRefusal(429, { code: "THROTTLED" }) ?? "", /amanhã/);
assert.match(staffRefusal(409, { code: "OWNER_LIMIT" }) ?? "", /dono da loja/);
assert.equal(staffRefusal(500, null), null);

// Navigation: the OWNER manages the team; an employee only reaches the store's home.
assert.ok(storeItems(id, `/lojas/${id}/funcionarios`).find(item => item.href.endsWith("/funcionarios"))?.current);
assert.deepEqual(staffItems(id, `/lojas/${id}`).map(item => item.label), ["Visão geral", "Caixa"]);

// The billing notice is for owners: an account without stores (an employee) sees none.
const expired = { status: "TRIAL_EXPIRED", plan: null, cycle: null, trialEndsAt: "2026-10-01T00:00:00Z", currentPeriodEnd: null, enforced: true, limits: { companies: 2, productsPerStore: 2000, monthlyQuestions: 60 }, companies: 0 };
assert.equal(billingNotice(expired, new Date()), null);
assert.equal(billingNotice({ ...expired, companies: 1 }, new Date())?.tone, "danger");
