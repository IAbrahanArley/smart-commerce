// Store OWNER manual expenses (SC-020, ADR-0015 F6). Pure helpers, free of React and Next imports
// for the smoke test. The backend validates everything again.

export type ExpenseCategory = "SHIPPING" | "PACKAGING" | "MARKETING" | "RENT" | "PEOPLE" | "OTHER";
export type ExpenseKind = "FIXED" | "VARIABLE";

export type Expense = {
  id: string;
  category: ExpenseCategory;
  kind: ExpenseKind;
  description: string;
  amount: string;
  competenceDate: string;
  paidOn: string | null;
  status: "ACTIVE" | "CANCELLED";
  createdAt: string;
  cancelledAt: string | null;
  cancelReason: string | null;
};

export type ExpenseMonth = { month: string; total: string; fixed: string; variable: string; expenses: Expense[] };

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
