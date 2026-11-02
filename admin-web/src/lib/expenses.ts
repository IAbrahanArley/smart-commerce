// Store OWNER expenses and payables (SC-020, SC-051; ADR-0015 F6, ADR-0019 C4). Pure helpers, free
// of React and Next imports for the smoke test. The backend validates everything again.

export type ExpenseCategory = "SHIPPING" | "PACKAGING" | "MARKETING" | "RENT" | "PEOPLE" | "OTHER";
export type ExpenseKind = "FIXED" | "VARIABLE";

export type Situation = "OPEN" | "OVERDUE" | "PAID" | "CANCELLED";

// SC-051: an expense is also a payable. A paid one without paidAccountId was paid before SC-051.
export type Expense = {
  id: string;
  category: ExpenseCategory;
  kind: ExpenseKind;
  description: string;
  amount: string;
  competenceDate: string;
  dueDate: string;
  supplier: string | null;
  documentNumber: string | null;
  installmentNumber: number | null;
  installmentCount: number | null;
  recurrence: "NONE" | "MONTHLY";
  situation: Situation;
  paidOn: string | null;
  paidAccountId: string | null;
  paidAccountName: string | null;
  surcharge: string;
  discount: string;
  paidTotal: string | null;
  status: "ACTIVE" | "CANCELLED";
  createdAt: string;
  cancelledAt: string | null;
  cancelReason: string | null;
};

// Payables falling due in one month: still to pay and paid among them, plus every past-due bill.
export type ExpenseMonth = { month: string; situation: Situation | null; toPay: string; paid: string; overdueCount: number; overdueTotal: string; expenses: Expense[] };

export const SITUATION_LABELS: Record<Situation, string> = { OPEN: "Em aberto", OVERDUE: "Vencida", PAID: "Paga", CANCELLED: "Cancelada" };
// The filter lives in the URL in Portuguese (?situacao=vencidas); the API takes the code.
export const SITUATION_SLUGS: Record<Situation, string> = { OPEN: "abertas", OVERDUE: "vencidas", PAID: "pagas", CANCELLED: "canceladas" };
export const SITUATION_FILTERS: Record<Situation, string> = { OPEN: "Em aberto", OVERDUE: "Vencidas", PAID: "Pagas", CANCELLED: "Canceladas" };
export const SITUATIONS = Object.keys(SITUATION_LABELS) as Situation[];
export const MAX_INSTALLMENTS = 24;

export function situationFromSlug(value: string | undefined): Situation | null {
  return SITUATIONS.find(item => SITUATION_SLUGS[item] === value) ?? null;
}

export const CATEGORY_LABELS: Record<ExpenseCategory, string> = {
  SHIPPING: "Frete",
  PACKAGING: "Embalagem",
  MARKETING: "Marketing",
  RENT: "Aluguel",
  PEOPLE: "Pessoal",
  OTHER: "Outros",
};
export const CATEGORIES = Object.keys(CATEGORY_LABELS) as ExpenseCategory[];
export const KIND_LABELS: Record<ExpenseKind, string> = { FIXED: "Fixa", VARIABLE: "Variável" };

// Months come from the URL (?mes=AAAA-MM); anything else falls back to the current month.
export function expenseMonth(value: string | undefined, today: Date): string {
  if (value && /^\d{4}-(0[1-9]|1[0-2])$/.test(value)) return value;
  return monthOf(today);
}

export function monthOf(date: Date): string {
  const parts = new Intl.DateTimeFormat("en-CA", { timeZone: "America/Sao_Paulo", year: "numeric", month: "2-digit" }).formatToParts(date);
  return `${parts.find(part => part.type === "year")!.value}-${parts.find(part => part.type === "month")!.value}`;
}

export function shiftMonth(month: string, delta: number): string {
  const [year, value] = month.split("-").map(Number);
  const index = year * 12 + (value - 1) + delta;
  return `${Math.floor(index / 12)}-${String((index % 12) + 1).padStart(2, "0")}`;
}

export function monthLabel(month: string): string {
  const [year, value] = month.split("-").map(Number);
  const text = new Intl.DateTimeFormat("pt-BR", { month: "long", year: "numeric", timeZone: "UTC" }).format(new Date(Date.UTC(year, value - 1, 1)));
  return text.charAt(0).toUpperCase() + text.slice(1);
}

export function formatDay(day: string | null): string {
  if (!day) return "—";
  const [year, month, date] = day.split("-");
  return `${date}/${month}/${year}`;
}

