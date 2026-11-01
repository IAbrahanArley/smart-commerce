"use client";

import { CalendarClock, CircleAlert, Package, Sparkles, Store } from "lucide-react";
import type { ReactNode } from "react";
import { billingNotice, CYCLE_NAME, cycleId, dateText, PLAN_NAME, planId, STATUS_TEXT, trialDaysLeft, type BillingAccount, type SubscriptionState } from "@/lib/billing";
import { cycleTotalText, planById } from "@/lib/plans";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

const count = (value: number) => new Intl.NumberFormat("pt-BR").format(value);

// ADR-0017 B13: the owner's plan at a glance, its usage and what is scheduled (a smaller plan at the
// renewal or the end of the subscription), each with the way back. Figures come from the backend and
// from Stripe; the price is the approved list price of lib/plans.ts, shown, never charged from here.
export default function BillingSummary({ account, subscription, busy, onUndo, onResume }: {
  account: BillingAccount; subscription: SubscriptionState | null; busy: string | null; onUndo: () => void; onResume: () => void;
}) {
  const now = new Date();
  const notice = billingNotice(account, now);
  const good = account.status === "ACTIVE" || account.status === "TRIAL";
  const days = trialDaysLeft(account, now);
  const pending = subscription?.pendingChange ?? null;
  const ending = subscription?.cancelAtPeriodEnd ? subscription.currentPeriodEnd ?? account.currentPeriodEnd : null;

  return <Card>
    <CardHeader className="flex-row flex-wrap items-start justify-between gap-3">
      <div className="flex flex-col gap-1.5">
        <CardTitle id="current-plan-title">Seu plano</CardTitle>
        <CardDescription>Vale para todas as suas lojas.</CardDescription>
      </div>
      <Badge variant={good ? "success" : "destructive"}>{ending ? "Cancelamento agendado" : STATUS_TEXT[account.status]}</Badge>
    </CardHeader>
    <CardContent className="flex flex-col gap-6">
      <div>
        <p className="m-0 text-2xl font-bold leading-tight text-foreground">
          {account.plan ? PLAN_NAME[account.plan] : "Profissional"}
          {account.cycle ? <span className="text-base font-semibold text-muted-foreground"> · {CYCLE_NAME[account.cycle]}</span> : null}
        </p>
        <p className="m-0 mt-1 text-sm text-muted-foreground">
          {account.plan && account.cycle ? cycleTotalText(planById(planId(account.plan)), cycleId(account.cycle))
            : account.status === "TRIAL" ? `Liberado no teste grátis até ${account.trialEndsAt ? dateText(account.trialEndsAt) : "o fim do teste"} (${days} ${days === 1 ? "dia" : "dias"}).`
            : "Escolha um plano para continuar."}
        </p>
        {account.currentPeriodEnd && account.status !== "TRIAL" && !ending ? <p className="m-0 mt-1 text-sm text-muted-foreground">
          {account.status === "ACTIVE" ? "Próxima cobrança em " : account.status === "CANCELED" ? "Período pago até " : "Período atual até "}
          <strong className="text-foreground">{dateText(account.currentPeriodEnd)}</strong>
        </p> : null}
      </div>

      {pending ? <Notice icon={<CalendarClock />}
        text={<>Em <strong>{dateText(pending.startsAt)}</strong>, seu plano muda para <strong>{PLAN_NAME[pending.plan]} {CYCLE_NAME[pending.cycle].toLowerCase()}</strong> ({cycleTotalText(planById(planId(pending.plan)), cycleId(pending.cycle))}). Até lá, nada muda.</>}
        action={<Button type="button" variant="outline" size="sm" disabled={busy !== null} onClick={onUndo}>{busy === "undo" ? "Desfazendo…" : "Desfazer mudança"}</Button>} /> : null}

      {ending ? <Notice icon={<CircleAlert />} tone="danger"
        text={<>Sua assinatura termina em <strong>{dateText(ending)}</strong>. Até lá, tudo continua funcionando. Depois, o painel fica só para consulta e a vitrine sai do ar; nada é apagado.</>}
        action={<Button type="button" size="sm" disabled={busy !== null} onClick={onResume}>{busy === "resume" ? "Mantendo…" : "Manter assinatura"}</Button>} /> : null}

      <dl className="m-0 grid gap-3 sm:grid-cols-3">
        <Usage icon={<Store />} label="Lojas" value={`${count(account.companies)} de ${count(account.limits.companies)}`} ratio={account.companies / Math.max(1, account.limits.companies)} />
        <Usage icon={<Package />} label="Produtos por loja" value={`até ${count(account.limits.productsPerStore)}`} />
        <Usage icon={<Sparkles />} label="Perguntas ao assistente" value={account.limits.monthlyQuestions === 0 ? "não incluídas" : `${count(account.limits.monthlyQuestions)} por mês`} />
      </dl>

      {notice ? <p className="m-0 text-sm text-muted-foreground">{notice.text}</p> : null}
    </CardContent>
  </Card>;
}

function Notice({ icon, tone = "info", text, action }: { icon: ReactNode; tone?: "info" | "danger"; text: ReactNode; action: ReactNode }) {
  return <div className={`flex flex-col gap-3 rounded-lg border p-4 text-sm sm:flex-row sm:items-center sm:justify-between ${tone === "danger" ? "border-destructive/30 bg-destructive-soft" : "border-border bg-primary-soft"}`}>
    <div className="flex items-start gap-3">
      <span className={`mt-0.5 shrink-0 [&_svg]:size-5 ${tone === "danger" ? "text-destructive" : "text-primary"}`} aria-hidden="true">{icon}</span>
      <p className="m-0 text-foreground">{text}</p>
    </div>
    <div className="shrink-0">{action}</div>
  </div>;
}

function Usage({ icon, label, value, ratio }: { icon: ReactNode; label: string; value: string; ratio?: number }) {
  const width = ratio === undefined ? null : Math.min(100, Math.round(ratio * 100));
  return <div className="flex min-w-0 items-start gap-3 rounded-lg border border-border p-3">
    <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-full bg-primary-soft text-primary [&_svg]:size-4" aria-hidden="true">{icon}</span>
    <div className="min-w-0 flex-1">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="m-0 text-sm font-semibold tabular-nums text-foreground">{value}</dd>
      {width !== null ? <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-muted" aria-hidden="true">
        <div className={`h-full rounded-full ${width >= 100 ? "bg-destructive" : "bg-primary"}`} style={{ width: `${width}%` }} />
      </div> : null}
    </div>
  </div>;
}
