"use client";

import Link from "next/link";
import { type FormEvent, useEffect, useRef, useState } from "react";
import { apiRequest } from "@/lib/auth-api";
import {
  type Category, MAX_CATEGORIES, MAX_DEPTH, categoryError, categoryName, categoryNameError, categoryPath, categorySlug,
  categorySlugError, parentOptions, parseCategories, productCountText, siblings,
} from "@/lib/categories";
import PageHeader from "@/components/page-header";
import PageState from "@/components/page-state";
import { useConfirm } from "@/components/confirm-dialog";
import { Loading } from "@/components/ui/loading";

type Editing = { id: string; name: string; slug: string; parentId: string };

// MVP block 1c, tree since Sprint 8 (SC-041): categories of the store in up to 3 levels, with a
// storefront address, an order among siblings and a visibility of their own. Removing one keeps
// its products, without a category; hiding one keeps its products in the storefront.
export default function StoreCategories({ tenantId }: { tenantId: string }) {
  const [list, setList] = useState<Category[] | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "signed-out" | "missing" | "error">("loading");
  const [attempt, setAttempt] = useState(0);
  const [name, setName] = useState("");
  const [parent, setParent] = useState("");
  const [nameError, setNameError] = useState<string | null>(null);
  const [editing, setEditing] = useState<Editing | null>(null);
  const [editErrors, setEditErrors] = useState<{ name?: string; slug?: string }>({});
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

  /** Sends the requests in order and stops at the first failure; each answers the whole list. */
  async function mutate(requests: (() => Promise<Response>)[], success: string): Promise<boolean> {
    if (submitting.current) return false;
    submitting.current = true; setBusy(true); setFeedback(null);
    try {
      for (const request of requests) {
        const response = await request();
        if (!response.ok) {
          const body: { code?: string } = await response.json().catch(() => ({}));
          setFeedback({ text: categoryError(response.status, body.code), error: true });
          return false;
        }
        const parsed = parseCategories(await response.json());
        if (parsed) setList(parsed);
      }
      setFeedback({ text: success, error: false });
      return true;
    } catch {
      setFeedback({ text: "Não foi possível confirmar a alteração. Recarregue a lista antes de tentar de novo.", error: true });
      return false;
    } finally { submitting.current = false; setBusy(false); }
  }

  const put = (id: string, path: string, body: object) => () =>
    apiRequest(`${api}/${encodeURIComponent(id)}${path}`, { method: "PUT", body });

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const problem = categoryNameError(name);
    setNameError(problem);
    if (problem) { document.getElementById("category-name")?.focus(); return; }
    const value = categoryName(name);
    if (await mutate([() => apiRequest(api, { method: "POST", body: { name: value, parentId: parent || null } })], `Categoria “${value}” criada.`)) setName("");
  }

  async function save(event: FormEvent<HTMLFormElement>, category: Category) {
    event.preventDefault();
    if (!editing) return;
    const errors = { name: categoryNameError(editing.name) ?? undefined, slug: categorySlugError(editing.slug) ?? undefined };
    setEditErrors(errors);
    if (errors.name || errors.slug) { document.getElementById(`category-edit-${errors.name ? "name" : "slug"}`)?.focus(); return; }
    const requests: (() => Promise<Response>)[] = [];
    const value = categoryName(editing.name), slug = categorySlug(editing.slug), parentId = editing.parentId || null;
    if (value !== category.name) requests.push(put(category.id, "", { name: value }));
    if (slug !== category.slug) requests.push(put(category.id, "/slug", { slug }));
    // A new parent puts the category last among its new siblings.
    if (parentId !== category.parentId) requests.push(put(category.id, "/placement", { parentId, position: MAX_CATEGORIES }));
    if (!requests.length) { setEditing(null); return; }
    if (await mutate(requests, "Categoria salva.")) setEditing(null);
  }

  function move(category: Category, offset: -1 | 1) {
    if (!list) return;
    const index = siblings(list, category).findIndex(other => other.id === category.id);
    void mutate([put(category.id, "/placement", { parentId: category.parentId, position: index + offset })],
      `“${category.name}” ${offset < 0 ? "subiu" : "desceu"} uma posição.`);
  }

  function publish(category: Category) {
    void mutate([put(category.id, "/publication", { published: !category.published })],
      category.published ? `“${category.name}” oculta na vitrine. Os produtos continuam visíveis.` : `“${category.name}” visível na vitrine.`);
  }

  async function remove(category: Category) {
    if (list?.some(other => other.parentId === category.id)) {
      setFeedback({ text: categoryError(409, "HAS_CHILDREN"), error: true }); return;
    }
    if (!(await confirm({
      title: `Remover a categoria “${category.name}”?`,
      message: category.products === 0 ? "Nenhum produto usa esta categoria."
        : `${productCountText(category.products)} ${category.products === 1 ? "continuará" : "continuarão"} no catálogo, sem categoria.`,
      confirmLabel: "Remover categoria", tone: "danger",
    }))) return;
    void mutate([() => apiRequest(`${api}/${encodeURIComponent(category.id)}`, { method: "DELETE" })], "Categoria removida.");
  }

  const full = (list?.length ?? 0) >= MAX_CATEGORIES;
  return <section className="page store-categories" aria-labelledby="categories-title" aria-busy={state === "loading" || busy}>{dialog}
    <PageHeader id="categories-title" title="Categorias" breadcrumbs={[{ href: `${storePath}/produtos`, label: "Produtos" }]}
      description={`Organize os produtos em até ${MAX_DEPTH} níveis, por exemplo Feminino › Roupas › Camisetas. A ordem aqui é a do menu da vitrine. Escolha a categoria de cada produto na edição do produto.`} />
    {state === "loading" ? <Loading context="categorias" /> : null}
    {state === "signed-out" ? <><p role="status">Sua sessão expirou. Entre novamente para gerenciar as categorias.</p><Link href="/entrar">Entrar</Link></> : null}
    {state === "missing" ? <PageState icon="store" title="Loja indisponível" href="/lojas" actionLabel="Voltar para minhas lojas">Escolha uma loja à qual você tem acesso.</PageState> : null}
    {state === "error" ? <PageState kind="error" icon="box" title="Não foi possível consultar as categorias" action={() => { setState("loading"); setAttempt(current => current + 1); }} actionLabel="Consultar novamente">Confira a conexão e tente novamente.</PageState> : null}
    {feedback ? <p ref={feedbackRef} tabIndex={-1} role={feedback.error ? "alert" : "status"} className={`feedback feedback-${feedback.error ? "error" : "success"}`}>{feedback.text}</p> : null}
    {state === "ready" && list ? <>
      {list.length === 0 ? <PageState icon="box" title="Nenhuma categoria">Crie a primeira categoria abaixo, por exemplo “Feminino” ou “Acessórios”.</PageState> : <ul className="category-list">
        {list.map(category => {
          const group = siblings(list, category);
          const index = group.findIndex(other => other.id === category.id);
          return <li key={category.id} className={`category-item category-depth-${category.depth}`}>
            {editing?.id === category.id ? <form onSubmit={event => void save(event, category)} noValidate className="category-edit" aria-label={`Editar “${category.name}”`}>
              <div className="form-field">
                <label htmlFor="category-edit-name">Nome</label>
                <input ref={editRef} id="category-edit-name" value={editing.name} maxLength={120} autoComplete="off" disabled={busy}
                  onChange={event => { setEditing({ ...editing, name: event.target.value }); setEditErrors(current => ({ ...current, name: undefined })); }}
                  aria-invalid={!!editErrors.name} aria-describedby={editErrors.name ? "category-edit-name-error" : undefined} />
                {editErrors.name ? <p id="category-edit-name-error" className="field-error">{editErrors.name}</p> : null}
              </div>
              <div className="form-field">
                <label htmlFor="category-edit-slug">Endereço na vitrine</label>
                <p id="category-edit-slug-help" className="field-help">Aparece no link da categoria. Mudar o endereço quebra links já compartilhados.</p>
                <input id="category-edit-slug" value={editing.slug} maxLength={80} autoComplete="off" spellCheck={false} disabled={busy}
                  onChange={event => { setEditing({ ...editing, slug: event.target.value }); setEditErrors(current => ({ ...current, slug: undefined })); }}
                  aria-invalid={!!editErrors.slug} aria-describedby={`category-edit-slug-help${editErrors.slug ? " category-edit-slug-error" : ""}`} />
                {editErrors.slug ? <p id="category-edit-slug-error" className="field-error">{editErrors.slug}</p> : null}
              </div>
              <div className="form-field">
                <label htmlFor="category-edit-parent">Dentro de</label>
                <select id="category-edit-parent" value={editing.parentId} disabled={busy} onChange={event => setEditing({ ...editing, parentId: event.target.value })}>
                  <option value="">Nenhuma (categoria principal)</option>
                  {parentOptions(list, category.id).map(option => <option key={option.id} value={option.id}>{categoryPath(list, option.id)}</option>)}
                </select>
              </div>
              <div className="form-actions">
                <button type="submit" disabled={busy}>Salvar</button>
                <button type="button" className="button-secondary" disabled={busy} onClick={() => { setEditing(null); setEditErrors({}); }}>Cancelar</button>
              </div>
            </form> : <>
              <span className="category-name">
                <span>{category.name}{category.published ? null : <>{" "}<span className="status-badge status-neutral category-hidden">Oculta na vitrine</span></>}</span>
                <span className="cell-hint">{productCountText(category.products)} · /{category.slug}</span>
              </span>
              <span className="form-actions">
                <button type="button" className="button-secondary" disabled={busy || index <= 0} aria-label={`Subir ${category.name}`} onClick={() => move(category, -1)}>Subir</button>
                <button type="button" className="button-secondary" disabled={busy || index >= group.length - 1} aria-label={`Descer ${category.name}`} onClick={() => move(category, 1)}>Descer</button>
                <button type="button" className="button-secondary" disabled={busy} aria-label={`Editar ${category.name}`}
                  onClick={() => { setEditing({ id: category.id, name: category.name, slug: category.slug, parentId: category.parentId ?? "" }); setEditErrors({}); }}>Editar</button>
                <button type="button" className="button-secondary" disabled={busy} aria-label={`${category.published ? "Ocultar" : "Mostrar"} ${category.name} na vitrine`} onClick={() => publish(category)}>{category.published ? "Ocultar" : "Mostrar"}</button>
                <button type="button" className="button-secondary" disabled={busy} aria-label={`Remover ${category.name}`} onClick={() => void remove(category)}>Remover</button>
              </span>
            </>}
          </li>;
        })}
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
        <div className="form-field">
          <label htmlFor="category-parent">Dentro de</label>
          <select id="category-parent" value={parent} disabled={busy || full} onChange={event => setParent(event.target.value)}>
            <option value="">Nenhuma (categoria principal)</option>
            {parentOptions(list).map(option => <option key={option.id} value={option.id}>{categoryPath(list, option.id)}</option>)}
          </select>
        </div>
        <button type="submit" disabled={busy || full}>{busy ? "Salvando…" : "Criar categoria"}</button>
        {full ? <p className="field-help">A loja atingiu o limite de categorias.</p> : null}
      </form>
    </> : null}
  </section>;
}