// Matches ExpenseInput's calendar bounds, including leap years and the store's time zone.
export function expenseDateErrors(competence: string, paid: string, now: Date): { competenceDate?: string; paidOn?: string } {
  const parts = new Intl.DateTimeFormat("en-CA", { timeZone: "America/Sao_Paulo", year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(now);
  const part = (name: string) => parts.find(item => item.type === name)!.value;
  const today = `${part("year")}-${part("month")}-${part("day")}`;
  const nextYear = Number(part("year")) + 1;
  const lastDay = new Date(Date.UTC(nextYear, Number(part("month")), 0)).getUTCDate();
  const limit = `${nextYear}-${part("month")}-${String(Math.min(Number(part("day")), lastDay)).padStart(2, "0")}`;
  const valid = (day: string) => /^\d{4}-\d{2}-\d{2}$/.test(day) && Number.isFinite(Date.parse(`${day}T12:00:00Z`)) && new Date(`${day}T12:00:00Z`).toISOString().slice(0, 10) === day;
  const errors: { competenceDate?: string; paidOn?: string } = {};
  if (!valid(competence) || competence < "2000-01-01" || competence > limit) errors.competenceDate = "Informe uma data válida desde 2000 até um ano à frente.";
  if (paid && (!valid(paid) || paid < "2000-01-01" || paid > today)) errors.paidOn = "Informe uma data válida desde 2000 até hoje, ou deixe em branco.";
  return errors;
}

// Accepts "1.234,56", "1234,56" or "1234.56"; returns the API's "1234.56" or an error message.
export function parseAmount(value: string): { amount: string } | { error: string } {
  const text = value.trim().replace(/^R\$\s*/, "");
  let normalized = text;
  if (/^\d{1,3}(\.\d{3})+(,\d{1,2})?$/.test(text)) normalized = text.replace(/\./g, "").replace(",", ".");
  else if (/^\d+,\d{1,2}$/.test(text)) normalized = text.replace(",", ".");
  if (!/^\d{1,15}(\.\d{1,2})?$/.test(normalized) || Number(normalized) <= 0) return { error: "Informe um valor maior que zero, com até duas casas decimais." };
  const [whole, fraction = ""] = normalized.split(".");
  return { amount: `${whole}.${fraction.padEnd(2, "0")}` };
}

// Mirrors the backend: one line, no control or invisible characters, between min and max characters.
export function lineText(value: string, max: number, what: string): { text: string } | { error: string } {
  if (/[\p{Cc}\p{Cf}\p{Zl}\p{Zp}]/u.test(value)) return { error: `Escreva ${what} em uma única linha, sem caracteres especiais invisíveis.` };
  const text = value.trim();
  const length = [...text].length;
  if (length < 3) return { error: `Informe ${what} com pelo menos 3 caracteres.` };
  if (length > max) return { error: `Use até ${max} caracteres.` };
  return { text };
}

// Interest, fine or discount: blank is zero; otherwise like parseAmount, but zero is allowed.
export function parseOptionalAmount(value: string): { amount: string } | { error: string } {
  if (!value.trim()) return { amount: "0.00" };
  if (/^\s*(R\$\s*)?0+([.,]0{1,2})?\s*$/.test(value)) return { amount: "0.00" };
  const parsed = parseAmount(value);
  return "error" in parsed ? { error: "Informe um valor com até duas casas decimais, ou deixe em branco." } : parsed;
}

const cents = (amount: string) => BigInt(amount.replace(".", ""));
// BigInt() instead of literals: the build targets ES2017.
const ZERO = BigInt(0); const HUNDRED = BigInt(100);
const reais = (value: bigint) => { const sign = value < ZERO ? "-" : ""; const abs = value < ZERO ? -value : value; return `${sign}${abs / HUNDRED}.${String(abs % HUNDRED).padStart(2, "0")}`; };

// Preview only, mirroring ExpenseInput.installmentAmounts: equal cents, the last absorbing the rest.
export function installmentAmounts(total: string, count: number): string[] {
  const all = cents(total); const each = all / BigInt(count);
  return Array.from({ length: count }, (_, index) => reais(index < count - 1 ? each : all - each * BigInt(count - 1)));
}

// Preview only: what leaves the account when paying (amount + interest or fine − discount).
export function paymentTotal(amount: string, surcharge: string, discount: string): string {
  return reais(cents(amount) + cents(surcharge) - cents(discount));
}
