// Store dashboard (SC-022, ADR-0015 F8, F9). Pure helpers, free of React and Next imports for the
// smoke test. Figures come from the backend; the chart only scales them.

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

export type Bar = { day: string; label: string; x: number; y: number; width: number; height: number; revenue: string };

// Bars of a fixed viewBox (width × height): equal slots, heights proportional to the day's
// revenue, a minimum of 0 so an empty period draws a flat chart instead of dividing by zero.
export function bars(days: { day: string; revenue: string }[], width: number, height: number): Bar[] {
  const values = days.map(day => Number(day.revenue));
  const max = Math.max(0, ...values.filter(Number.isFinite));
  const slot = days.length ? width / days.length : width;
  const gap = Math.min(4, slot * 0.2);
  return days.map((day, index) => {
    const value = Number.isFinite(values[index]) ? values[index] : 0;
    const barHeight = max > 0 ? Math.round((value / max) * height * 100) / 100 : 0;
    return { day: day.day, label: shortDay(day.day), x: Math.round((index * slot + gap / 2) * 100) / 100, y: Math.round((height - barHeight) * 100) / 100,
      width: Math.max(1, Math.round((slot - gap) * 100) / 100), height: barHeight, revenue: day.revenue };
  });
}

export function shortDay(day: string): string {
  const [, month, date] = day.split("-");
  return `${date}/${month}`;
}

export function optionsText(options: { name: string; value: string }[]): string {
  return options.map(option => `${option.name}: ${option.value}`).join(" · ");
}
