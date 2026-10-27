// Product categories (MVP block 1c, PRD item 3; tree of up to 3 levels since Sprint 8, SC-041).
// Pure helpers, free of React and Next imports for the smoke test. The backend validates again and
// owns uniqueness, depth and cycles.

export type Category = {
  id: string; name: string; slug: string; parentId: string | null;
  depth: number; position: number; published: boolean; products: number;
};

export const MAX_CATEGORIES = 50;
export const MAX_DEPTH = 3;

/** The name as the backend stores it: trimmed, runs of spaces collapsed. */
export function categoryName(raw: string): string {
  return raw.trim().replace(/ {2,}/g, " ");
}

/** Same rule as the backend: 1 to 60 characters, one line; null when valid. */
export function categoryNameError(raw: string): string | null {
  if (/[\p{Cc}]/u.test(raw)) return "Use uma única linha.";
  const value = categoryName(raw);
  if (!value) return "Informe o nome da categoria.";
  if ([...value].length > 60) return "Use até 60 caracteres.";
  return null;
}

/** The storefront address as the backend stores it: trimmed, lower case. */
export function categorySlug(raw: string): string {
  return raw.trim().toLowerCase();
}

/** Same rule as the backend: letters, digits and single hyphens, up to 80; null when valid. */
export function categorySlugError(raw: string): string | null {
  const value = categorySlug(raw);
  if (!value) return "Informe o endereço da categoria.";
  if (value.length > 80) return "Use até 80 caracteres.";
  if (!/^[a-z0-9]+(-[a-z0-9]+)*$/.test(value)) return "Use letras sem acento, números e hífens entre as palavras.";
  return null;
}

export function parseCategories(raw: unknown): Category[] | null {
  if (!Array.isArray(raw)) return null;
  const list: Category[] = [];
  for (const item of raw) {
    if (!item || typeof item !== "object") return null;
    const value = item as Record<string, unknown>;
    if (typeof value.id !== "string" || typeof value.name !== "string" || typeof value.slug !== "string"
      || (value.parentId !== null && typeof value.parentId !== "string")
      || !Number.isInteger(value.depth) || (value.depth as number) < 1 || (value.depth as number) > MAX_DEPTH
      || !Number.isInteger(value.position) || typeof value.published !== "boolean" || typeof value.products !== "number") return null;
    list.push({
      id: value.id, name: value.name, slug: value.slug, parentId: value.parentId as string | null,
      depth: value.depth as number, position: value.position as number, published: value.published, products: value.products,
    });
  }
  return list;
}

/** "Feminino › Roupas › Camisetas": the category with its parents, for selects. */
export function categoryPath(list: Category[], id: string): string {
  const byId = new Map(list.map(category => [category.id, category]));
  const names: string[] = [];
  for (let at = byId.get(id); at && names.length <= MAX_DEPTH; at = at.parentId ? byId.get(at.parentId) : undefined) names.unshift(at.name);
  return names.join(" › ");
}

/** Levels of the subtree rooted at {@code id}, itself included. */
function height(list: Category[], id: string): number {
  return 1 + Math.max(0, ...list.filter(category => category.parentId === id).map(child => height(list, child.id)));
}

function within(list: Category[], candidate: string, id: string): boolean {
  const byId = new Map(list.map(category => [category.id, category]));
  for (let at = byId.get(candidate); at; at = at.parentId ? byId.get(at.parentId) : undefined) if (at.id === id) return true;
  return false;
}

/**
 * Categories that can hold a new subcategory ({@code moving} absent) or the category {@code moving}
 * with its subtree, without a cycle or more than {@link MAX_DEPTH} levels.
 */
export function parentOptions(list: Category[], moving?: string): Category[] {
  const levels = moving ? height(list, moving) : 1;
  return list.filter(category => category.depth + levels <= MAX_DEPTH && (!moving || !within(list, category.id, moving)));
}

/** Siblings of a category in display order (the backend already lists them that way). */
export function siblings(list: Category[], category: Category): Category[] {
  return list.filter(other => other.parentId === category.parentId);
}

export function productCountText(count: number): string {
  if (count === 0) return "Nenhum produto";
  return count === 1 ? "1 produto" : `${count} produtos`;
}

/** The message for a failed category request. */
export function categoryError(status: number, code?: string): string {
  if (status === 409 && code === "NAME_CONFLICT") return "Já existe uma categoria com esse nome no mesmo nível.";
  if (status === 409 && code === "SLUG_CONFLICT") return "Outra categoria da loja já usa esse endereço.";
  if (status === 409 && code === "LIMIT") return `A loja já tem ${MAX_CATEGORIES} categorias. Remova uma antes de criar outra.`;
  if (status === 409 && code === "DEPTH") return `As categorias podem ter no máximo ${MAX_DEPTH} níveis.`;
  if (status === 409 && code === "CYCLE") return "Uma categoria não pode ficar dentro dela mesma ou de uma subcategoria sua.";
  if (status === 409 && code === "HAS_CHILDREN") return "Mova ou remova as subcategorias antes de remover esta categoria.";
  if (status === 400) return "Confira o nome (até 60 caracteres) e o endereço (letras sem acento, números e hífens).";
  if (status === 404) return "Categoria ou loja indisponível. Recarregue a lista.";
  if (status === 401) return "Sua sessão expirou. Entre novamente para continuar.";
  return "Não foi possível concluir. Tente novamente.";
}
