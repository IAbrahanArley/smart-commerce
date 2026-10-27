// MVP block 1d: identification of the platform operator in the legal pages. Pure, imported by the
// smoke test. The storefront keeps its own copy in storefront-web/src/lib/legal.ts; change both.

export const LEGAL = {
  /** Company name (razão social). Pending: supplied by the owner before production. */
  operatorName: null as string | null,
  /** CNPJ as printed, e.g. "00.000.000/0001-00". Pending, like the name. */
  cnpj: null as string | null,
  contactEmail: "abrahanarley@gmail.com",
  /** Date of the current text, shown as "Atualizado em". */
  updatedAt: "2026-10-26",
};

/** True while the operator is not identified: the pages then show a draft notice. */
export function legalDraft(legal = LEGAL): boolean {
  return !legal.operatorName || !legal.cnpj;
}

export function operatorText(legal = LEGAL): string {
  return legal.operatorName && legal.cnpj
    ? `${legal.operatorName}, inscrita no CNPJ sob o nº ${legal.cnpj}`
    : "[razão social e CNPJ da empresa operadora, a preencher]";
}

export function updatedText(legal = LEGAL): string {
  const [year, month, day] = legal.updatedAt.split("-");
  return `Atualizado em ${day}/${month}/${year}.`;
}
