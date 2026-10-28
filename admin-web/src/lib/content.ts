// Sprint 8 (SC-043): company details and institutional pages of the store. Pure helpers, free of
// React and Next imports for the smoke test. The backend normalizes and validates again.

export type Company = {
  announcement: string; legalName: string; cnpj: string; phone: string; address: string;
  facebook: string; tiktok: string; youtube: string;
};
export type StorePage = { kind: string; slug: string; title: string; bodyHtml: string };

export const EMPTY_COMPANY: Company = { announcement: "", legalName: "", cnpj: "", phone: "", address: "", facebook: "", tiktok: "", youtube: "" };
const FIELDS = Object.keys(EMPTY_COMPANY) as (keyof Company)[];

/** The company as the form edits it: every field a string, null becomes "". */
export function parseCompany(raw: unknown): Company | null {
  if (!raw || typeof raw !== "object") return null;
  const value = raw as Record<string, unknown>;
  const company = { ...EMPTY_COMPANY };
  for (const field of FIELDS) {
    const entry = value[field];
    if (entry !== null && entry !== undefined && typeof entry !== "string") return null;
    company[field] = typeof entry === "string" ? entry : "";
  }
  // Stored digits shown the way people read them.
  if (/^\d{14}$/.test(company.cnpj)) company.cnpj = formatCnpj(company.cnpj);
  return company;
}

export function parsePages(raw: unknown): StorePage[] | null {
  if (!Array.isArray(raw)) return null;
  const pages: StorePage[] = [];
  for (const item of raw) {
    if (!item || typeof item !== "object") return null;
    const page = item as Record<string, unknown>;
    if (typeof page.kind !== "string" || typeof page.slug !== "string" || !/^[a-z-]{1,40}$/.test(page.slug)
      || typeof page.title !== "string" || typeof page.bodyHtml !== "string") return null;
    pages.push({ kind: page.kind, slug: page.slug, title: page.title, bodyHtml: page.bodyHtml });
  }
  return pages;
}

export function formatCnpj(digits: string): string {
  return digits.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, "$1.$2.$3/$4-$5");
}

/** Same check digits as the backend (Receita Federal module 11); null when valid or empty. */
export function cnpjError(raw: string): string | null {
  const value = raw.trim();
  if (!value) return null;
  const digits = value.replace(/\D/g, "");
  if (!/^[0-9./\s-]+$/.test(value) || digits.length !== 14 || /^(\d)\1{13}$/.test(digits)) return "Informe os 14 números do CNPJ.";
  const check = (length: number) => {
    let sum = 0, weight = length - 7;
    for (let index = 0; index < length; index++) { sum += Number(digits[index]) * weight; weight = weight === 2 ? 9 : weight - 1; }
    const rest = sum % 11;
    return rest < 2 ? 0 : 11 - rest;
  };
  return check(12) === Number(digits[12]) && check(13) === Number(digits[13]) ? null : "CNPJ inválido. Confira os números.";
}

/** The message for a failed content request. */
export function contentError(status: number): string {
  if (status === 400) return "Confira os campos: algum valor não está no formato esperado.";
  if (status === 404) return "Loja indisponível. Recarregue a página.";
  if (status === 401) return "Sua sessão expirou. Entre novamente para continuar.";
  return "Não foi possível salvar. Tente novamente.";
}
