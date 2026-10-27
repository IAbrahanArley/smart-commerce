// SC-033: the platform subscription seen by the owner (ADR-0017). Pure, imported by the smoke test.
// The backend decides the plan, the standing and the limits; this module only reads its answers and
// never computes an amount to charge (prices shown come from lib/plans.ts, charged ones from Stripe).

import type { Cycle, PlanId } from "@/lib/plans";

export type BillingStatus = "TRIAL" | "TRIAL_EXPIRED" | "ACTIVE" | "PAST_DUE" | "SUSPENDED" | "CANCELED";
export type BackendPlan = "ESSENCIAL" | "PROFISSIONAL" | "ESCALA";
export type BackendCycle = "MONTHLY" | "SEMIANNUAL" | "ANNUAL";

export type BillingAccount = {
  status: BillingStatus;
  plan: BackendPlan | null;
  cycle: BackendCycle | null;
  trialEndsAt: string | null;
  currentPeriodEnd: string | null;
  /** False while billing is off in this environment: nothing is limited or charged. */
  enforced: boolean;
  limits: { companies: number; productsPerStore: number; monthlyQuestions: number };
  companies: number;
};

const STATUSES: readonly BillingStatus[] = ["TRIAL", "TRIAL_EXPIRED", "ACTIVE", "PAST_DUE", "SUSPENDED", "CANCELED"];
const PLANS: readonly BackendPlan[] = ["ESSENCIAL", "PROFISSIONAL", "ESCALA"];
const CYCLES: readonly BackendCycle[] = ["MONTHLY", "SEMIANNUAL", "ANNUAL"];
const ISO = /^\d{4}-\d{2}-\d{2}T[\d:.]+Z$/;

function oneOf<T extends string>(value: unknown, allowed: readonly T[]): T | null {
  return typeof value === "string" && (allowed as readonly string[]).includes(value) ? value as T : null;
}

function count(value: unknown): number | null {
  return typeof value === "number" && Number.isInteger(value) && value >= 0 ? value : null;
}

function date(value: unknown): string | null {
  return typeof value === "string" && ISO.test(value) ? value : null;
}

export function parseAccount(raw: unknown): BillingAccount | null {
  if (!raw || typeof raw !== "object") return null;
  const value = raw as Record<string, unknown>;
  const status = oneOf(value.status, STATUSES);
  const limits = value.limits && typeof value.limits === "object" ? value.limits as Record<string, unknown> : null;
  const companies = count(limits?.companies), products = count(limits?.productsPerStore), questions = count(limits?.monthlyQuestions);
  const owned = count(value.companies);
  if (!status || typeof value.enforced !== "boolean" || companies === null || products === null || questions === null || owned === null) return null;
  return {
    status, plan: oneOf(value.plan, PLANS), cycle: oneOf(value.cycle, CYCLES),
    trialEndsAt: date(value.trialEndsAt), currentPeriodEnd: date(value.currentPeriodEnd), enforced: value.enforced,
    limits: { companies, productsPerStore: products, monthlyQuestions: questions }, companies: owned,
  };
}

export const STATUS_TEXT: Record<BillingStatus, string> = {
  TRIAL: "Teste grátis",
  TRIAL_EXPIRED: "Teste encerrado",
  ACTIVE: "Assinatura ativa",
  PAST_DUE: "Pagamento pendente",
  SUSPENDED: "Assinatura suspensa",
  CANCELED: "Assinatura cancelada",
};

export const PLAN_NAME: Record<BackendPlan, string> = { ESSENCIAL: "Essencial", PROFISSIONAL: "Profissional", ESCALA: "Escala" };
export const CYCLE_NAME: Record<BackendCycle, string> = { MONTHLY: "Mensal", SEMIANNUAL: "Semestral", ANNUAL: "Anual" };

export function backendPlan(id: PlanId): BackendPlan {
  return id.toUpperCase() as BackendPlan;
}

export function backendCycle(cycle: Cycle): BackendCycle {
  return cycle === "monthly" ? "MONTHLY" : cycle === "semiannual" ? "SEMIANNUAL" : "ANNUAL";
}

/** Whether a new Checkout makes sense: no subscription is running for this owner. */
export function canSubscribe(account: BillingAccount): boolean {
  return account.status === "TRIAL" || account.status === "TRIAL_EXPIRED" || account.status === "CANCELED";
}

