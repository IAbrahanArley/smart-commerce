"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { apiRequest } from "@/lib/auth-api";
import type { Product } from "@/lib/catalog";
import { type Category, categoryError, categoryPath, parseCategories } from "@/lib/categories";
import { Loading } from "@/components/ui/loading";
import { useUnsavedChanges } from "@/lib/use-unsaved-changes";

const MAX_ADDITIONAL = 10;

// MVP block 1c: the category of one product, saved with its own button (saving on change would send
// one request per arrow key in a closed select). It does not touch the product version, so it never
// conflicts with unsaved edits in the main form. SC-043: the other
// categories it also appears in ("Promoções", "Outlet"), saved together with their own button.
export default function ProductCategory({ tenantId, product, onSaved }: {
  tenantId: string; product: Product; onSaved: (product: Product, notice?: string) => void;
}) {
  const [categories, setCategories] = useState<Category[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ text: string; error: boolean } | null>(null);
  const [extra, setExtra] = useState<string[]>(product.additionalCategoryIds ?? []);
  const [chosen, setChosen] = useState(product.categoryId ?? "");
  const submitting = useRef(false);
  const tenantPath = `/api/tenants/${encodeURIComponent(tenantId)}`;
  const storePath = `/lojas/${encodeURIComponent(tenantId)}`;
  useUnsavedChanges(chosen !== (product.categoryId ?? "") || [...extra].sort().join() !== [...(product.additionalCategoryIds ?? [])].sort().join());

  useEffect(() => {
    const controller = new AbortController();
    apiRequest(`${tenantPath}/categories`, { signal: controller.signal }).then(async response => {
      const parsed = response.ok ? parseCategories(await response.json()) : null;
      if (!parsed) throw new Error();
      if (!controller.signal.aborted) setCategories(parsed);
    }).catch(() => { if (!controller.signal.aborted) setFailed(true); });
    return () => controller.abort();
  }, [tenantPath]);

  async function choose(value: string) {
    if (submitting.current) return;
    submitting.current = true; setBusy(true); setMessage(null);
    try {
      const response = await apiRequest(`${tenantPath}/products/${encodeURIComponent(product.id)}/category`, { method: "PUT", body: { categoryId: value || null } });
      if (!response.ok) {
        const body: { code?: string } = await response.json().catch(() => ({}));
        setMessage({ text: categoryError(response.status, body.code), error: true }); return;
      }
      const saved: Product = await response.json();
      onSaved(saved);
      setChosen(saved.categoryId ?? "");
      const name = categories && value ? categoryPath(categories, value) : "";
      setMessage({ text: name ? `Categoria “${name}” salva.` : "Produto sem categoria.", error: false });
    } catch { setMessage({ text: "Não foi possível confirmar a categoria. Recarregue o produto.", error: true }); }
    finally { submitting.current = false; setBusy(false); }
  }

  async function saveExtra() {
    if (submitting.current) return;
    if (extra.length > MAX_ADDITIONAL) { setMessage({ text: `Escolha até ${MAX_ADDITIONAL} categorias adicionais.`, error: true }); return; }
    submitting.current = true; setBusy(true); setMessage(null);
    try {
      const response = await apiRequest(`${tenantPath}/products/${encodeURIComponent(product.id)}/categories`, { method: "PUT", body: { categoryIds: extra } });
      if (!response.ok) {
        const body: { code?: string } = await response.json().catch(() => ({}));
        setMessage({ text: categoryError(response.status, body.code), error: true }); return;
      }
      const saved: Product = await response.json();
      onSaved(saved);
      setExtra(saved.additionalCategoryIds ?? []);
      setMessage({ text: "Categorias adicionais salvas.", error: false });
    } catch { setMessage({ text: "Não foi possível confirmar as categorias. Recarregue o produto.", error: true }); }
    finally { submitting.current = false; setBusy(false); }
  }

  const others = categories?.filter(category => category.id !== product.categoryId) ?? [];
  return <section className="side-card" aria-labelledby="product-category-title">
    <h2 id="product-category-title">Categoria</h2>
    {failed ? <p role="alert" className="feedback feedback-error">Não foi possível consultar as categorias.</p> : null}
    {!categories && !failed ? <Loading context="categorias" className="py-4" /> : null}
    {categories ? categories.length === 0
      ? <p>A loja ainda não tem categorias. <Link href={`${storePath}/categorias`}>Criar categorias</Link></p>
      : <div className="form-field">
        <label htmlFor="product-category">Categoria do produto</label>
        <select id="product-category" value={chosen} onChange={event => setChosen(event.target.value)} aria-describedby="product-category-help">
          <option value="">Sem categoria</option>
          {categories.map(category => <option key={category.id} value={category.id}>{categoryPath(categories, category.id)}</option>)}
        </select>
        <p id="product-category-help" className="field-help">Define o caminho do produto na vitrine. <Link href={`${storePath}/categorias`}>Gerenciar categorias</Link></p>
        <button type="button" className="button-secondary" disabled={busy || chosen === (product.categoryId ?? "")} onClick={() => void choose(chosen)}>{busy ? "Salvando…" : "Salvar categoria"}</button>
      </div> : null}
    {categories && others.length ? <fieldset className="category-extra" disabled={busy}>
      <legend>Também aparece em</legend>
      <p className="field-help">Para coleções como “Promoções” ou “Outlet”. Até {MAX_ADDITIONAL}.</p>
      {others.map(category => <label key={category.id} className="check-field">
        <input type="checkbox" checked={extra.includes(category.id)}
          onChange={event => setExtra(current => event.target.checked ? [...current, category.id] : current.filter(id => id !== category.id))} />
        {categoryPath(categories, category.id)}
      </label>)}
      <button type="button" className="button-secondary" onClick={() => void saveExtra()}>Salvar categorias adicionais</button>
    </fieldset> : null}
    {message ? <p role={message.error ? "alert" : "status"} className={`feedback feedback-${message.error ? "error" : "success"}`}>{message.text}</p> : null}
  </section>;
}
