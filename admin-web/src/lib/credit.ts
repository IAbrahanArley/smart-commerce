// SC-055 (ADR-0019 C9): store customers and store credit ("crediário"). Pure helpers, free of
// React and Next imports for the smoke test. The backend decides limits, balances and allocation;
// this only previews and checks the form before sending.

export type Customer = { id: string; name: string; phone: string; email: string | null; note: string | null; creditLimit: string | null };
export type Installment = { id: string; saleId: string; soldAt: string; number: number; count: number; dueOn: string; amount: string; received: string; open: string; overdue: boolean };
export type CreditEvent = { kind: "SALE" | "RECEIPT"; id: string; at: string; amount: string; installmentCount: number | null; status: "ACTIVE" | "CANCELLED" | null; accountName: string | null; by: string };
export type Destination = { id: string; name: string };
export type CreditCustomer = {
  customer: Customer; open: string; overdue: string; availableCredit: string | null; installments: Installment[];
  history: CreditEvent[]; nextCursor: string | null; sessions: Destination[]; banks: Destination[];
};
export type CustomerLine = { id: string; name: string; phone: string; creditLimit: string | null; open: string; overdue: string; nextDueOn: string | null };
export type CreditOverview = { open: string; overdue: string; overdueCustomers: number; customers: CustomerLine[]; nextCursor: string | null };
export type CreditFilter = "ALL" | "OPEN" | "OVERDUE";

export const FILTERS: { value: CreditFilter; slug: string; label: string }[] = [
  { value: "ALL", slug: "todos", label: "Todos" },
  { value: "OPEN", slug: "com-saldo", label: "Com saldo" },
  { value: "OVERDUE", slug: "vencidos", label: "Vencidos" },
];

export function filterFromSlug(value: string | null | undefined): CreditFilter {
  return FILTERS.find(filter => filter.slug === value)?.value ?? "ALL";
}

const cents = (amount: string) => BigInt(amount.replace(".", ""));
// BigInt() instead of literals: the build targets ES2017.
const ZERO = BigInt(0); const HUNDRED = BigInt(100);
const reais = (value: bigint) => { const sign = value < ZERO ? "-" : ""; const abs = value < ZERO ? -value : value; return `${sign}${abs / HUNDRED}.${String(abs % HUNDRED).padStart(2, "0")}`; };

/** Mirrors Credit.split: equal cents, the last installment absorbing the rest; null when a cent is missing. */
export function splitInstallments(total: string, count: number): string[] | null {
  const all = cents(total); const each = all / BigInt(count);
  if (each <= ZERO) return null;
  return Array.from({ length: count }, (_, index) => reais(index < count - 1 ? each : all - each * BigInt(count - 1)));
}

/** Like LocalDate.plusMonths: the same day, or the month's last day when it is shorter. */
export function addMonths(day: string, months: number): string {
  const [year, month, date] = day.split("-").map(Number);
  const target = new Date(Date.UTC(year, month - 1 + months, 1));
  const last = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0)).getUTCDate();
  return `${target.getUTCFullYear()}-${String(target.getUTCMonth() + 1).padStart(2, "0")}-${String(Math.min(date, last)).padStart(2, "0")}`;
}

/** Today in São Paulo, as yyyy-mm-dd. */
export function todayInStore(now: Date): string {
  const parts = new Intl.DateTimeFormat("en-CA", { timeZone: "America/Sao_Paulo", year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(now);
  const part = (name: string) => parts.find(item => item.type === name)!.value;
  return `${part("year")}-${part("month")}-${part("day")}`;
}

/** The first due date the backend accepts: from today to one year ahead. */
export function firstDueError(day: string, today: string): string | null {
  const valid = /^\d{4}-\d{2}-\d{2}$/.test(day) && Number.isFinite(Date.parse(`${day}T12:00:00Z`)) && new Date(`${day}T12:00:00Z`).toISOString().slice(0, 10) === day;
  if (!valid || day < today || day > addMonths(today, 12)) return "Escolha uma data de hoje até um ano à frente.";
  return null;
}

/** Phone as the backend keeps it: 10 to 15 digits, an optional leading "+". */
export function phoneDigits(value: string): string | null {
  const digits = value.replace(/[\s().-]/g, "");
  return /^\+?\d{10,15}$/.test(digits) ? digits : null;
}

/** Brazilian phones shown as typed by people: (11) 98765-4321; masked or foreign ones unchanged. */
export function phoneText(phone: string): string {
  const local = phone.startsWith("+55") ? phone.slice(3) : phone;
  if (/^\d{11}$/.test(local)) return `(${local.slice(0, 2)}) ${local.slice(2, 7)}-${local.slice(7)}`;
  if (/^\d{10}$/.test(local)) return `(${local.slice(0, 2)}) ${local.slice(2, 6)}-${local.slice(6)}`;
  return phone;
}

/** Name: one line of 2 to 120 characters, like CustomerInput. */
export function nameError(value: string): string | null {
  if (/[\p{Cc}\p{Cf}\p{Zl}\p{Zp}]/u.test(value)) return "Escreva o nome em uma única linha.";
  const length = [...value.trim()].length;
  if (length < 2) return "Informe o nome do cliente.";
  if (length > 120) return "Use até 120 caracteres.";
  return null;
}

export function installmentText(installment: Pick<Installment, "number" | "count">): string {
  return installment.count === 1 ? "Parcela única" : `Parcela ${installment.number} de ${installment.count}`;
}

/** Whether the receipt fits what is owed (the backend refuses more). */
export function withinOwed(amount: string, open: string): boolean {
  return cents(amount) <= cents(open);
}
