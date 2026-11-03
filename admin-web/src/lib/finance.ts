// Store OWNER financial report (SC-021, ADR-0015 F1–F5). Pure helpers, free of React and Next
// imports for the smoke test. Every figure comes from the backend ledger; nothing is computed here.

export type ReportPeriodKey = "TODAY" | "LAST_7_DAYS" | "LAST_30_DAYS" | "THIS_MONTH" | "LAST_MONTH";

export type FinanceReport = {
  period: { key: string; firstDay: string; lastDay: string };
  grossRevenue: string;
  refunds: string;
  fees: string;
  netRevenue: string;
  cogs: string;
  variableExpenses: string;
  contributionMargin: string;
  marginPercent: string | null;
  fixedExpenses: string;
  complete: boolean;
  missing: { code: "FEES_UNAVAILABLE" | "COST_MISSING" | string; count: number }[];
  paidOrders: number;
  averageTicket: string | null;
  receivable: { amount: string; feesUnknown: number };
  cashMovement: string;
  // SC-052: drawer counts minus expected, signed (negative is cash missing); outside the margin.
  cashDifferences: string;
  // SC-053: the part of the fees the store informed for card sales at the counter.
  informedFees: string;
};

export const PERIODS: { key: ReportPeriodKey; label: string }[] = [
  { key: "TODAY", label: "Hoje" },
  { key: "LAST_7_DAYS", label: "Últimos 7 dias" },
  { key: "LAST_30_DAYS", label: "Últimos 30 dias" },
  { key: "THIS_MONTH", label: "Este mês" },
  { key: "LAST_MONTH", label: "Mês anterior" },
];

// Values from the URL (?periodo=) are trusted only after this check; anything else is this month.
export function reportPeriod(value: string | undefined): ReportPeriodKey {
  return PERIODS.some(period => period.key === value) ? value as ReportPeriodKey : "THIS_MONTH";
}

export function missingText(item: { code: string; count: number }): string {
  const plural = item.count === 1 ? "" : "s";
  if (item.code === "FEES_UNAVAILABLE") return `${item.count} venda${plural} sem a taxa informada (Mercado Pago ou cartão na loja)`;
  if (item.code === "COST_MISSING") return `${item.count} pedido${plural} com produto sem custo cadastrado`;
  return `${item.count} item${plural} sem dados completos`;
}

export function percentText(value: string | null): string {
  if (value === null) return "—";
  const number = Number(value);
  return Number.isFinite(number) ? `${new Intl.NumberFormat("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(number)}%` : "—";
}

export type BreakdownRow = { label: string; amount: number; amountText: string; kind: "revenue" | "deduction" | "result" };

// Path from gross revenue to the contribution margin, in the order of the report. Deductions are drawn
// as positive bars; the text always comes from the backend.
export function marginBreakdown(report: Pick<FinanceReport, "grossRevenue" | "refunds" | "fees" | "cogs" | "variableExpenses" | "contributionMargin">): BreakdownRow[] {
  const row = (label: string, value: string, kind: BreakdownRow["kind"]): BreakdownRow => {
    const amount = Math.abs(Number(value));
    return { label, amount: Number.isFinite(amount) ? amount : 0, amountText: value, kind };
  };
  return [
    row("Receita bruta", report.grossRevenue, "revenue"),
    row("Reembolsos", report.refunds, "deduction"),
    row("Taxas", report.fees, "deduction"),
    row("Custo dos produtos", report.cogs, "deduction"),
    row("Despesas variáveis", report.variableExpenses, "deduction"),
    row("Margem de contribuição", report.contributionMargin, "result"),
  ];
}
