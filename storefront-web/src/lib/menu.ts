// Sprint 8 (SC-044): the category menu of the storefront, read from the public catalog (SC-042).
// Pure helpers, free of React and Next imports for the smoke test. Anything outside the expected
// shape is dropped: a broken menu must never break the page around it.

export type MenuItem = { name: string; slug: string; children: MenuItem[] };

const SLUG = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const MAX_DEPTH = 3;
const MAX_ITEMS = 50;

/** The menu of a catalog response; [] when absent or malformed. */
export function parseMenu(raw: unknown): MenuItem[] {
  if (!raw || typeof raw !== "object") return [];
  let count = 0;
  const items = (value: unknown, depth: number): MenuItem[] | null => {
    if (!Array.isArray(value) || depth > MAX_DEPTH) return null;
    const list: MenuItem[] = [];
    for (const entry of value) {
      if (!entry || typeof entry !== "object" || ++count > MAX_ITEMS) return null;
      const item = entry as Record<string, unknown>;
      if (typeof item.name !== "string" || !item.name.trim() || [...item.name].length > 60
        || typeof item.slug !== "string" || item.slug.length > 80 || !SLUG.test(item.slug)) return null;
      const children = item.children === undefined ? [] : items(item.children, depth + 1);
      if (!children) return null;
      list.push({ name: item.name, slug: item.slug, children });
    }
    return list;
  };
  return items((raw as Record<string, unknown>).menu, 1) ?? [];
}

/** Storefront path of a category page (SC-045). */
export function categoryPath(storeSlug: string, categorySlug: string): string {
  return `/lojas/${encodeURIComponent(storeSlug)}/c/${encodeURIComponent(categorySlug)}`;
}
