// Store OWNER orders (SC-010). Pure helpers, free of React and Next imports for the smoke test.

export type OrderStatus = "PENDING_PAYMENT" | "EXPIRED" | "CANCELLED";

export type OrderSummary = {
  id: string;
  number: number;
  status: OrderStatus;
  total: string;
  currency: "BRL";
  buyerName: string | null;
  fulfillmentType: "PICKUP" | "FIXED_SHIPPING";
  units: number;
  createdAt: string;
  reservationExpiresAt: string;
};

export type OrderPage = { orders: OrderSummary[]; nextCursor: string | null };

export type OrderDetail = {
  id: string;
  number: number;
  status: OrderStatus;
  currency: "BRL";
  items: { productName: string; options: { name: string; value: string }[]; sku: string; unitPrice: string; unitCost: string | null; quantity: number; lineTotal: string }[];
  subtotal: string;
  shipping: string;
  total: string;
  fulfillment: { type: "PICKUP" | "FIXED_SHIPPING"; text: string };
  buyer: { name: string; email: string; phone: string } | null;
  address: { line1: string; line2: string | null; district: string; city: string; state: string; postalCode: string } | null;
  reservationExpiresAt: string;
  createdAt: string;
  expiredAt: string | null;
  cancellation: { reason: string; cancelledAt: string } | null;
  anonymizedAt: string | null;
};

export const ORDER_STATUSES: OrderStatus[] = ["PENDING_PAYMENT", "EXPIRED", "CANCELLED"];
export const STATUS_LABELS: Record<OrderStatus, string> = {
  PENDING_PAYMENT: "Aguardando pagamento",
  EXPIRED: "Reserva expirada",
  CANCELLED: "Cancelado",
};
export const FULFILLMENT_LABELS = { PICKUP: "Retirada na loja", FIXED_SHIPPING: "Entrega com frete fixo" } as const;
export const CANCEL_REASON_MIN = 3;
export const CANCEL_REASON_MAX = 300;

// Query values from the URL are trusted only after this check; anything else lists every order.
export function orderStatus(value: string | undefined): OrderStatus | null {
  return ORDER_STATUSES.includes(value as OrderStatus) ? value as OrderStatus : null;
}

export function orderCursor(value: string | undefined): string | null {
  return value && /^[1-9][0-9]{0,17}$/.test(value) ? value : null;
}

export function ordersApiPath(tenantId: string, status: OrderStatus | null, after: string | null): string {
  const query = new URLSearchParams();
  if (status) query.set("status", status);
  if (after) query.set("after", after);
  const suffix = query.toString();
  return `/api/tenants/${encodeURIComponent(tenantId)}/orders${suffix ? `?${suffix}` : ""}`;
}

// Admin URLs use Portuguese parameter names; they are mapped to the API ones above.
export function ordersPagePath(tenantId: string, status: OrderStatus | null, after: string | null): string {
  const query = new URLSearchParams();
  if (status) query.set("situacao", status);
  if (after) query.set("apos", after);
  const suffix = query.toString();
  return `/lojas/${encodeURIComponent(tenantId)}/pedidos${suffix ? `?${suffix}` : ""}`;
}

// Mirrors the backend rule: one line, no control or separator characters, 3 to 300 code points.
export function cancelReason(value: string): { reason: string } | { error: string } {
  if (/[\p{Cc}\p{Cf}\p{Zl}\p{Zp}]/u.test(value)) return { error: "Escreva o motivo em uma única linha, sem caracteres especiais invisíveis." };
  const reason = value.trim();
  const length = [...reason].length;
  if (length < CANCEL_REASON_MIN) return { error: `Informe o motivo do cancelamento com pelo menos ${CANCEL_REASON_MIN} caracteres.` };
  if (length > CANCEL_REASON_MAX) return { error: `Use até ${CANCEL_REASON_MAX} caracteres.` };
  return { reason };
}

const DATE_TIME = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short", timeZone: "America/Sao_Paulo" });

export function formatDateTime(value: string | null): string {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : DATE_TIME.format(date);
}

export function formatPhone(digits: string): string {
  const match = /^(?:\+?55)?(\d{2})(\d{4,5})(\d{4})$/.exec(digits);
  return match ? `(${match[1]}) ${match[2]}-${match[3]}` : digits;
}

export function formatPostalCode(digits: string): string {
  return /^\d{8}$/.test(digits) ? `${digits.slice(0, 5)}-${digits.slice(5)}` : digits;
}
