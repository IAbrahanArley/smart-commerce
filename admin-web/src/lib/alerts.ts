// Store alerts (SC-024, ADR-0016 A6). Pure helpers, free of React and Next imports for the smoke
// test. The backend decides which alerts exist from deterministic rules; the admin only words them
// and links to the screen that resolves each one. Unknown codes or targets are dropped.

import { formatBRL } from "./catalog.ts";

export type AlertSeverity = "CRITICAL" | "ATTENTION" | "INFO";
export type StoreAlert = { code: string; severity: AlertSeverity; count: number | null; amount: string | null; target: string };

const SEVERITIES: readonly AlertSeverity[] = ["CRITICAL", "ATTENTION", "INFO"];
const TARGETS = ["PRODUCTS", "ORDERS", "PAYMENTS", "FINANCE"] as const;

export const SEVERITY_TEXT: Record<AlertSeverity, string> = { CRITICAL: "Crítico", ATTENTION: "Atenção", INFO: "Informativo" };

export function parseAlerts(raw: unknown): StoreAlert[] | null {
  if (!raw || typeof raw !== "object" || !Array.isArray((raw as { alerts?: unknown }).alerts)) return null;
  const alerts: StoreAlert[] = [];
  for (const item of (raw as { alerts: unknown[] }).alerts) {
    if (!item || typeof item !== "object") continue;
    const value = item as Record<string, unknown>;
    if (typeof value.code !== "string" || !/^[A-Z_]{3,40}$/.test(value.code)) continue;
    if (!SEVERITIES.includes(value.severity as AlertSeverity)) continue;
    if (!TARGETS.includes(value.target as (typeof TARGETS)[number])) continue;
    const count = typeof value.count === "number" && Number.isSafeInteger(value.count) && value.count >= 0 ? value.count : null;
    const amount = typeof value.amount === "string" && /^-?\d{1,13}\.\d{2}$/.test(value.amount) ? value.amount : null;
    const alert = { code: value.code, severity: value.severity as AlertSeverity, count, amount, target: value.target as string };
    if (alertText(alert)) alerts.push(alert);
  }
  return alerts;
}

function plural(count: number, one: string, many: string): string {
  return `${count} ${count === 1 ? one : many}`;
}

// The sentence shown for each known code; null for codes this admin version does not know.
export function alertText(alert: StoreAlert): string | null {
  const count = alert.count ?? 0;
  switch (alert.code) {
    case "PAYMENTS_EXPIRED": return "A autorização do Mercado Pago venceu. Conecte de novo para voltar a receber pagamentos.";
    case "PAYMENTS_REVOKED": return "A autorização do Mercado Pago foi retirada. Conecte de novo para voltar a receber pagamentos.";
    case "PAYMENTS_NOT_CONNECTED": return "Conecte a conta Mercado Pago da loja para receber pagamentos.";
    case "MARGIN_NEGATIVE": return alert.amount ? `A margem de contribuição deste mês está negativa: ${formatBRL(alert.amount)}.` : null;
    case "COST_MISSING": return `${plural(count, "pedido pago", "pedidos pagos")} neste mês sem custo cadastrado. A margem fica parcial.`;
    case "FEES_UNAVAILABLE": return `${plural(count, "venda", "vendas")} deste mês ainda sem a taxa do Mercado Pago. A margem fica parcial.`;
    case "ORDERS_EXPIRING": return `${plural(count, "pedido aguardando", "pedidos aguardando")} pagamento com reserva terminando em até 10 minutos.`;
    case "LOW_STOCK": return `${plural(count, "item", "itens")} com estoque baixo (5 unidades ou menos).`;
    case "WITHOUT_SALES": return `${plural(count, "item", "itens")} com estoque e sem venda nos últimos 30 dias.`;
    default: return null;
  }
}

export function alertLink(alert: StoreAlert, tenantId: string): { href: string; label: string } {
  const base = `/lojas/${encodeURIComponent(tenantId)}`;
  switch (alert.target) {
    case "PAYMENTS": return { href: `${base}/pagamentos`, label: "Abrir pagamentos" };
    case "FINANCE": return { href: `${base}/financeiro`, label: "Abrir financeiro" };
    case "ORDERS": return { href: `${base}/pedidos?situacao=PENDING_PAYMENT`, label: "Ver pedidos pendentes" };
    default: return { href: `${base}/produtos`, label: "Ver produtos" };
  }
}
