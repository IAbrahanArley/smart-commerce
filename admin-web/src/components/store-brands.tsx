"use client";

import Link from "next/link";
import { type FormEvent, useEffect, useRef, useState } from "react";
import { apiRequest } from "@/lib/auth-api";
import { type Brand, MAX_BRANDS, brandError, parseBrands } from "@/lib/brands";
import { categoryName, categoryNameError, productCountText } from "@/lib/categories";
import PageHeader from "@/components/page-header";
import PageState from "@/components/page-state";
import { useConfirm } from "@/components/confirm-dialog";
import { useUnsavedChanges } from "@/lib/use-unsaved-changes";
import { Loading } from "@/components/ui/loading";

// Sprint 8 (SC-041): brands of the store, chosen per product in its "Vitrine" card. Removing a
// brand keeps its products, without a brand. Names follow the category rule.
export default function StoreBrands({ tenantId }: { tenantId: string }) {
  const [list, setList] = useState<Brand[] | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "signed-out" | "missing" | "error">("loading");
  const [attempt, setAttempt] = useState(0);
  const [name, setName] = useState("");
  const [nameError, setNameError] = useState<string | null>(null);
  const [editing, setEditing] = useState<{ id: string; name: string } | null>(null);
  const [editError, setEditError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{ text: string; error: boolean } | null>(null);
  useUnsavedChanges(!!name.trim() || (!!editing && editing.name !== list?.find(brand => brand.id === editing.id)?.name));
  const submitting = useRef(false);
  const feedbackRef = useRef<HTMLParagraphElement>(null);
  const editRef = useRef<HTMLInputElement>(null);
  const { confirm, dialog } = useConfirm();
  const api = `/api/tenants/${encodeURIComponent(tenantId)}/brands`;
  const storePath = `/lojas/${encodeURIComponent(tenantId)}`;

  useEffect(() => {
    const controller = new AbortController();
    apiRequest(api, { signal: controller.signal }).then(async response => {
      if (response.status === 401) { setState("signed-out"); return; }
      if (response.status === 403 || response.status === 404) { setState("missing"); return; }
      if (!response.ok) throw new Error();
      const parsed = parseBrands(await response.json());
      if (!parsed) throw new Error();
      if (controller.signal.aborted) return;
      setList(parsed); setState("ready");
    }).catch(() => { if (!controller.signal.aborted) setState("error"); });
    return () => controller.abort();
  }, [api, attempt]);

  useEffect(() => { if (feedback) feedbackRef.current?.focus(); }, [feedback]);
  useEffect(() => { if (editing) editRef.current?.focus(); }, [editing?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  async function mutate(request: () => Promise<Response>, success: string): Promise<boolean> {
    if (submitting.current) return false;
    submitting.current = true; setBusy(true); setFeedback(null);
    try {
      const response = await request();
      if (!response.ok) {
        const body: { code?: string } = await response.json().catch(() => ({}));
        setFeedback({ text: brandError(response.status, body.code), error: true });
        return false;
      }
      const parsed = parseBrands(await response.json());
      if (parsed) setList(parsed);
      setFeedback({ text: success, error: false });
      return true;
    } catch {
      setFeedback({ text: "Não foi possível confirmar a alteração. Recarregue a lista antes de tentar de novo.", error: true });
      return false;
    } finally { submitting.current = false; setBusy(false); }
  }

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const problem = categoryNameError(name);
    setNameError(problem);
    if (problem) { document.getElementById("brand-name")?.focus(); return; }
    const value = categoryName(name);
    if (await mutate(() => apiRequest(api, { method: "POST", body: { name: value } }), `Marca “${value}” criada.`)) setName("");
  }

  async function rename(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!editing) return;
    const problem = categoryNameError(editing.name);
    setEditError(problem);
    if (problem) { editRef.current?.focus(); return; }
    if (await mutate(() => apiRequest(`${api}/${encodeURIComponent(editing.id)}`, { method: "PUT", body: { name: categoryName(editing.name) } }), "Marca renomeada.")) setEditing(null);
  }

  async function remove(brand: Brand) {
    if (!(await confirm({
      title: `Remover a marca “${brand.name}”?`,
      message: brand.products === 0 ? "Nenhum produto usa esta marca."
        : `${productCountText(brand.products)} ${brand.products === 1 ? "continuará" : "continuarão"} no catálogo, sem marca.`,
      confirmLabel: "Remover marca", tone: "danger",
    }))) return;
    void mutate(() => apiRequest(`${api}/${encodeURIComponent(brand.id)}`, { method: "DELETE" }), "Marca removida.");
  }

  const full = (list?.length ?? 0) >= MAX_BRANDS;
  return <section className="page store-categories" aria-labelledby="brands-title" aria-busy={state === "loading" || busy}>{dialog}
    <PageHeader id="brands-title" title="Marcas" breadcrumbs={[{ href: `${storePath}/produtos`, label: "Produtos" }]}
      description="Cadastre as marcas que a loja vende. O comprador poderá filtrar por marca na vitrine. Escolha a marca de cada produto no cartão “Vitrine” da edição do produto." />
    {state === "loading" ? <Loading context="marcas" /> : null}
    {state === "signed-out" ? <><p role="status">Sua sessão expirou. Entre novamente para gerenciar as marcas.</p><Link href="/entrar">Entrar</Link></> : null}
    {state === "missing" ? <PageState icon="store" title="Loja indisponível" href="/lojas" actionLabel="Voltar para minhas lojas">Escolha uma loja à qual você tem acesso.</PageState> : null}
    {state === "error" ? <PageState kind="error" icon="box" title="Não foi possível consultar as marcas" action={() => { setState("loading"); setAttempt(current => current + 1); }} actionLabel="Consultar novamente">Confira a conexão e tente novamente.</PageState> : null}
    {feedback ? <p ref={feedbackRef} tabIndex={-1} role={feedback.error ? "alert" : "status"} className={`feedback feedback-${feedback.error ? "error" : "success"}`}>{feedback.text}</p> : null}
    {state === "ready" && list ? <>
      {list.length === 0 ? <PageState icon="box" title="Nenhuma marca">Crie a primeira marca abaixo.</PageState> : <ul className="category-list">
        {list.map(brand => <li key={brand.id} className="category-item">
          {editing?.id === brand.id ? <form onSubmit={event => void rename(event)} noValidate className="category-edit">
            <label htmlFor={`brand-edit-${brand.id}`} className="visually-hidden">Novo nome de “{brand.name}”</label>
            <input ref={editRef} id={`brand-edit-${brand.id}`} value={editing.name} maxLength={120} autoComplete="off" disabled={busy}
              onChange={event => { setEditing({ id: brand.id, name: event.target.value }); setEditError(null); }}
              aria-invalid={!!editError} aria-describedby={editError ? `brand-edit-${brand.id}-error` : undefined} />
            {editError ? <p id={`brand-edit-${brand.id}-error`} className="field-error">{editError}</p> : null}
            <div className="form-actions">
              <button type="submit" disabled={busy}>Salvar nome</button>
              <button type="button" className="button-secondary" disabled={busy} onClick={() => { setEditing(null); setEditError(null); }}>Cancelar</button>
            </div>
          </form> : <>
            <span className="category-name">{brand.name}<span className="cell-hint">{productCountText(brand.products)} · /{brand.slug}</span></span>
            <span className="form-actions">
              <button type="button" className="button-secondary" disabled={busy} aria-label={`Renomear ${brand.name}`} onClick={() => { setEditing({ id: brand.id, name: brand.name }); setEditError(null); }}>Renomear</button>
              <button type="button" className="button-secondary" disabled={busy} aria-label={`Remover ${brand.name}`} onClick={() => void remove(brand)}>Remover</button>
            </span>
          </>}
        </li>)}
      </ul>}
      <form className="side-card" onSubmit={event => void create(event)} noValidate aria-labelledby="brand-create-title">
        <h2 id="brand-create-title">Nova marca</h2>
        <div className="form-field">
          <label htmlFor="brand-name">Nome</label>
          <p id="brand-name-help" className="field-help">Até 60 caracteres. Até {MAX_BRANDS} marcas por loja.</p>
          <input id="brand-name" value={name} maxLength={120} autoComplete="off" disabled={busy || full}
            onChange={event => { setName(event.target.value); setNameError(null); }}
            aria-invalid={!!nameError} aria-describedby={`brand-name-help${nameError ? " brand-name-error" : ""}`} />
          {nameError ? <p id="brand-name-error" className="field-error">{nameError}</p> : null}
        </div>
        <button type="submit" disabled={busy || full}>{busy ? "Salvando…" : "Criar marca"}</button>
        {full ? <p className="field-help">A loja atingiu o limite de marcas.</p> : null}
      </form>
    </> : null}
  </section>;
}
