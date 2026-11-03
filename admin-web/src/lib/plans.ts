// SaaS plans approved on 2026-10-26 (docs/product/PRICING_PROPOSAL.md). Pure, imported by the smoke
// test. Prices are whole reais written per billing cycle, never computed at render time; the backend
// billing module (not built yet) will own the charged amounts.

export type Cycle = "monthly" | "semiannual" | "annual";
export type PlanId = "essencial" | "profissional" | "escala";

export const CYCLES: { id: Cycle; label: string; months: number; discount: number }[] = [
  { id: "monthly", label: "Mensal", months: 1, discount: 0 },
  { id: "semiannual", label: "Semestral", months: 6, discount: 10 },
  { id: "annual", label: "Anual", months: 12, discount: 20 },
];

export type Plan = {
  id: PlanId;
  name: string;
  summary: string;
  /** Total charged per cycle, in whole reais. */
  prices: Record<Cycle, number>;
  stores: number;
  features: string[];
  featured?: boolean;
};

export const TRIAL_DAYS = 14;

export const PLANS: Plan[] = [
  {
    id: "essencial", name: "Essencial", summary: "Para começar a vender online com uma loja.",
    prices: { monthly: 59, semiannual: 318, annual: 564 }, stores: 1,
    features: ["1 loja", "Até 150 produtos", "Vitrine, carrinho e pedidos", "Pagamentos pelo Mercado Pago, sem tarifa por venda", "Resumo financeiro do mês", "Caixa da loja com 1 funcionário", "Alertas de estoque e pedidos"],
  },
  {
    id: "profissional", name: "Profissional", summary: "Para quem quer acompanhar margem e crescer.", featured: true,
    prices: { monthly: 129, semiannual: 696, annual: 1236 }, stores: 2,
    features: ["Até 2 lojas", "Até 2.000 produtos por loja", "Tudo do Essencial", "Financeiro completo: margem, despesas e caixa", "Até 3 funcionários no caixa", "Assistente com IA: 60 perguntas por mês", "Suporte prioritário por e-mail"],
  },
  {
    id: "escala", name: "Escala", summary: "Para operações com várias lojas.",
    prices: { monthly: 299, semiannual: 1614, annual: 2868 }, stores: 5,
    features: ["Até 5 lojas", "Produtos sem limite", "Tudo do Profissional", "Até 10 funcionários no caixa", "Assistente com IA: 200 perguntas por mês", "Suporte prioritário"],
  },
];

const brl = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL", maximumFractionDigits: 0 });

export function cycleOf(id: Cycle) {
  return CYCLES.find(cycle => cycle.id === id) ?? CYCLES[0];
}

/** Price shown per month, the cycle total divided by its months (all approved totals divide exactly). */
export function monthlyPrice(plan: Plan, cycle: Cycle): number {
  return plan.prices[cycle] / cycleOf(cycle).months;
}

export function priceText(value: number): string {
  return brl.format(value);
}

/** The cycle's total and how often it is charged, such as "R$ 318 a cada 6 meses". */
export function cycleTotalText(plan: Plan, cycle: Cycle): string {
  const every = cycle === "monthly" ? "por mês" : cycle === "annual" ? "por ano" : `a cada ${cycleOf(cycle).months} meses`;
  return `${priceText(plan.prices[cycle])} ${every}`;
}

export function planById(id: PlanId): Plan {
  return PLANS.find(plan => plan.id === id) ?? PLANS[0];
}

/** The line under the price: what is charged and how often. */
export function billingText(plan: Plan, cycle: Cycle): string {
  if (cycle === "monthly") return "cobrado todo mês";
  const months = cycleOf(cycle).months;
  return `${priceText(plan.prices[cycle])} a cada ${months} meses`;
}
