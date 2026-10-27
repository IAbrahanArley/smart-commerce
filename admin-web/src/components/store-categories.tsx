"use client";

import Link from "next/link";
import { type FormEvent, useEffect, useRef, useState } from "react";
import { apiRequest } from "@/lib/auth-api";
import { type Category, MAX_CATEGORIES, categoryError, categoryName, categoryNameError, parseCategories, productCountText } from "@/lib/categories";
import PageHeader from "@/components/page-header";
import PageState from "@/components/page-state";
import { useConfirm } from "@/components/confirm-dialog";
import { Loading } from "@/components/ui/loading";

// MVP block 1c: flat categories of the store. Removing one keeps its products, without a category.
export default function StoreCategories({ tenantId }: { tenantId: string }) {
  const [list, setList] = useState<Category[] | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "signed-out" | "missing" | "error">("loading");
  const [attempt, setAttempt] = useState(0);
  const [name, setName] = useState("");
  const [nameError, setNameError] = useState<string | null>(null);
  const [editing, setEditing] = useState<{ id: string; name: string } | null>(null);
  const [editError, setEditError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{ text: string; error: boolean } | null>(null);
  const submitting = useRef(false);
  const feedbackRef = useRef<HTMLParagraphElement>(null);
  const editRef = useRef<HTMLInputElement>(null);
  const { confirm, dialog } = useConfirm();
  const api = `/api/tenants/${encodeURIComponent(tenantId)}/categories`;
  const storePath = `/lojas/${encodeURIComponent(tenantId)}`;

  useEffect(() => {
    const controller = new AbortController();
    apiRequest(api, { signal: controller.signal }).then(async response => {
      if (response.status === 401) { setState("signed-out"); return; }
      if (response.status === 403 || response.status === 404) { setState("missing"); return; }
      if (!response.ok) throw new Error();
      const parsed = parseCategories(await response.json());
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
        setFeedback({ text: categoryError(response.status, body.code), error: true });
        return false;
      }
      const parsed = parseCategories(await response.json());
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
    if (problem) { document.getElementById("category-name")?.focus(); return; }
    const value = categoryName(name);
    if (await mutate(() => apiRequest(api, { method: "POST", body: { name: value } }), `Categoria “${value}” criada.`)) setName("");
  }

  async function rename(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!editing) return;
    const problem = categoryNameError(editing.name);
    setEditError(problem);
    if (problem) { editRef.current?.focus(); return; }
    if (await mutate(() => apiRequest(`${api}/${encodeURIComponent(editing.id)}`, { method: "PUT", body: { name: categoryName(editing.name) } }), "Categoria renomeada.")) setEditing(null);
  }

  async function remove(category: Category) {
    if (!(await confirm({
      title: `Remover a categoria “${category.name}”?`,
      message: category.products === 0 ? "Nenhum produto usa esta categoria."
        : `${productCountText(category.products)} ${category.products === 1 ? "continuará" : "continuarão"} no catálogo, sem categoria.`,
      confirmLabel: "Remover categoria", tone: "danger",
    }))) return;
    void mutate(() => apiRequest(`${api}/${encodeURIComponent(category.id)}`, { method: "DELETE" }), "Categoria removida.");
  }

  const full = (list?.length ?? 0) >= MAX_CATEGORIES;
  return <section className="page store-categories" aria-labelledby="categories-title" aria-busy={state === "loading" || busy}>{dialog}
    <PageHeader id="categories-title" title="Categorias" breadcrumbs={[{ href: `${storePath}/produtos`, label: "Produtos" }]}
      description="Organize os produtos para o comprador encontrar o que procura na vitrine. Cada produto pode ter uma categoria; escolha-a na edição do produto." />
    {state === "loading" ? <Loading context="categorias" /> : null}
    {state === "signed-out" ? <><p role="status">Sua sessão expirou. Entre novamente para gerenciar as categorias.</p><Link href="/entrar">Entrar</Link></> : null}
    {state === "missing" ? <PageState icon="store" title="Loja indisponível" href="/lojas" actionLabel="Voltar para minhas lojas">Escolha uma loja à qual você tem acesso.</PageState> : null}
    {state === "error" ? <PageState kind="error" icon="box" title="Não foi possível consultar as categorias" action={() => { setState("loading"); setAttempt(current => current + 1); }} actionLabel="Consultar novamente">Confira a conexão e tente novamente.</PageState> : null}
    {feedback ? <p ref={feedbackRef} tabIndex={-1} role={feedback.error ? "alert" : "status"} className={`feedback feedback-${feedback.error ? "error" : "success"}`}>{feedback.text}</p> : null}
    {state === "ready" && list ? <>
      {list.length === 0 ? <PageState icon="box" title="Nenhuma categoria">Crie a primeira categoria abaixo, por exemplo “Camisetas” ou “Acessórios”.</PageState> : <ul className="category-list">
        {list.map(category => <li key={category.id} className="category-item">
          {editing?.id === category.id ? <form onSubmit={event => void rename(event)} noValidate className="category-edit">
            <label htmlFor={`category-edit-${category.id}`} className="visually-hidden">Novo nome de “{category.name}”</label>
            <input ref={editRef} id={`category-edit-${category.id}`} value={editing.name} maxLength={120} autoComplete="off" disabled={busy}
              onChange={event => { setEditing({ id: category.id, name: event.target.value }); setEditError(null); }}
              aria-invalid={!!editError} aria-describedby={editError ? `category-edit-${category.id}-error` : undefined} />
            {editError ? <p id={`category-edit-${category.id}-error`} className="field-error">{editError}</p> : null}
            <div className="form-actions">
              <button type="submit" disabled={busy}>Salvar nome</button>
              <button type="button" className="button-secondary" disabled={busy} onClick={() => { setEditing(null); setEditError(null); }}>Cancelar</button>
            </div>
          </form> : <>
            <span className="category-name">{category.name}<span className="cell-hint">{productCountText(category.products)}</span></span>
            <span className="form-actions">
              <button type="button" className="button-secondary" disabled={busy} aria-label={`Renomear ${category.name}`} onClick={() => { setEditing({ id: category.id, name: category.name }); setEditError(null); }}>Renomear</button>
              <button type="button" className="button-secondary" disabled={busy} aria-label={`Remover ${category.name}`} onClick={() => void remove(category)}>Remover</button>
            </span>
          </>}
        </li>)}
      </ul>}
      <form className="side-card" onSubmit={event => void create(event)} noValidate aria-labelledby="category-create-title">
        <h2 id="category-create-title">Nova categoria</h2>
        <div className="form-field">
          <label htmlFor="category-name">Nome</label>
          <p id="category-name-help" className="field-help">Até 60 caracteres. Até {MAX_CATEGORIES} categorias por loja.</p>
          <input id="category-name" value={name} maxLength={120} autoComplete="off" disabled={busy || full}
            onChange={event => { setName(event.target.value); setNameError(null); }}
            aria-invalid={!!nameError} aria-describedby={`category-name-help${nameError ? " category-name-error" : ""}`} />
          {nameError ? <p id="category-name-error" className="field-error">{nameError}</p> : null}
        </div>
        <button type="submit" disabled={busy || full}>{busy ? "Salvando…" : "Criar categoria"}</button>
        {full ? <p className="field-help">A loja atingiu o limite de categorias.</p> : null}
      </form>
    </> : null}
  </section>;
}
