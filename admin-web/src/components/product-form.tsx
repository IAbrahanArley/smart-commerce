"use client";

import { useEffect, useRef, useState, type FormEvent, type ReactNode } from "react";
import { apiRequest, requestError } from "@/lib/auth-api";
import { decimalInput, type Product, type VariantDraft } from "@/lib/catalog";
import DescriptionEditor from "@/components/description-editor";
import VariantBuilder from "@/components/variant-builder";
import StatusBadge from "@/components/status-badge";
import { useConfirm } from "@/components/confirm-dialog";

type Fields = { name: string; sku: string; price: string; cost: string; descriptionHtml: string };
type Errors = Partial<Record<keyof Fields | "variants", string>>;

export default function ProductForm({ tenantId, product, storePublished = false, onSaved, onConflict, onDirtyChange, aside }: {
  tenantId: string; product?: Product; storePublished?: boolean; onSaved: (product: Product, notice?: string) => void; onConflict?: () => void; onDirtyChange?: (dirty: boolean) => void; aside?: ReactNode;
}) {
  const initial: Fields = { name: product?.name ?? "", sku: product?.sku ?? "", price: product?.price.replace(".", ",") ?? "", cost: product?.cost?.replace(".", ",") ?? "", descriptionHtml: product?.descriptionHtml ?? "" };
  const [fields, setFields] = useState<Fields>(initial);
  const [errors, setErrors] = useState<Errors>({});
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [conflict, setConflict] = useState(false);
  const [baseline, setBaseline] = useState(JSON.stringify(initial));
  const submitting = useRef(false);
  const formRef = useRef<HTMLFormElement>(null);
  const feedbackRef = useRef<HTMLParagraphElement>(null);
  const pendingErrorFocus = useRef<keyof Fields | "variants" | null>(null);
  const [kind, setKind] = useState<"SIMPLE" | "VARIABLE">(product?.kind ?? "SIMPLE");
  const [variants, setVariants] = useState<VariantDraft[]>([]);
  const { confirm, dialog } = useConfirm();
  const dirty = JSON.stringify(fields) !== baseline || (!product && (kind !== "SIMPLE" || variants.length > 0));
  const savedDescription = product?.descriptionHtml ?? "";
  const safeSavedDescription = [...savedDescription].length <= 20000 && new TextEncoder().encode(savedDescription).length <= 80000
    && !savedDescription.replace(/<\/?(?:p|strong|em|ul|ol|li)>|<br\s*\/?>/g, "").includes("<");
  useEffect(() => { onDirtyChange?.(dirty || busy); }, [dirty, busy, onDirtyChange]);

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

  async function publish() {
    if (!product || submitting.current || conflict || dirty) return;
    const published = !product.published;
    if (!(await confirm(published
      ? { title: "Publicar este produto?", message: "Quando a loja estiver publicada, qualquer pessoa com o link poderá consultar seu nome, preço e disponibilidade. SKU e custo não serão exibidos.", confirmLabel: "Publicar produto" }
      : { title: "Ocultar este produto?", message: "Ele deixará de aparecer na vitrine pública.", confirmLabel: "Ocultar produto" }))) return;
    submitting.current = true; setBusy(true); setMessage("");
    try {
      const response = await apiRequest(`/api/tenants/${encodeURIComponent(tenantId)}/products/${encodeURIComponent(product.id)}/publication`, { method: "PUT", body: { published, version: product.version } });
      if (response.status === 409) { setConflict(true); setMessage("O produto foi alterado em outra sessão. Carregue a versão atual antes de alterar a publicação. Nenhum dado foi sobrescrito."); return; }
      if (response.status === 401) { setMessage("Sua sessão expirou. Entre novamente antes de alterar a publicação."); return; }
      if (response.status === 403 || response.status === 404) { setMessage("Produto ou loja indisponível para esta solicitação."); return; }
      if (!response.ok) { setMessage(requestError(response.status)); return; }
      onSaved(await response.json(), published ? storePublished ? "Produto publicado na vitrine." : "Produto selecionado para a vitrine. Ele ficará visível quando você publicar a loja." : "Produto oculto na vitrine.");
    } catch { setConflict(true); setMessage("Não foi possível confirmar a publicação. Carregue a versão atual para consultar o resultado antes de tentar novamente."); }
    finally { submitting.current = false; setBusy(false); }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current || conflict) return;
    const nextErrors: Errors = {};
    const name = fields.name.trim(); const sku = fields.sku.trim();
    const price = decimalInput(fields.price); const cost = fields.cost.trim() ? decimalInput(fields.cost) : null;
    if (!name || [...name].length > 120) nextErrors.name = "Informe um nome com até 120 caracteres.";
    if ([...fields.descriptionHtml].length > 20000 || new TextEncoder().encode(fields.descriptionHtml).length > 80000) nextErrors.descriptionHtml = "Use até 20.000 caracteres na descrição.";
    if (kind === "SIMPLE") {
      if (!/^[A-Za-z0-9._/-]{1,64}$/.test(sku)) nextErrors.sku = "Use de 1 a 64 letras sem acentos, números, ponto, hífen, barra ou sublinhado.";
      if (!price || price === "0.00") nextErrors.price = "Informe um preço maior que zero, com até 17 dígitos antes da vírgula e 2 casas decimais.";
      if (fields.cost.trim() && !cost) nextErrors.cost = "Informe um custo a partir de zero, com até 17 dígitos antes da vírgula e 2 casas decimais, ou deixe em branco.";
    }
    const variantBody = variants.map(variant => ({ sku: variant.sku.trim(), price: decimalInput(variant.price), cost: variant.cost.trim() ? decimalInput(variant.cost) : null, options: variant.options }));
    if (!product && kind === "VARIABLE" && (!variantBody.length || variantBody.length > 50 || variantBody.some(variant => !/^[A-Za-z0-9._/-]{1,64}$/.test(variant.sku) || !variant.price || variant.price === "0.00" || (variant.cost === null && variants[variantBody.indexOf(variant)].cost.trim())) || new Set(variantBody.map(variant => variant.sku)).size !== variantBody.length)) nextErrors.variants = "Gere até 50 combinações e preencha cada SKU distinto e preço maior que zero. Custos são opcionais, a partir de zero, com até duas casas decimais.";
    setErrors(nextErrors); setMessage("");
    const first = (Object.keys(nextErrors) as (keyof Errors)[])[0];
    if (first) { pendingErrorFocus.current = first; return; }
    submitting.current = true; setBusy(true);
    try {
      const response = await apiRequest(`/api/tenants/${encodeURIComponent(tenantId)}/products${product ? `/${encodeURIComponent(product.id)}` : ""}`, {
        method: product ? "PUT" : "POST", body: { name, descriptionHtml: fields.descriptionHtml, kind, ...(kind === "SIMPLE" ? { sku, price, cost } : product ? {} : { variants: variantBody }), ...(product ? { version: product.version } : {}) },
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
      const savedFields = product ? { name: saved.name, sku: saved.sku, price: saved.price.replace(".", ","), cost: saved.cost?.replace(".", ",") ?? "", descriptionHtml: saved.descriptionHtml } : { name: "", sku: "", price: "", cost: "", descriptionHtml: "" };
      setBaseline(JSON.stringify(savedFields));
      setFields(savedFields);
      if (!product) { setKind("SIMPLE"); setVariants([]); }
      onSaved(saved);
    } catch { setMessage("Não foi possível confirmar o salvamento. Consulte a lista antes de tentar novamente para evitar um cadastro duplicado."); }
    finally { submitting.current = false; setBusy(false); }
  }

  useEffect(() => { if (message) feedbackRef.current?.focus(); }, [message]);
  useEffect(() => {
    if (busy || !pendingErrorFocus.current) return;
    const field = pendingErrorFocus.current;
    pendingErrorFocus.current = null;
    formRef.current?.querySelector<HTMLInputElement | HTMLTextAreaElement>(field === "variants" ? '[name="variant-0-sku"]' : `[name="${field}"]`)?.focus();
  }, [busy, errors]);

  const form = <form ref={formRef} className="workspace-main" onSubmit={(event) => void submit(event)} noValidate aria-busy={busy}>
    <h2>Identificação</h2>
    {!product ? <div className="form-field"><label htmlFor="product-kind">Tipo de produto</label><select id="product-kind" value={kind} disabled={busy} onChange={event => { const next = event.target.value as "SIMPLE" | "VARIABLE"; void (async () => { if (!variants.length || await confirm({ title: "Mudar o tipo de produto?", message: "As combinações ainda não salvas serão descartadas.", confirmLabel: "Mudar tipo", tone: "danger" })) { setKind(next); setVariants([]); } })(); }}><option value="SIMPLE">Simples — um SKU e um estoque</option><option value="VARIABLE">Com variações — SKU e estoque por combinação</option></select><p className="field-help">O tipo não pode ser alterado depois do cadastro.</p></div> : <p className="field-help">{kind === "VARIABLE" ? "Produto com variações. Preços e estoque são gerenciados por combinação." : "Produto simples."}</p>}
    {([['name', 'Nome do produto', 'Até 120 caracteres.'], ['sku', 'SKU', 'Identificador único nesta loja. Letras maiúsculas e minúsculas são diferentes.'], ['price', 'Preço de venda (R$)', 'Maior que zero. Exemplo: 49,90. Sem separador de milhares.'], ['cost', 'Custo (R$) — opcional', 'Zero é permitido. Deixe em branco se ainda não souber.']] as const).filter(([field]) => field === "name" || kind === "SIMPLE").map(([field, label, hint]) => <div className="form-field" key={field}>
      <label htmlFor={`product-${field}`}>{label}</label>
      <input id={`product-${field}`} name={field} value={fields[field]} onChange={event => update(field, event.target.value)}
        type="text" inputMode={field === "price" || field === "cost" ? "decimal" : "text"} autoComplete="off"
        spellCheck={field === "name"} maxLength={field === "name" ? 240 : field === "sku" ? 64 : 20}
        required={field !== "cost"} disabled={busy || conflict} aria-invalid={!!errors[field]}
        aria-describedby={`product-${field}-help${errors[field] ? ` product-${field}-error` : ""}`} />
      <p id={`product-${field}-help`} className="field-help">{hint}</p>
      {errors[field] ? <p id={`product-${field}-error`} className="field-error">{errors[field]}</p> : null}
    </div>)}
    <section className="product-section" aria-labelledby="description-title"><h2 id="description-title">Descrição</h2><DescriptionEditor value={fields.descriptionHtml} onChange={value => update("descriptionHtml", value)} disabled={busy || conflict} error={errors.descriptionHtml} />
      {product ? <div className="saved-description"><h3>Prévia da descrição salva</h3>{fields.descriptionHtml !== savedDescription ? <p className="field-help">Esta prévia mostra a última versão salva. Salve as alterações para atualizá-la.</p> : null}
        {safeSavedDescription && savedDescription ? <div dangerouslySetInnerHTML={{ __html: savedDescription }} /> : <p>{savedDescription ? "Não foi possível exibir a prévia com segurança." : "Nenhuma descrição salva."}</p>}
      </div> : null}
    </section>
    {!product && kind === "VARIABLE" ? <section className="product-section" aria-labelledby="variant-create-title"><h2 id="variant-create-title">Variações e preços</h2><VariantBuilder variants={variants} onChange={setVariants} disabled={busy || conflict} />{errors.variants ? <p role="alert" className="field-error">{errors.variants}</p> : null}</section> : null}
    {!product ? <p className="field-help">Após cadastrar, você seguirá para a edição do produto para incluir fotos e movimentar o estoque de cada SKU.</p> : null}
    {message ? <p ref={feedbackRef} tabIndex={-1} role="alert" className="feedback feedback-error">{message}</p> : null}
    {conflict && onConflict ? <button type="button" onClick={() => void (async () => { if (!dirty || await confirm({ title: "Carregar a versão atual?", message: "Suas alterações não salvas serão descartadas.", confirmLabel: "Descartar e carregar", tone: "danger" })) onConflict(); })()}>Carregar versão atual</button> : <button type="submit" disabled={busy}>{busy ? "Salvando produto…" : product ? "Salvar alterações" : "Cadastrar produto"}</button>}
  </form>;
  return product || aside ? <>{form}{dialog}<aside className="workspace-side" aria-label="Publicação e estoque">
    {product ? <section className="side-card" aria-labelledby="product-publication-title"><h2 id="product-publication-title">Publicação do produto</h2>
      <p>{product.published ? storePublished ? <StatusBadge tone="positive">Visível na vitrine</StatusBadge> : <StatusBadge tone="neutral">Selecionado · Loja oculta</StatusBadge> : <StatusBadge tone="neutral">Oculto na vitrine</StatusBadge>}</p>
      <p className="field-help">A vitrine permite somente consulta. Nome, preço e disponibilidade são públicos quando produto e loja estão publicados.</p>
      {dirty ? <p className="field-help">Salve as alterações do produto antes de mudar sua publicação.</p> : null}
      <button type="button" disabled={busy || conflict || dirty} onClick={() => void publish()}>{busy ? "Aguarde a atualização…" : product.published ? "Ocultar produto" : "Publicar produto"}</button>
    </section> : null}
    {aside}
  </aside></> : <>{form}{dialog}</>;
}
