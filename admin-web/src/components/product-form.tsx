"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { apiRequest, requestError } from "@/lib/auth-api";
import { decimalInput, type Product } from "@/lib/catalog";

type Fields = { name: string; sku: string; price: string; cost: string };
type Errors = Partial<Record<keyof Fields, string>>;

export default function ProductForm({ tenantId, product, onSaved, onConflict }: {
  tenantId: string; product?: Product; onSaved: (product: Product) => void; onConflict?: () => void;
}) {
  const initial: Fields = { name: product?.name ?? "", sku: product?.sku ?? "", price: product?.price.replace(".", ",") ?? "", cost: product?.cost?.replace(".", ",") ?? "" };
  const [fields, setFields] = useState<Fields>(initial);
  const [errors, setErrors] = useState<Errors>({});
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [conflict, setConflict] = useState(false);
  const [baseline, setBaseline] = useState(JSON.stringify(initial));
  const submitting = useRef(false);
  const formRef = useRef<HTMLFormElement>(null);
  const feedbackRef = useRef<HTMLParagraphElement>(null);
  const pendingErrorFocus = useRef<keyof Fields | null>(null);
  const dirty = JSON.stringify(fields) !== baseline;

  useEffect(() => {
    if (!dirty) return;
    const leave = (event: BeforeUnloadEvent) => event.preventDefault();
    const navigate = (event: MouseEvent) => {
      const link = event.target instanceof Element ? event.target.closest("a[href]") : null;
      if (link && !event.ctrlKey && !event.metaKey && !event.shiftKey && event.button === 0
        && !window.confirm("Há alterações não salvas. Deseja sair sem salvar?")) event.preventDefault();
    };
    window.addEventListener("beforeunload", leave);
    document.addEventListener("click", navigate, true);
    return () => { window.removeEventListener("beforeunload", leave); document.removeEventListener("click", navigate, true); };
  }, [dirty]);

  function update(field: keyof Fields, value: string) {
    setFields(current => ({ ...current, [field]: value }));
    setErrors(current => ({ ...current, [field]: undefined }));
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current || conflict) return;
    const nextErrors: Errors = {};
    const name = fields.name.trim(); const sku = fields.sku.trim();
    const price = decimalInput(fields.price); const cost = fields.cost.trim() ? decimalInput(fields.cost) : null;
    if (!name || [...name].length > 120) nextErrors.name = "Informe um nome com até 120 caracteres.";
    if (!/^[A-Za-z0-9._/-]{1,64}$/.test(sku)) nextErrors.sku = "Use de 1 a 64 letras sem acentos, números, ponto, hífen, barra ou sublinhado.";
    if (!price || price === "0.00") nextErrors.price = "Informe um preço maior que zero, com até 17 dígitos antes da vírgula e 2 casas decimais.";
    if (fields.cost.trim() && !cost) nextErrors.cost = "Informe um custo a partir de zero, com até 17 dígitos antes da vírgula e 2 casas decimais, ou deixe em branco.";
    setErrors(nextErrors); setMessage("");
    const first = (Object.keys(nextErrors) as (keyof Fields)[])[0];
    if (first) { pendingErrorFocus.current = first; return; }
    submitting.current = true; setBusy(true);
    try {
      const response = await apiRequest(`/api/tenants/${encodeURIComponent(tenantId)}/products${product ? `/${encodeURIComponent(product.id)}` : ""}`, {
        method: product ? "PUT" : "POST", body: { name, sku, price, cost, ...(product ? { version: product.version } : {}) },
      });
      if (response.status === 409) {
        const body: { code?: string } = await response.json().catch(() => ({}));
        if (body.code === "SKU_CONFLICT") {
          setErrors({ sku: "Este SKU já está em uso nesta loja. Escolha outro identificador." });
          pendingErrorFocus.current = "sku";
        } else if (product) {
          setConflict(true); setMessage("Este produto foi alterado em outra sessão. Seus dados não foram sobrescritos. Carregue a versão atual antes de editar novamente.");
        } else setMessage("Não foi possível salvar: o SKU pode estar em uso. Confira o identificador e tente novamente.");
        return;
      }
      if (response.status === 401) { setMessage("Sua sessão expirou. Entre novamente antes de salvar o produto."); return; }
      if (response.status === 404 || response.status === 403) { setMessage("Produto ou loja indisponível para esta solicitação. Volte para suas lojas ou tente novamente."); return; }
      if (!response.ok) { setMessage(requestError(response.status)); return; }
      const saved: Product = await response.json();
      const savedFields = product ? { name: saved.name, sku: saved.sku, price: saved.price.replace(".", ","), cost: saved.cost?.replace(".", ",") ?? "" } : { name: "", sku: "", price: "", cost: "" };
      setBaseline(JSON.stringify(savedFields));
      setFields(savedFields);
      onSaved(saved);
    } catch { setMessage("Não foi possível confirmar o salvamento. Consulte a lista antes de tentar novamente para evitar um cadastro duplicado."); }
    finally { submitting.current = false; setBusy(false); }
  }

  useEffect(() => { if (message) feedbackRef.current?.focus(); }, [message]);
  useEffect(() => {
    if (busy || !pendingErrorFocus.current) return;
    const field = pendingErrorFocus.current;
    pendingErrorFocus.current = null;
    formRef.current?.querySelector<HTMLInputElement>(`[name="${field}"]`)?.focus();
  }, [busy, errors]);

  return <form ref={formRef} onSubmit={submit} noValidate aria-busy={busy}>
    {([['name', 'Nome do produto', 'Até 120 caracteres.'], ['sku', 'SKU', 'Identificador único nesta loja. Letras maiúsculas e minúsculas são diferentes.'], ['price', 'Preço de venda (R$)', 'Maior que zero. Exemplo: 49,90. Sem separador de milhares.'], ['cost', 'Custo (R$) — opcional', 'Zero é permitido. Deixe em branco se ainda não souber.']] as const).map(([field, label, hint]) => <div className="form-field" key={field}>
      <label htmlFor={`product-${field}`}>{label}</label>
      <input id={`product-${field}`} name={field} value={fields[field]} onChange={event => update(field, event.target.value)}
        type="text" inputMode={field === "price" || field === "cost" ? "decimal" : "text"} autoComplete="off"
        spellCheck={field === "name"} maxLength={field === "name" ? 240 : field === "sku" ? 64 : 20}
        required={field !== "cost"} disabled={busy || conflict} aria-invalid={!!errors[field]}
        aria-describedby={`product-${field}-help${errors[field] ? ` product-${field}-error` : ""}`} />
      <p id={`product-${field}-help`} className="field-help">{hint}</p>
      {errors[field] ? <p id={`product-${field}-error`} className="field-error">{errors[field]}</p> : null}
    </div>)}
    {message ? <p ref={feedbackRef} tabIndex={-1} role="alert" className="feedback feedback-error">{message}</p> : null}
    {conflict && onConflict ? <button type="button" onClick={() => { if (!dirty || window.confirm("Carregar a versão atual descartará suas alterações não salvas. Deseja continuar?")) onConflict(); }}>Carregar versão atual</button> : <button type="submit" disabled={busy}>{busy ? "Salvando produto…" : product ? "Salvar alterações" : "Cadastrar produto"}</button>}
  </form>;
}
