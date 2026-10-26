// Product categories (MVP block 1c, PRD item 3). Pure helpers, free of React and Next imports for
// the smoke test. The backend validates again and owns uniqueness per store.

export type Category = { id: string; name: string; products: number };

export const MAX_CATEGORIES = 50;

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

export function parseCategories(raw: unknown): Category[] | null {
  if (!Array.isArray(raw)) return null;
  const list: Category[] = [];
  for (const item of raw) {
    if (!item || typeof item !== "object") return null;
    const value = item as Record<string, unknown>;
    if (typeof value.id !== "string" || typeof value.name !== "string" || typeof value.products !== "number") return null;
    list.push({ id: value.id, name: value.name, products: value.products });
  }
  return list;
}

export function productCountText(count: number): string {
  if (count === 0) return "Nenhum produto";
  return count === 1 ? "1 produto" : `${count} produtos`;
}

/** The message for a failed category request. */
export function categoryError(status: number, code?: string): string {
  if (status === 409 && code === "NAME_CONFLICT") return "Já existe uma categoria com esse nome na loja.";
  if (status === 409 && code === "LIMIT") return `A loja já tem ${MAX_CATEGORIES} categorias. Remova uma antes de criar outra.`;
  if (status === 400) return "Informe um nome de categoria com até 60 caracteres.";
  if (status === 404) return "Categoria ou loja indisponível. Recarregue a lista.";
  if (status === 401) return "Sua sessão expirou. Entre novamente para continuar.";
  return "Não foi possível concluir. Tente novamente.";
}
