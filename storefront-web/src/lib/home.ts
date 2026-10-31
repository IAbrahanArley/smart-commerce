// Sprint 8 (SC-043): the store home as the owner configured it in "Aparência e conteúdo": banners
// shown now and the ordered sections. Pure helpers for the smoke test; a malformed entry is dropped.

import { categoryPath } from "./menu.ts";

export type Banner = {
  id: string; alt: string; title: string | null;
  linkKind: "NONE" | "CATEGORY" | "PRODUCT" | "SEARCH"; linkTarget: string | null; mobileImage: boolean;
  /** SC-048: image sizes, so the page reserves the space before the image loads. */
  width: number | null; height: number | null; mobileWidth: number | null; mobileHeight: number | null;
};
export type SectionKind = "NEW" | "BEST" | "FEATURED" | "CATEGORY";
export type Section = { kind: SectionKind; title: string; categorySlug: string | null };
export type Home = { banners: Banner[]; sections: Section[] };

/** The home of a store that configured no section: its newest products. */
export const DEFAULT_SECTIONS: Section[] = [{ kind: "NEW", title: "Lançamentos", categorySlug: null }];

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const SLUG = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const LINE = /^[^\p{Cc}]+$/u;

function line(value: unknown, max: number): string | null {
  return typeof value === "string" && value.trim() && [...value].length <= max && LINE.test(value) ? value : null;
}

function banner(raw: unknown): Banner[] {
  if (!raw || typeof raw !== "object") return [];
  const value = raw as Record<string, unknown>;
  const alt = line(value.alt, 200);
  if (typeof value.id !== "string" || !UUID.test(value.id) || !alt) return [];
  const kind = value.linkKind;
  let target: string | null = null;
  if (kind === "CATEGORY" || kind === "PRODUCT") {
    if (typeof value.linkTarget !== "string" || value.linkTarget.length > 80 || !SLUG.test(value.linkTarget)) return [];
    target = value.linkTarget;
  } else if (kind === "SEARCH") {
    target = line(value.linkTarget, 100);
    if (!target) return [];
  } else if (kind !== "NONE") return [];
  const size = (entry: unknown) => Number.isInteger(entry) && (entry as number) >= 1 && (entry as number) <= 10000 ? entry as number : null;
  const mobileImage = value.mobileImage === true;
  return [{
    id: value.id, alt, title: line(value.title, 80), linkKind: kind, linkTarget: target, mobileImage,
    width: size(value.width), height: size(value.height),
    mobileWidth: mobileImage ? size(value.mobileWidth) : null, mobileHeight: mobileImage ? size(value.mobileHeight) : null,
  }];
}

function section(raw: unknown): Section[] {
  if (!raw || typeof raw !== "object") return [];
  const value = raw as Record<string, unknown>;
  const title = line(value.title, 60);
  if (!title) return [];
  if (value.kind === "CATEGORY")
    return typeof value.categorySlug === "string" && value.categorySlug.length <= 80 && SLUG.test(value.categorySlug)
      ? [{ kind: "CATEGORY", title, categorySlug: value.categorySlug }] : [];
  return value.kind === "NEW" || value.kind === "BEST" || value.kind === "FEATURED"
    ? [{ kind: value.kind, title, categorySlug: null }] : [];
}

export function parseHome(raw: unknown): Home {
  const value = raw && typeof raw === "object" ? raw as Record<string, unknown> : {};
  return {
    banners: Array.isArray(value.banners) ? value.banners.flatMap(banner).slice(0, 5) : [],
    sections: Array.isArray(value.sections) ? value.sections.flatMap(section).slice(0, 8) : [],
  };
}

/** Where a banner leads, always inside the store; null when it has no link. */
export function bannerHref(storeSlug: string, value: Banner): string | null {
  const store = `/lojas/${encodeURIComponent(storeSlug)}`;
  switch (value.linkKind) {
    case "CATEGORY": return categoryPath(storeSlug, value.linkTarget!);
    case "PRODUCT": return `${store}/p/${encodeURIComponent(value.linkTarget!)}`;
    case "SEARCH": return `${store}/busca?${new URLSearchParams({ q: value.linkTarget! })}`;
    default: return null;
  }
}

export function bannerImage(storeSlug: string, id: string, variant: "desktop" | "mobile"): string {
  return `/midia/lojas/${encodeURIComponent(storeSlug)}/banners/${encodeURIComponent(id)}/${variant}`;
}

/** The catalog query that fills a section. */
export function sectionQuery(value: Section): URLSearchParams {
  switch (value.kind) {
    case "NEW": return new URLSearchParams({ ordem: "recentes" });
    case "BEST": return new URLSearchParams({ ordem: "mais-vendidos" });
    case "FEATURED": return new URLSearchParams({ destaque: "1", ordem: "recentes" });
    case "CATEGORY": return new URLSearchParams({ categoria: value.categorySlug!, ordem: "recentes" });
  }
}

/** The "see all" page of a section. */
export function sectionHref(storeSlug: string, value: Section): string {
  const store = `/lojas/${encodeURIComponent(storeSlug)}`;
  switch (value.kind) {
    case "NEW": return `${store}/busca?ordem=recentes`;
    case "BEST": return `${store}/busca?ordem=mais-vendidos`;
    case "FEATURED": return `${store}/busca?destaque=1`;
    case "CATEGORY": return categoryPath(storeSlug, value.categorySlug!);
  }
}
