"use client";

import Link from "next/link";
import { useRef, useState, useEffect, type FormEvent } from "react";
import { useConfirm } from "@/components/confirm-dialog";
import { useUnsavedChanges } from "@/lib/use-unsaved-changes";
import { apiRequest } from "@/lib/auth-api";
import { decimalInput, type Product, type ProductVariant } from "@/lib/catalog";
import { variantLabel } from "@/components/variant-builder";

function VariantForm({ tenantId, product, variant, locked, onSaved, onReload }: {
  tenantId: string; product: Product; variant?: ProductVariant; locked: boolean; onSaved: (product: Product, message?: string) => void; onReload: () => void;
}) {
  const [sku, setSku] = useState(variant?.sku ?? "");
  const [price, setPrice] = useState((variant?.price ?? product.price).replace(".", ","));
  const [cost, setCost] = useState(variant?.cost?.replace(".", ",") ?? "");
  const [values, setValues] = useState((product.variants[0]?.options ?? []).map(() => ""));
  const [busy, setBusy] = useState(false);
  const [stale, setStale] = useState(false);
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const submitting = useRef(false);
  const feedback = useRef<HTMLParagraphElement>(null);
  const skuRef = useRef<HTMLInputElement>(null);
  const { confirm, dialog } = useConfirm();
  const id = variant?.id ?? "new";
  const [baseline] = useState(JSON.stringify({ sku: variant?.sku ?? "", price: (variant?.price ?? product.price).replace(".", ","), cost: variant?.cost?.replace(".", ",") ?? "", values: (product.variants[0]?.options ?? []).map(() => "") }));
  const dirty = JSON.stringify({ sku, price, cost, values }) !== baseline;
  useEffect(() => { if (error) feedback.current?.focus(); }, [error]);
  useUnsavedChanges(dirty, "Há alterações não salvas nesta combinação. Deseja sair sem salvar?");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current || locked || stale) return;
    const amount = decimalInput(price); const unitCost = cost.trim() ? decimalInput(cost) : null;
    // Each problem next to its field; focus goes to the first one in the form order.
    const found: Record<string, string> = {};
    if (!variant) values.forEach((value, index) => {
      if (!value.trim() || [...value.trim()].length > 40 || /[\p{Cc}\p{Cf}\p{Cs}\p{Zl}\p{Zp}]/u.test(value)) found[`option-${index}`] = "Informe o valor da opção, com até 40 caracteres.";
    });
    if (!/^[A-Za-z0-9._/-]{1,64}$/.test(sku.trim())) found.sku = "Use de 1 a 64 letras sem acentos, números, ponto, hífen, barra ou sublinhado.";
    if (!amount || amount === "0.00") found.price = "Informe um preço maior que zero, com até duas casas decimais.";
    if (cost.trim() && !unitCost) found.cost = "Informe o custo com até duas casas decimais ou deixe em branco.";
    setFieldErrors(found);
    const first = Object.keys(found)[0];
    if (first) { setError(""); document.getElementById(`variant-${id}-${first}`)?.focus(); return; }
    submitting.current = true; setBusy(true); setError("");
    try {
      const response = await apiRequest(`/api/tenants/${encodeURIComponent(tenantId)}/products/${encodeURIComponent(product.id)}/variants${variant ? `/${encodeURIComponent(variant.id)}` : ""}`, {
        method: variant ? "PUT" : "POST", body: { sku: sku.trim(), price: amount, cost: unitCost, version: variant?.version ?? product.version,
          ...(!variant ? { options: product.variants[0].options.map((option, index) => ({ name: option.name, value: values[index].trim().normalize("NFC") })) } : {}) },
      });
      if (response.status === 409) {
        const body: { code?: string } = await response.json().catch(() => ({}));
        if (body.code === "VERSION_CONFLICT") setStale(true);
        setError(body.code === "SKU_CONFLICT" ? "Este SKU já está em uso. Escolha outro identificador." : "A combinação já existe ou foi alterada em outra sessão. Confira os valores ou carregue a versão atual."); return;
      }
      if (!response.ok) { setError("Não foi possível salvar a combinação. Confira os dados e sua sessão antes de tentar novamente."); return; }
      const saved: Product = await response.json();
      if (!variant) { setSku(""); setValues(values.map(() => "")); }
      onSaved(saved, variant ? "Combinação atualizada." : "Nova combinação cadastrada. O estoque começa em zero.");
    } catch { setStale(true); setError("Não foi possível confirmar o salvamento. Carregue a versão atual antes de repetir."); }
    finally { submitting.current = false; setBusy(false); }
  }
  const invalid = (key: string) => fieldErrors[key] ? { "aria-invalid": true, "aria-describedby": `variant-${id}-${key}-error` } : {};
  const fieldError = (key: string) => fieldErrors[key] ? <p id={`variant-${id}-${key}-error`} className="field-error">{fieldErrors[key]}</p> : null;
  const clear = (key: string) => { if (fieldErrors[key]) setFieldErrors(current => ({ ...current, [key]: "" })); };
  return <>{dialog}<form onSubmit={(event) => void submit(event)} className="variant-definition" aria-busy={busy} noValidate>
    <h3>{variant ? variantLabel(variant.options) : "Adicionar combinação"}</h3>
    {!variant ? product.variants[0]?.options.map((option, index) => <div className="form-field" key={option.name}><label htmlFor={`variant-${id}-option-${index}`}>{option.name}</label><input id={`variant-${id}-option-${index}`} value={values[index]} autoComplete="off" maxLength={80} required disabled={busy || locked || stale} {...invalid(`option-${index}`)} onChange={event => { setValues(current => current.map((value, position) => position === index ? event.target.value : value)); clear(`option-${index}`); }} />{fieldError(`option-${index}`)}</div>) : null}
    <div className="form-field"><label htmlFor={`variant-${id}-sku`}>SKU da combinação</label><input ref={skuRef} id={`variant-${id}-sku`} value={sku} autoComplete="off" spellCheck={false} maxLength={64} required disabled={busy || locked || stale} {...invalid("sku")} onChange={event => { setSku(event.target.value); clear("sku"); }} />{fieldError("sku")}</div>
    <div className="form-field"><label htmlFor={`variant-${id}-price`}>Preço (R$)</label><input id={`variant-${id}-price`} value={price} inputMode="decimal" autoComplete="off" maxLength={20} required disabled={busy || locked || stale} {...invalid("price")} onChange={event => { setPrice(event.target.value); clear("price"); }} />{fieldError("price")}</div>
    <div className="form-field"><label htmlFor={`variant-${id}-cost`}>Custo (R$) — opcional</label><input id={`variant-${id}-cost`} value={cost} inputMode="decimal" autoComplete="off" maxLength={20} disabled={busy || locked || stale} {...invalid("cost")} onChange={event => { setCost(event.target.value); clear("cost"); }} />{fieldError("cost")}</div>
    {error ? <p ref={feedback} role="alert" tabIndex={-1} className="feedback feedback-error">{error}</p> : null}
    {stale ? <button type="button" disabled={busy || locked} onClick={() => void (async () => { if (await confirm({ title: "Carregar a versão atual?", message: "Alterações não salvas nas combinações serão descartadas.", confirmLabel: "Descartar e carregar", tone: "danger" })) onReload(); })()}>Carregar versão atual</button> : <button type="submit" disabled={busy || locked}>{busy ? "Salvando combinação…" : variant ? "Salvar combinação" : "Adicionar combinação"}</button>}
    {variant ? <p><Link className="back-link" href={`/lojas/${encodeURIComponent(tenantId)}/produtos/${encodeURIComponent(product.id)}/estoque?variantId=${encodeURIComponent(variant.id)}`}>Consultar e movimentar estoque de {variantLabel(variant.options)}</Link></p> : null}
  </form></>;
}

export default function ProductVariants(props: { tenantId: string; product: Product; locked: boolean; onSaved: (product: Product, message?: string) => void; onReload: () => void }) {
  return <section className="product-section" aria-labelledby="variants-title"><h2 id="variants-title">Preços e estoque por combinação</h2>
    <p>As opções de cada combinação são fixas. Você pode atualizar SKU, preço e custo ou adicionar novas combinações, até o limite de 50.</p>
    {props.locked ? <p className="notice">Salve a identificação e a descrição antes de alterar as combinações.</p> : null}
    {props.product.variants.map(variant => <VariantForm key={`${variant.id}-${variant.version}`} {...props} variant={variant} />)}
    {props.product.variants.length < 50 ? <VariantForm key={`new-${props.product.variants.length}`} {...props} /> : <p>Limite de 50 combinações atingido.</p>}
  </section>;
}
