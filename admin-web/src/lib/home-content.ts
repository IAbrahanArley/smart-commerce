// Sprint 8 (SC-043): banners and sections of the store home. Pure helpers, free of React and Next
// imports for the smoke test. The backend validates again and owns the limits.

export type LinkKind = "NONE" | "CATEGORY" | "PRODUCT" | "SEARCH";
export type Banner = {
  id: string; position: number; alt: string; title: string | null; linkKind: LinkKind; linkTarget: string | null;
  startsAt: string | null; endsAt: string | null; desktopImage: boolean; mobileImage: boolean;
};
export type SectionKind = "NEW" | "BEST" | "FEATURED" | "CATEGORY";
export type Section = { kind: SectionKind; title: string; categoryId: string | null };
/** The banner form: every field a string; dates in the datetime-local format. */
export type BannerDraft = { alt: string; title: string; linkKind: LinkKind; linkTarget: string; startsAt: string; endsAt: string };

export const MAX_BANNERS = 5;
export const MAX_SECTIONS = 8;
export const EMPTY_BANNER: BannerDraft = { alt: "", title: "", linkKind: "NONE", linkTarget: "", startsAt: "", endsAt: "" };
export const LINK_KINDS: { value: LinkKind; label: string }[] = [
  { value: "NONE", label: "Sem link" },
  { value: "CATEGORY", label: "Categoria" },
  { value: "PRODUCT", label: "Produto" },
  { value: "SEARCH", label: "Busca" },
];
export const SECTION_KINDS: { value: SectionKind; label: string; title: string }[] = [
  { value: "NEW", label: "Lançamentos (mais recentes)", title: "Lançamentos" },
  { value: "BEST", label: "Mais vendidos (90 dias)", title: "Mais vendidos" },
  { value: "FEATURED", label: "Produtos em destaque", title: "Destaques" },
  { value: "CATEGORY", label: "Uma categoria", title: "" },
];

const LINK_VALUES = new Set(LINK_KINDS.map(kind => kind.value));
const SECTION_VALUES = new Set(SECTION_KINDS.map(kind => kind.value));
const SLUG = /^[a-z0-9]+(-[a-z0-9]+)*$/;

function optional(value: unknown): value is string | null {
  return value === null || typeof value === "string";
}

export function parseBanners(raw: unknown): Banner[] | null {
  if (!Array.isArray(raw)) return null;
  const list: Banner[] = [];
  for (const item of raw) {
    if (!item || typeof item !== "object") return null;
    const value = item as Record<string, unknown>;
    if (typeof value.id !== "string" || !Number.isInteger(value.position) || typeof value.alt !== "string"
      || !optional(value.title) || !LINK_VALUES.has(value.linkKind as LinkKind) || !optional(value.linkTarget)
      || !optional(value.startsAt) || !optional(value.endsAt)
      || typeof value.desktopImage !== "boolean" || typeof value.mobileImage !== "boolean") return null;
    list.push({
      id: value.id, position: value.position as number, alt: value.alt, title: value.title, linkKind: value.linkKind as LinkKind,
      linkTarget: value.linkTarget, startsAt: value.startsAt, endsAt: value.endsAt, desktopImage: value.desktopImage, mobileImage: value.mobileImage,
    });
  }
  return list;
}

export function parseSections(raw: unknown): Section[] | null {
  if (!Array.isArray(raw)) return null;
  const list: Section[] = [];
  for (const item of raw) {
    if (!item || typeof item !== "object") return null;
    const value = item as Record<string, unknown>;
    if (!SECTION_VALUES.has(value.kind as SectionKind) || typeof value.title !== "string" || !optional(value.categoryId)) return null;
    list.push({ kind: value.kind as SectionKind, title: value.title, categoryId: value.categoryId });
  }
  return list;
}

/** "2026-11-01T10:30" in the browser's time zone, from an ISO instant. */
export function toLocalInput(iso: string | null): string {
  if (!iso) return "";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** The ISO instant of a datetime-local value in the browser's time zone; null when empty. */
export function fromLocalInput(value: string): string | null {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

export function bannerDraft(banner: Banner): BannerDraft {
  return {
    alt: banner.alt, title: banner.title ?? "", linkKind: banner.linkKind, linkTarget: banner.linkTarget ?? "",
    startsAt: toLocalInput(banner.startsAt), endsAt: toLocalInput(banner.endsAt),
  };
}

/** Same rules as the backend, by field; empty when the draft can be sent. */
export function bannerErrors(draft: BannerDraft): Partial<Record<keyof BannerDraft, string>> {
  const errors: Partial<Record<keyof BannerDraft, string>> = {};
  const alt = draft.alt.trim();
  if (!alt) errors.alt = "Descreva a imagem para quem não pode vê-la.";
  else if ([...alt].length > 200) errors.alt = "Use até 200 caracteres.";
  if ([...draft.title.trim()].length > 80) errors.title = "Use até 80 caracteres.";
  const target = draft.linkTarget.trim();
  if (draft.linkKind === "CATEGORY" && !target) errors.linkTarget = "Escolha a categoria.";
  if (draft.linkKind === "PRODUCT" && (!SLUG.test(target.toLowerCase()) || target.length > 80))
    errors.linkTarget = "Informe o endereço do produto na vitrine, como “camiseta-azul”.";
  if (draft.linkKind === "SEARCH" && (!target || [...target].length > 100)) errors.linkTarget = "Informe o que buscar, com até 100 caracteres.";
  const starts = fromLocalInput(draft.startsAt), ends = fromLocalInput(draft.endsAt);
  if (draft.startsAt && !starts) errors.startsAt = "Data inválida.";
  if (draft.endsAt && !ends) errors.endsAt = "Data inválida.";
  else if (starts && ends && starts >= ends) errors.endsAt = "O fim precisa ser depois do início.";
  return errors;
}

export function bannerBody(draft: BannerDraft) {
  return {
    alt: draft.alt.trim(), title: draft.title.trim() || null, linkKind: draft.linkKind,
    linkTarget: draft.linkKind === "NONE" ? null : draft.linkKind === "PRODUCT" ? draft.linkTarget.trim().toLowerCase() : draft.linkTarget.trim(),
    startsAt: fromLocalInput(draft.startsAt), endsAt: fromLocalInput(draft.endsAt),
  };
}

/** Same rule as the backend: a title of 1 to 60 characters; a category only for a category section. */
export function sectionError(section: Section): string | null {
  const title = section.title.trim();
  if (!title) return "Dê um título à seção.";
  if ([...title].length > 60) return "Use até 60 caracteres.";
  if (section.kind === "CATEGORY" && !section.categoryId) return "Escolha a categoria da seção.";
  return null;
}

/** The list with the item at {@code index} moved one place; unchanged at the ends. */
export function move<T>(list: T[], index: number, step: -1 | 1): T[] {
  const target = index + step;
  if (target < 0 || target >= list.length) return list;
  const next = [...list];
  [next[index], next[target]] = [next[target], next[index]];
  return next;
}

export function homeError(status: number, code?: string): string {
  if (code === "BANNER_LIMIT") return `A loja já tem o máximo de ${MAX_BANNERS} banners.`;
  if (status === 413) return "Use uma imagem de até 5 MiB.";
  if (status === 400) return "Confira os campos: algum valor não está no formato esperado.";
  if (status === 404) return "Banner, categoria ou loja não encontrados. Recarregue a página.";
  if (status === 401) return "Sua sessão expirou. Entre novamente para continuar.";
  return "Não foi possível salvar. Tente novamente.";
}
