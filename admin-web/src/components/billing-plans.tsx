"use client";

import { Check } from "lucide-react";
import { billingText, CYCLES, monthlyPrice, PLANS, priceText, type Cycle, type PlanId } from "@/lib/plans";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

type Choice = { plan: PlanId; cycle: Cycle };

// ADR-0017 B3, B13: the three approved plans for the chosen cycle. Without a subscription each card
// opens Stripe's Checkout; with one, it starts a plan change whose price and timing the backend
// previews before anything happens. `blocked` explains why changes are paused (unpaid, ending).
export default function BillingPlans({ mode, cycle, onCycle, current, pending, busy, blocked, onChoose }: {
  mode: "subscribe" | "change"; cycle: Cycle; onCycle: (cycle: Cycle) => void; current: Choice | null; pending: Choice | null;
  busy: string | null; blocked: string | null; onChoose: (plan: PlanId) => void;
}) {
  const disabled = busy !== null || blocked !== null;
  return <section aria-labelledby="choose-plan" className="flex flex-col gap-4">
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div>
        <h2 id="choose-plan" className="m-0 text-lg font-semibold">{mode === "subscribe" ? "Escolha seu plano" : "Mudar de plano"}</h2>
        <p className="m-0 mt-1 text-sm text-muted-foreground">{mode === "subscribe"
          ? "Pagamento com cartão de crédito, renovado automaticamente a cada ciclo."
          : "Plano maior ou ciclo mais longo vale na hora, com o valor proporcional. Plano menor ou ciclo mais curto vale na renovação. Você confere tudo antes de confirmar."}</p>
      </div>
      <fieldset className="m-0 inline-flex flex-wrap gap-1 rounded-lg border border-border bg-card p-1">
        <legend className="visually-hidden">Ciclo de cobrança</legend>
        {CYCLES.map(option => <label key={option.id} className="relative">
          <input type="radio" name="cycle" value={option.id} checked={cycle === option.id} onChange={() => onCycle(option.id)} disabled={busy !== null} className="peer visually-hidden" />
          <span className="inline-flex min-h-11 cursor-pointer items-center gap-2 rounded-md px-4 text-sm font-semibold text-muted-foreground peer-checked:bg-primary peer-checked:text-primary-foreground peer-focus-visible:ring-2 peer-focus-visible:ring-ring peer-focus-visible:ring-offset-2 peer-disabled:cursor-not-allowed">
            {option.label}{option.discount ? <span className="rounded bg-success-soft px-1.5 py-0.5 text-xs text-success">−{option.discount}%</span> : null}
          </span>
        </label>)}
      </fieldset>
    </div>
    {blocked ? <p role="status" className="m-0 rounded-lg border border-border bg-muted p-3 text-sm text-foreground">{blocked}</p> : null}
    <ul className="m-0 grid list-none gap-4 p-0 md:grid-cols-3">
      {PLANS.map(plan => {
        const isCurrent = current?.plan === plan.id && current.cycle === cycle;
        const isPending = pending?.plan === plan.id && pending.cycle === cycle;
        const highlighted = mode === "change" ? current?.plan === plan.id : plan.featured;
        return <li key={plan.id} className={`flex flex-col rounded-lg border bg-card p-6 shadow-card ${highlighted ? "border-primary ring-1 ring-primary" : "border-border"}`}>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h3 className="m-0 text-lg font-semibold">{plan.name}</h3>
            {isCurrent ? <Badge variant="success">Plano atual</Badge> : isPending ? <Badge>Agendado</Badge> : mode === "subscribe" && plan.featured ? <Badge>Mais escolhido</Badge> : null}
          </div>
          <p className="m-0 mt-1 text-sm text-muted-foreground">{plan.summary}</p>
          <p className="m-0 mt-4"><strong className="text-3xl font-bold tabular-nums text-foreground">{priceText(monthlyPrice(plan, cycle))}</strong> <span className="text-sm text-muted-foreground">por mês</span></p>
          <p className="m-0 text-sm text-muted-foreground">{billingText(plan, cycle)}</p>
          <ul className="m-0 mt-4 flex flex-1 list-none flex-col gap-2 p-0 text-sm">
            {plan.features.map(feature => <li key={feature} className="flex items-start gap-2">
              <Check className="mt-0.5 size-4 shrink-0 text-success" aria-hidden="true" />{feature}
            </li>)}
          </ul>
          <Button type="button" className="mt-6 w-full" variant={isCurrent || (mode === "subscribe" && !highlighted) ? "outline" : "default"}
            disabled={disabled || isCurrent} onClick={() => onChoose(plan.id)}>
            {isCurrent ? "Seu plano atual" : busy === plan.id ? (mode === "subscribe" ? "Abrindo a Stripe…" : "Calculando…") : mode === "subscribe" ? `Assinar ${plan.name}` : `Mudar para ${plan.name}`}
          </Button>
        </li>;
      })}
    </ul>
  </section>;
}
