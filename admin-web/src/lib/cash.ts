// SC-052 (ADR-0019 C5): the store's cash register. Pure helpers, free of React and Next imports for
// the smoke test. Amounts travel as decimal text and are only formatted here; the backend decides.

import { parseAmount } from "./expenses.ts";

export type MovementKind = "ADJUSTMENT" | "TRANSFER_OUT" | "TRANSFER_IN" | "EXPENSE_PAYMENT" | "EXPENSE_REFUND" | "CASH_DIFFERENCE" | "OWNER_WITHDRAWAL" | "OWNER_CONTRIBUTION" | "COUNTER_SALE" | "COUNTER_SALE_REFUND";
export type CashMovement = { id: string; kind: MovementKind; amount: string; description: string | null; recordedBy: string; createdAt: string };
// expectedAmount and difference are null for employees (blind close) and while the session is open.
export type CashSession = {
  id: string; accountId: string; accountName: string; status: "OPEN" | "CLOSED"; openingAmount: string; openedBy: string; openedAt: string;
  reopenedFrom: string | null; reopenReason: string | null; countedAmount: string | null; expectedAmount: string | null; difference: string | null;
  closedBy: string | null; closedAt: string | null; movements: CashMovement[] | null;
};
export type Drawer = { id: string; name: string; session: CashSession | null };
export type Destination = { id: string; name: string; type: "CASH" | "BANK" | "PAYMENT_PROVIDER" };
export type CashOverview = { role: "OWNER" | "STAFF"; drawers: Drawer[]; destinations: Destination[] };
export type MoveType = "DROP" | "TOP_UP" | "EXPENSE";

export const MOVEMENT_LABELS: Record<MovementKind, string> = {
  ADJUSTMENT: "Saldo inicial", TRANSFER_OUT: "Sangria para outra conta", TRANSFER_IN: "Suprimento de outra conta",
  EXPENSE_PAYMENT: "Despesa paga pelo caixa", EXPENSE_REFUND: "Estorno de despesa", CASH_DIFFERENCE: "Diferença de caixa",
  OWNER_WITHDRAWAL: "Retirada do dono", OWNER_CONTRIBUTION: "Aporte do dono",
  COUNTER_SALE: "Venda em dinheiro", COUNTER_SALE_REFUND: "Cancelamento de venda",
};
export const MOVE_TITLES: Record<MoveType, string> = { DROP: "Sangria", TOP_UP: "Suprimento", EXPENSE: "Despesa paga pelo caixa" };

// A counted amount: like parseAmount, but zero is a valid count (an empty drawer); blank is not.
export function parseCount(value: string): { amount: string } | { error: string } {
  if (!value.trim()) return { error: "Informe o valor contado, mesmo que seja zero." };
  if (/^\s*(R\$\s*)?0+([.,]0{1,2})?\s*$/.test(value)) return { amount: "0.00" };
  const parsed = parseAmount(value);
  return "error" in parsed ? { error: "Informe um valor com até duas casas decimais." } : parsed;
}

// "Sobra de R$ 5,00", "Falta de R$ 5,00" or "Sem diferença"; the text formatter is passed in to
// keep this file free of the catalog helpers.
export function differenceText(difference: string, format: (value: string) => string): string {
  if (/^-?0+\.00$/.test(difference)) return "Sem diferença";
  return difference.startsWith("-") ? `Falta de ${format(difference.slice(1))}` : `Sobra de ${format(difference)}`;
}

export function formatDateTime(instant: string): string {
  return new Intl.DateTimeFormat("pt-BR", { timeZone: "America/Sao_Paulo", day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" }).format(new Date(instant));
}

// Where a drop can go to or a top-up come from: every account but the drawer itself.
export function otherAccounts(destinations: Destination[], drawerId: string): Destination[] {
  return destinations.filter(item => item.id !== drawerId && item.type !== "PAYMENT_PROVIDER");
}
