"use client";

import Link from "next/link";
import { useState } from "react";
import { Check } from "lucide-react";
import { billingText, cycleOf, CYCLES, type Cycle, monthlyPrice, PLANS, priceText, TRIAL_DAYS } from "@/lib/plans";

// Plan cards of the landing (SaaS plans, 2026-10-26). The cycle is a native radio group, so arrows and
// screen readers work as usual; a status line names the chosen cycle instead of rereading every card.
export default function PricingPlans() {
  const [cycle, setCycle] = useState<Cycle>("monthly");
  return <div className="pricing">
    <fieldset className="pricing-cycles">
      <legend className="visually-hidden">Período de cobrança</legend>
      {CYCLES.map(item => <label key={item.id} className="pricing-cycle">
        <input type="radio" name="cycle" value={item.id} checked={cycle === item.id} onChange={() => setCycle(item.id)} />
        <span>{item.label}{item.discount ? <small> −{item.discount}%</small> : null}</span>
      </label>)}
    </fieldset>
    <p className="visually-hidden" role="status">{`Preços do período ${cycleOf(cycle).label.toLowerCase()}.`}</p>
    <ul className="pricing-cards">
      {PLANS.map(plan => <li key={plan.id} className="pricing-card" data-featured={plan.featured ? "true" : undefined}>
        {plan.featured ? <p className="pricing-badge">Mais escolhido</p> : null}
        <h3>{plan.name}</h3>
        <p className="pricing-summary">{plan.summary}</p>
        <p className="pricing-price"><strong>{priceText(monthlyPrice(plan, cycle))}</strong><span>/mês</span></p>
        <p className="pricing-billing">{billingText(plan, cycle)}</p>
        <Link className={plan.featured ? "landing-button pricing-cta" : "landing-text-link pricing-cta"} href="/cadastro">
          Começar teste grátis<span className="visually-hidden"> do plano {plan.name}</span>
        </Link>
        <ul className="pricing-features">
          {plan.features.map(feature => <li key={feature}><Check aria-hidden="true" />{feature}</li>)}
        </ul>
      </li>)}
    </ul>
    <p className="landing-note pricing-note">{TRIAL_DAYS} dias grátis, sem cartão. Depois, pagamento no cartão de crédito; cancele quando quiser. O assistente com IA está em liberação gradual.</p>
  </div>;
}
