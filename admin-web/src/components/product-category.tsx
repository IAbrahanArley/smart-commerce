"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { apiRequest } from "@/lib/auth-api";
import type { Product } from "@/lib/catalog";
import { type Category, categoryError, categoryPath, parseCategories } from "@/lib/categories";
import { Loading } from "@/components/ui/loading";

// MVP block 1c: the category of one product, saved as soon as it is chosen. It does not touch the
// product version, so it never conflicts with unsaved edits in the main form.
export default function ProductCategory({ tenantId, product, onSaved }: {
  tenantId: string; product: Product; onSaved: (product: Product, notice?: string) => void;
}) {
  const [categories, setCategories] = useState<Category[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ text: string; error: boolean } | null>(null);
  const submitting = useRef(false);
  const tenantPath = `/api/tenants/${encodeURIComponent(tenantId)}`;
  const storePath = `/lojas/${encodeURIComponent(tenantId)}`;

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
      const name = categories && value ? categoryPath(categories, value) : "";
      setMessage({ text: name ? `Categoria “${name}” salva.` : "Produto sem categoria.", error: false });
    } catch { setMessage({ text: "Não foi possível confirmar a categoria. Recarregue o produto.", error: true }); }
    finally { submitting.current = false; setBusy(false); }
  }

  return <section className="side-card" aria-labelledby="product-category-title">
    <h2 id="product-category-title">Categoria</h2>
    {failed ? <p role="alert" className="feedback feedback-error">Não foi possível consultar as categorias.</p> : null}
    {!categories && !failed ? <Loading context="categorias" className="py-4" /> : null}
    {categories ? categories.length === 0
      ? <p>A loja ainda não tem categorias. <Link href={`${storePath}/categorias`}>Criar categorias</Link></p>
      : <div className="form-field">
        <label htmlFor="product-category">Categoria do produto</label>
        <select id="product-category" value={product.categoryId ?? ""} disabled={busy} onChange={event => void choose(event.target.value)} aria-describedby="product-category-help">
          <option value="">Sem categoria</option>
          {categories.map(category => <option key={category.id} value={category.id}>{categoryPath(categories, category.id)}</option>)}
        </select>
        <p id="product-category-help" className="field-help">Salva ao escolher. Na vitrine, o comprador pode filtrar por categoria. <Link href={`${storePath}/categorias`}>Gerenciar categorias</Link></p>
      </div> : null}
    {message ? <p role={message.error ? "alert" : "status"} className={`feedback feedback-${message.error ? "error" : "success"}`}>{message.text}</p> : null}
  </section>;
}