/** Whole days left in the trial, rounded up, never negative. */
export function trialDaysLeft(account: BillingAccount, now: Date): number {
  if (!account.trialEndsAt) return 0;
  return Math.max(0, Math.ceil((Date.parse(account.trialEndsAt) - now.getTime()) / 86_400_000));
}

const dateFormat = new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "2-digit", year: "numeric", timeZone: "America/Sao_Paulo" });

export function dateText(iso: string): string {
  return dateFormat.format(new Date(iso));
}

/**
 * The banner shown on every panel page, or null when nothing needs the owner's attention. The trial
 * is only announced in its last 3 days, so the notice does not become noise.
 */
export function billingNotice(account: BillingAccount, now: Date): { text: string; tone: "info" | "danger" } | null {
  if (!account.enforced) return null;
  switch (account.status) {
    case "TRIAL": {
      const days = trialDaysLeft(account, now);
      if (days > 3) return null;
      return { text: days <= 1 ? "Seu teste grátis termina hoje. Assine um plano para continuar fazendo alterações." : `Seu teste grátis termina em ${days} dias. Assine um plano para não interromper a loja.`, tone: "info" };
    }
    case "TRIAL_EXPIRED": return { text: "Seu teste grátis terminou. O painel está só para consulta e a vitrine está fora do ar até você assinar um plano. Nada foi apagado.", tone: "danger" };
    case "PAST_DUE": return { text: "Não conseguimos cobrar sua assinatura. Atualize o cartão para evitar a suspensão da loja.", tone: "danger" };
    case "SUSPENDED": return { text: "Sua assinatura está suspensa por falta de pagamento. O painel está só para consulta e a vitrine está fora do ar. Nada foi apagado.", tone: "danger" };
    case "CANCELED": return { text: "Sua assinatura foi cancelada. O painel está só para consulta e a vitrine está fora do ar. Assine de novo quando quiser; nada foi apagado.", tone: "danger" };
    default: return null;
  }
}

// The browser only leaves for Stripe's own hosted pages over HTTPS.
function stripeTarget(raw: unknown, host: string): string | null {
  if (!raw || typeof raw !== "object") return null;
  const url = (raw as Record<string, unknown>).url;
  if (typeof url !== "string") return null;
  try {
    const parsed = new URL(url);
    return parsed.protocol === "https:" && parsed.hostname === host && !parsed.username && !parsed.password && !parsed.port ? parsed.toString() : null;
  } catch {
    return null;
  }
}

export function checkoutTarget(raw: unknown): string | null {
  return stripeTarget(raw, "checkout.stripe.com");
}

export function portalTarget(raw: unknown): string | null {
  return stripeTarget(raw, "billing.stripe.com");
}

/** Result word of the return from Checkout; it never proves payment (only the webhook does). */
export function checkoutMessage(result: string | null): { text: string; error: boolean } | null {
  switch (result) {
    case "sucesso": return { text: "Pagamento enviado. Estamos confirmando sua assinatura com a Stripe; isso costuma levar alguns segundos.", error: false };
    case "cancelado": return { text: "Você saiu do pagamento antes de concluir. Nada foi cobrado.", error: true };
    default: return null;
  }
}

/** Message for a refused billing request, from the backend's code; null when it is not a billing refusal. */
export function billingRefusal(status: number, body: unknown): string | null {
  const value = body && typeof body === "object" ? body as Record<string, unknown> : {};
  const message = typeof value.message === "string" && value.message.length <= 300 ? value.message : null;
  switch (value.code) {
    case "PLAN_LIMIT": return message ?? "Seu plano chegou ao limite. Mude de plano em Plano e cobrança.";
    case "PLAN_INACTIVE": return message ?? "Sua assinatura não está ativa. Regularize em Plano e cobrança.";
    case "SUBSCRIPTION_EXISTS": return message ?? "Você já tem uma assinatura. Use “Gerenciar assinatura”.";
    case "NO_SUBSCRIPTION": return message ?? "Você ainda não tem assinatura. Escolha um plano.";
    case "STRIPE_UNAVAILABLE": return "Não foi possível falar com o processador de pagamentos. Tente novamente em instantes.";
    default: return status === 402 ? "Sua assinatura não está ativa. Regularize em Plano e cobrança." : null;
  }
}
