// Store OWNER orders (SC-010). Pure helpers, free of React and Next imports for the smoke test.

export type OrderStatus = "PENDING_PAYMENT" | "PAID" | "REFUNDED" | "EXPIRED" | "CANCELLED";

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
  // SC-014: confirmed by the backend with the provider; paidWithoutStock = paid after the
  // reservation ended without units to cover it (the store decides: restock or refund).
  paidAt: string | null;
  paidWithoutStock: boolean;
  refundedAt: string | null;
  // SC-015: null when the buyer never started the payment. Fee and net are not reported by the
  // provider's Orders API, so they are not shown as if known.
  payment: OrderPayment | null;
};

export type OrderPayment = {
  provider: "MERCADO_PAGO";
  providerOrderId: string | null;
  status: "CREATING" | "CREATED" | "APPROVED" | "REFUNDED";
  providerStatus: string | null;
  providerStatusDetail: string | null;
  amount: string;
  paidAmount: string | null;
  approvedAt: string | null;
  checkedAt: string | null;
  lastCheckError: string | null;
  // SC-016: the store's full refund; REQUESTED until the provider confirms it.
  refund: { status: "REQUESTED" | "CONFIRMED"; reason: string; restock: boolean; requestedAt: string; providerAcceptedAt: string | null; confirmedAt: string | null; lastError: string | null } | null;
};

export const PAYMENT_STATUS_LABELS: Record<OrderPayment["status"], string> = {
  CREATING: "Pagamento não iniciado no Mercado Pago",
  CREATED: "Aguardando confirmação do Mercado Pago",
  APPROVED: "Confirmado pelo Mercado Pago",
  REFUNDED: "Reembolsado pelo Mercado Pago",
};

// Divergences recorded by the last provider query (SC-015); unknown codes get a generic text.
export function paymentDivergence(code: string | null): string | null {
  if (!code) return null;
  switch (code) {
    case "PROVIDER_ERROR": return "O Mercado Pago não respondeu ou informou dados diferentes dos do pedido. A consulta será repetida.";
    case "ACCOUNT_CHANGED": return "A loja foi conectada a outra conta Mercado Pago depois deste pedido; a conta atual não pode consultá-lo.";
    case "NOT_CONNECTED":
    case "REVOKED": return "A conta Mercado Pago da loja não está conectada; reconecte em Pagamentos para a consulta voltar a funcionar.";
    default: return "A última consulta ao Mercado Pago não pôde ser concluída. Ela será repetida.";
  }
}

export const ORDER_STATUSES: OrderStatus[] = ["PENDING_PAYMENT", "PAID", "REFUNDED", "EXPIRED", "CANCELLED"];
export const STATUS_LABELS: Record<OrderStatus, string> = {
  PENDING_PAYMENT: "Aguardando pagamento",
  PAID: "Pago",
  REFUNDED: "Reembolsado",
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
