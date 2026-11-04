// SC-050 (ADR-0019 C2): accounts of the store, balances and statements. Pure, imported by the smoke
// test. Amounts travel as decimal text ("150.00") and are only formatted here, never computed.

import { decimalInput } from "./catalog.ts";

export type AccountType = "CASH" | "BANK" | "PAYMENT_PROVIDER";
export type Account = { id: string; type: AccountType; name: string; systemKey: string | null; balance: string };
export type Movement = {
  id: string; kind: "ADJUSTMENT" | "TRANSFER_OUT" | "TRANSFER_IN" | "EXPENSE_PAYMENT" | "EXPENSE_REFUND" | "CASH_DIFFERENCE" | "OWNER_WITHDRAWAL" | "OWNER_CONTRIBUTION" | "COUNTER_SALE" | "COUNTER_SALE_REFUND"; amount: string; occurredOn: string;
  description: string | null; counterpartAccountId: string | null; balanceAfter: string;
};
export type Statement = { account: Account; from: string; to: string; openingBalance: string; closingBalance: string; movements: Movement[]; nextCursor: string | null };

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const MONEY = /^-?\d{1,16}\.\d{2}$/;
// Balances are sums of movements and may outgrow a single movement's 15 digits.
const BALANCE = /^-?\d+\.\d{2}$/;
const DAY = /^\d{4}-\d{2}-\d{2}$/;
const TYPES: readonly AccountType[] = ["CASH", "BANK", "PAYMENT_PROVIDER"];
const KINDS: readonly Movement["kind"][] = ["ADJUSTMENT", "TRANSFER_OUT", "TRANSFER_IN", "EXPENSE_PAYMENT", "EXPENSE_REFUND", "CASH_DIFFERENCE", "OWNER_WITHDRAWAL", "OWNER_CONTRIBUTION", "COUNTER_SALE", "COUNTER_SALE_REFUND"];

export const TYPE_LABELS: Record<AccountType, string> = { CASH: "Dinheiro (gaveta)", BANK: "Banco", PAYMENT_PROVIDER: "Mercado Pago" };
export const KIND_LABELS: Record<Movement["kind"], string> = { ADJUSTMENT: "Ajuste", TRANSFER_OUT: "Transferência enviada", TRANSFER_IN: "Transferência recebida", EXPENSE_PAYMENT: "Pagamento de conta", EXPENSE_REFUND: "Estorno de conta paga", CASH_DIFFERENCE: "Diferença de caixa", OWNER_WITHDRAWAL: "Retirada do dono", OWNER_CONTRIBUTION: "Aporte do dono", COUNTER_SALE: "Venda na loja", COUNTER_SALE_REFUND: "Cancelamento de venda na loja" };

const text = (value: unknown, max: number) => typeof value === "string" && value.length <= max ? value : null;

export function parseAccount(raw: unknown): Account | null {
  if (!raw || typeof raw !== "object") return null;
  const value = raw as Record<string, unknown>;
  const id = typeof value.id === "string" && UUID.test(value.id) ? value.id : null;
  const type = TYPES.find(item => item === value.type) ?? null;
  const name = text(value.name, 240);
  const balance = typeof value.balance === "string" && BALANCE.test(value.balance) ? value.balance : null;
  const systemKey = value.systemKey === null || value.systemKey === "DEFAULT_CASH" || value.systemKey === "MERCADO_PAGO" ? value.systemKey : undefined;
  if (!id || !type || !name || !balance || systemKey === undefined) return null;
  return { id, type, name, systemKey, balance };
}

export function parseAccounts(raw: unknown): Account[] | null {
  if (!Array.isArray(raw)) return null;
  const accounts = raw.map(parseAccount);
  return accounts.every(Boolean) ? accounts as Account[] : null;
}

