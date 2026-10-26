// Store dashboard (SC-022, ADR-0015 F8, F9). Pure helpers, free of React and Next imports for the
// smoke test. Figures come from the backend; the chart only positions them.

export type DashboardStock = { productId: string; variantId: string; productName: string; options: { name: string; value: string }[]; available: number; lastSaleAt: string | null };
export type Dashboard = {
  period: { key: string; firstDay: string; lastDay: string };
  paidOrders: number;
  revenue: string;
  averageTicket: string | null;
  contributionMargin: string;
  marginPercent: string | null;
  marginComplete: boolean;
  missing: { code: string; count: number }[];
  salesByDay: { day: string; orders: number; revenue: string }[];
  lowStockThreshold: number;
  lowStockCount: number;
  lowStock: DashboardStock[];
  staleAfterDays: number;
  withoutSalesCount: number;
  withoutSales: DashboardStock[];
};

export function shortDay(day: string): string {
  const [, month, date] = day.split("-");
  return `${date}/${month}`;
}

export function optionsText(options: { name: string; value: string }[]): string {
  return options.map(option => `${option.name}: ${option.value}`).join(" · ");
}

export type SalesPoint = { day: string; label: string; revenue: number; orders: number; revenueText: string };

// Chart points: the number only positions the bar; labels and tooltips show the backend's own string.
export function salesPoints(days: { day: string; orders: number; revenue: string }[]): SalesPoint[] {
  return days.map(day => {
    const revenue = Number(day.revenue);
    return { day: day.day, label: shortDay(day.day), revenue: Number.isFinite(revenue) ? revenue : 0, orders: day.orders, revenueText: day.revenue };
  });
}
