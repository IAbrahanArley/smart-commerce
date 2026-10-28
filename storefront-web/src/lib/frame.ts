// Sprint 8 (SC-044/SC-043): what the store frame shows around every page, read from the public
// catalog response: category menu, company details and the institutional pages the store wrote.
// Pure helpers for the smoke test; a malformed field is dropped, never shown.

import { type MenuItem, parseMenu } from "./menu.ts";

export type Company = {
  announcement: string | null; legalName: string | null; cnpj: string | null; phone: string | null;
  address: string | null; facebook: string | null; tiktok: string | null; youtube: string | null;
};
export type PageLink = { name: string; slug: string };
export type Frame = { menu: MenuItem[]; company: Company | null; pages: PageLink[] };

export const EMPTY_FRAME: Frame = { menu: [], company: null, pages: [] };

const FORMATS: Record<keyof Company, RegExp> = {
  announcement: /^[^\p{Cc}]{1,120}$/u,
  legalName: /^[^\p{Cc}]{1,120}$/u,
  cnpj: /^\d{14}$/,
  phone: /^55[1-9]\d{9,10}$/,
  address: /^[^\p{Cc}]{1,240}$/u,
  facebook: /^[A-Za-z0-9.]{1,100}$/,
  tiktok: /^[A-Za-z0-9._]{1,30}$/,
  youtube: /^[A-Za-z0-9._-]{1,100}$/,
};

function company(raw: unknown): Company | null {
  if (!raw || typeof raw !== "object") return null;
  const value = raw as Record<string, unknown>;
  const result = {} as Company;
  for (const key of Object.keys(FORMATS) as (keyof Company)[]) {
    const entry = value[key];
    result[key] = typeof entry === "string" && FORMATS[key].test(entry) ? entry : null;
  }
  return Object.values(result).some(Boolean) ? result : null;
}

function pages(raw: unknown): PageLink[] {
  if (!Array.isArray(raw)) return [];
  return raw.flatMap(entry => {
    if (!entry || typeof entry !== "object") return [];
    const page = entry as Record<string, unknown>;
    return typeof page.name === "string" && page.name.length <= 60 && typeof page.slug === "string" && /^[a-z]+(?:-[a-z]+)*$/.test(page.slug)
      ? [{ name: page.name, slug: page.slug }] : [];
  }).slice(0, 10);
}

export function parseFrame(raw: unknown): Frame {
  if (!raw || typeof raw !== "object") return EMPTY_FRAME;
  const value = raw as Record<string, unknown>;
  return { menu: parseMenu(raw), company: company(value.company), pages: pages(value.pages) };
}

export function formatCnpj(digits: string): string {
  return digits.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, "$1.$2.$3/$4-$5");
}

/** "(83) 3333-4444" from E.164 digits. */
export function formatPhone(digits: string): string {
  const match = /^55(\d{2})(\d{4,5})(\d{4})$/.exec(digits);
  return match ? `(${match[1]}) ${match[2]}-${match[3]}` : digits;
}

export function pagePath(storeSlug: string, pageSlug: string): string {
  return `/lojas/${encodeURIComponent(storeSlug)}/paginas/${encodeURIComponent(pageSlug)}`;
}