function parseMovement(raw: unknown): Movement | null {
  if (!raw || typeof raw !== "object") return null;
  const value = raw as Record<string, unknown>;
  const kind = KINDS.find(item => item === value.kind) ?? null;
  const id = typeof value.id === "string" && UUID.test(value.id) ? value.id : null;
  const amount = typeof value.amount === "string" && MONEY.test(value.amount) ? value.amount : null;
  const after = typeof value.balanceAfter === "string" && BALANCE.test(value.balanceAfter) ? value.balanceAfter : null;
  const day = typeof value.occurredOn === "string" && DAY.test(value.occurredOn) ? value.occurredOn : null;
  const description = value.description === null ? null : text(value.description, 800);
  const counterpart = value.counterpartAccountId === null ? null : typeof value.counterpartAccountId === "string" && UUID.test(value.counterpartAccountId) ? value.counterpartAccountId : undefined;
  if (!kind || !id || !amount || !after || !day || description === undefined || (value.description !== null && description === null) || counterpart === undefined) return null;
  return { id, kind, amount, occurredOn: day, description, counterpartAccountId: counterpart, balanceAfter: after };
}

export function parseStatement(raw: unknown): Statement | null {
  if (!raw || typeof raw !== "object") return null;
  const value = raw as Record<string, unknown>;
  const account = parseAccount(value.account);
  const movements = Array.isArray(value.movements) ? value.movements.map(parseMovement) : null;
  const ok = (field: unknown) => typeof field === "string" && BALANCE.test(field);
  const day = (field: unknown) => typeof field === "string" && DAY.test(field);
  if (!account || !movements || movements.some(item => !item) || !ok(value.openingBalance) || !ok(value.closingBalance) || !day(value.from) || !day(value.to) || (value.nextCursor !== null && typeof value.nextCursor !== "string")) return null;
  return { account, from: value.from as string, to: value.to as string, openingBalance: value.openingBalance as string, closingBalance: value.closingBalance as string, movements: movements as Movement[], nextCursor: value.nextCursor as string | null };
}

/** A typed amount in reais ("1.234,56", "-20", "15,5") as the decimal text the API takes, or an error. */
export function parseSignedAmount(value: string, allowNegative: boolean): { amount: string } | { error: string } {
  const trimmed = value.trim().replace(/\s/g, "");
  const negative = trimmed.startsWith("-");
  if (negative && !allowNegative) return { error: "Informe um valor positivo." };
  const body = (negative ? trimmed.slice(1) : trimmed).replace(/^R\$/, "");
  // Dots are thousands separators only in a well-formed group ("1.234.567,89" or "10.500"); a lone
  // dot with 1 or 2 decimals is the decimal point ("10.5"); anything else ("1234.567") is refused.
  const grouped = /^\d{1,3}(?:\.\d{3})+(?:,\d{1,2})?$/.test(body);
  const digits = grouped ? body.replace(/\./g, "") : body;
  const amount = decimalInput(digits);
  if (!amount || /^0+\.00$/.test(amount)) return { error: "Informe um valor maior que zero, com até 2 casas decimais." };
  if (amount.split(".")[0].length > 15) return { error: "Valor alto demais." };
  return { amount: negative ? `-${amount}` : amount };
}

/** Length check of a name or reason (3 characters up to {@code max}); the backend validates again. */
export function lineError(value: string, label: string, max: number): string | null {
  const length = [...value.trim()].length;
  if (length < 3) return `${label}: use pelo menos 3 caracteres.`;
  if (length > max) return `${label}: use até ${max} caracteres.`;
  return null;
}

/** Accounts that take manual adjustments and transfers: every one but the read-only Mercado Pago. */
export function manualAccounts(accounts: Account[]): Account[] {
  return accounts.filter(account => account.systemKey !== "MERCADO_PAGO");
}

/** Today's calendar day in São Paulo (the stores' calendar), as YYYY-MM-DD. */
export function todayInStore(now: Date): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "America/Sao_Paulo", year: "numeric", month: "2-digit", day: "2-digit" }).format(now);
}

/** Message for a refused account request, from the backend's code; null for unknown answers. */
export function accountRefusal(status: number, body: unknown): string | null {
  const value = body && typeof body === "object" ? body as Record<string, unknown> : {};
  const message = typeof value.message === "string" && value.message.length <= 300 ? value.message : null;
  switch (value.code) {
    case "REQUEST_CONFLICT": case "ACCOUNT_LIMIT": case "SAME_ACCOUNT": case "PROVIDER_READ_ONLY": case "MISSING": case "INVALID_REQUEST": return message;
    default: return status === 404 ? "Conta ou loja não encontrada." : null;
  }
}
