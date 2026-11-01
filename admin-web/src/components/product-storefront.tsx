"use client";

import Link from "next/link";
import { type FormEvent, useEffect, useRef, useState } from "react";
import { apiRequest } from "@/lib/auth-api";
import { type Product, formatBRL } from "@/lib/catalog";
import { type Brand, compareAtInput, parseBrands, storefrontError } from "@/lib/brands";
import { categorySlug, categorySlugError } from "@/lib/categories";
import { Loading } from "@/components/ui/loading";
import { useUnsavedChanges } from "@/lib/use-unsaved-changes";

// Sprint 8 (SC-041): how the product appears in the storefront besides name, price and photos:
// brand, address and the "compare at" price of each variant. Saved apart from the product form, so
// it never touches the product version or conflicts with unsaved edits there.
export default function ProductStorefront({ tenantId, product, onSaved }: {
  tenantId: string; product: Product; onSaved: (product: Product, notice?: string) => void;
}) {
  const [brands, setBrands] = useState<Brand[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [brandId, setBrandId] = useState(product.brandId ?? "");
  const [slug, setSlug] = useState(product.slug ?? "");
  const [featured, setFeatured] = useState(product.featured === true);
  const [compareAt, setCompareAt] = useState<Record<string, string>>(() => initialCompareAt(product));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ text: string; error: boolean } | null>(null);
  const submitting = useRef(false);
  const tenantPath = `/api/tenants/${encodeURIComponent(tenantId)}`;
  const storePath = `/lojas/${encodeURIComponent(tenantId)}`;
  useUnsavedChanges(brandId !== (product.brandId ?? "") || slug !== (product.slug ?? "") || featured !== (product.featured === true)
    || JSON.stringify(compareAt) !== JSON.stringify(initialCompareAt(product)));

  useEffect(() => {
    const controller = new AbortController();
    apiRequest(`${tenantPath}/brands`, { signal: controller.signal }).then(async response => {
      const parsed = response.ok ? parseBrands(await response.json()) : null;
      if (!parsed) throw new Error();
      if (!controller.signal.aborted) setBrands(parsed);
    }).catch(() => { if (!controller.signal.aborted) setFailed(true); });
    return () => controller.abort();
  }, [tenantPath]);

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current) return;
    const found: Record<string, string> = {};
    const slugProblem = slug.trim() ? categorySlugError(slug) : product.slug ? "Informe o endereço do produto." : null;
    if (slugProblem) found.slug = slugProblem;
    const variants = product.variants.map(variant => {
      const parsed = compareAtInput(compareAt[variant.id] ?? "", variant.price);
      if ("error" in parsed) found[variant.id] = parsed.error;
      return { id: variant.id, compareAtPrice: "value" in parsed ? parsed.value : null };
    });
    setErrors(found);
    const first = Object.keys(found)[0];
    if (first) { document.getElementById(first === "slug" ? "product-slug" : `compare-at-${first}`)?.focus(); return; }
    submitting.current = true; setBusy(true); setMessage(null);
    try {
      const response = await apiRequest(`${tenantPath}/products/${encodeURIComponent(product.id)}/storefront`, {
        method: "PUT", body: { brandId: brandId || null, slug: slug.trim() ? categorySlug(slug) : null, variants, featured },
      });
      if (!response.ok) {
        const body: { code?: string } = await response.json().catch(() => ({}));
        setMessage({ text: storefrontError(response.status, body.code), error: true }); return;
      }
      const saved: Product = await response.json();
      onSaved(saved);
      setSlug(saved.slug ?? ""); setCompareAt(initialCompareAt(saved)); setFeatured(saved.featured === true);
      setMessage({ text: "Dados da vitrine salvos.", error: false });
    } catch { setMessage({ text: "Não foi possível confirmar. Recarregue o produto.", error: true }); }
    finally { submitting.current = false; setBusy(false); }
  }

  return <section className="side-card" aria-labelledby="product-storefront-title">
    <h2 id="product-storefront-title">Vitrine</h2>
    <form onSubmit={event => void save(event)} noValidate className="category-edit">
      <div className="form-field">
        <label htmlFor="product-brand">Marca</label>
        {failed ? <p role="alert" className="feedback feedback-error">Não foi possível consultar as marcas.</p> : null}
        {!brands && !failed ? <Loading context="marcas" className="py-4" /> : null}
        {brands ? <select id="product-brand" value={brandId} disabled={busy} onChange={event => setBrandId(event.target.value)} aria-describedby="product-brand-help">
          <option value="">Sem marca</option>
          {brands.map(brand => <option key={brand.id} value={brand.id}>{brand.name}</option>)}
        </select> : null}
        <p id="product-brand-help" className="field-help"><Link href={`${storePath}/marcas`}>Gerenciar marcas</Link></p>
      </div>
      <div className="form-field">
        <label htmlFor="product-slug">Endereço na vitrine</label>
        <p id="product-slug-help" className="field-help">Aparece no link do produto. Ao mudar, o link antigo continua levando ao produto.</p>
        <input id="product-slug" value={slug} maxLength={80} autoComplete="off" spellCheck={false} disabled={busy}
          onChange={event => { setSlug(event.target.value); setErrors(current => ({ ...current, slug: "" })); }}
          aria-invalid={!!errors.slug} aria-describedby={`product-slug-help${errors.slug ? " product-slug-error" : ""}`} />
        {errors.slug ? <p id="product-slug-error" className="field-error">{errors.slug}</p> : null}
      </div>
      <div className="form-field">
        <label className="check-field">
          <input type="checkbox" checked={featured} disabled={busy} onChange={event => setFeatured(event.target.checked)} aria-describedby="product-featured-help" />
          Destaque na vitrine
        </label>
        <p id="product-featured-help" className="field-help">Aparece na seção de destaques da página inicial, se a loja tiver essa seção em <Link href={`${storePath}/conteudo`}>Aparência e conteúdo</Link>.</p>
      </div>
      {product.variants.map(variant => {
        const label = product.kind === "SIMPLE" || !variant.options.length ? "Preço “de” (opcional)" : `Preço “de” · ${variant.options.map(option => option.value).join(" / ")}`;
        return <div className="form-field" key={variant.id}>
          <label htmlFor={`compare-at-${variant.id}`}>{label}</label>
          <p id={`compare-at-${variant.id}-help`} className="field-help">Preço atual: {formatBRL(variant.price)}. Aparece riscado na vitrine; o valor cobrado é sempre o preço atual.</p>
          <input id={`compare-at-${variant.id}`} inputMode="decimal" value={compareAt[variant.id] ?? ""} autoComplete="off" disabled={busy}
            onChange={event => { const value = event.target.value; setCompareAt(current => ({ ...current, [variant.id]: value })); setErrors(current => ({ ...current, [variant.id]: "" })); }}
            aria-invalid={!!errors[variant.id]} aria-describedby={`compare-at-${variant.id}-help${errors[variant.id] ? ` compare-at-${variant.id}-error` : ""}`} />
          {errors[variant.id] ? <p id={`compare-at-${variant.id}-error`} className="field-error">{errors[variant.id]}</p> : null}
        </div>;
      })}
      <button type="submit" disabled={busy}>{busy ? "Salvando…" : "Salvar dados da vitrine"}</button>
    </form>
    {message ? <p role={message.error ? "alert" : "status"} className={`feedback feedback-${message.error ? "error" : "success"}`}>{message.text}</p> : null}
  </section>;
}

function initialCompareAt(product: Product): Record<string, string> {
  return Object.fromEntries(product.variants.map(variant => [variant.id, variant.compareAtPrice ? variant.compareAtPrice.replace(".", ",") : ""]));
}
