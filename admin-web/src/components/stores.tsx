"use client";

import Link from "next/link";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { billingRefusal } from "@/lib/billing";
import { apiRequest, requestError } from "@/lib/auth-api";
import type { TenantStore } from "@/lib/tenant-api";
import PageHeader from "@/components/page-header";
import { AddButton, FormSheet } from "@/components/form-sheet";
import { Loading } from "@/components/ui/loading";

export default function StoresPanel() {
  const [signedOut, setSignedOut] = useState(false);
  const [stores, setStores] = useState<TenantStore[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [slugError, setSlugError] = useState("");
  const [nameErrors, setNameErrors] = useState<{ companyName?: string; storeName?: string }>({});
  const [created, setCreated] = useState<TenantStore | null>(null);
  const [creating, setCreating] = useState(false);
  const submitting = useRef(false);
  const slugField = useRef<HTMLInputElement>(null);
  const feedback = useRef<HTMLParagraphElement>(null);

  useEffect(() => {
    const controller = new AbortController();
    apiRequest("/api/tenants", { signal: controller.signal }).then(async response => {
      if (response.status === 401) { setSignedOut(true); return; }
      if (!response.ok) throw new Error();
      setStores(await response.json());
    }).catch(() => {
      if (!controller.signal.aborted) setLoadError("Não foi possível consultar suas lojas. Tente novamente.");
    }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [attempt]);

  useEffect(() => { if (error || created) feedback.current?.focus(); }, [error, created]);

  async function createStore(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current) return;
    const form = event.currentTarget;
    const values = new FormData(form);
    const body = { companyName: String(values.get("companyName") ?? "").trim(),
      storeName: String(values.get("storeName") ?? "").trim(), slug: String(values.get("slug") ?? "") };
    setError(""); setCreated(null);
    // Persistent errors next to each field, in the form order, instead of the browser's bubble.
    const names: { companyName?: string; storeName?: string } = {};
    for (const field of ["companyName", "storeName"] as const) {
      if ([...body[field]].length < 1 || [...body[field]].length > 120) names[field] = "Use de 1 a 120 caracteres para o nome.";
    }
    const slugProblem = /^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(body.slug) && body.slug.length >= 3 && body.slug.length <= 63
      ? "" : "Use de 3 a 63 letras minúsculas sem acentos, números e hífens, começando e terminando com letra ou número.";
    setNameErrors(names); setSlugError(slugProblem);
    const first = names.companyName ? "company-name" : names.storeName ? "store-name" : slugProblem ? "store-slug" : null;
    if (first) { document.getElementById(first)?.focus(); return; }
    submitting.current = true; setPending(true);
    try {
      const response = await apiRequest("/api/tenants", { method: "POST", body });
      if (response.status === 401) { setSignedOut(true); return; }
      if (response.status === 409 || response.status === 402) {
        const refusal = billingRefusal(response.status, await response.json().catch(() => null));
        if (refusal) { setError(refusal); return; }
        setSlugError("Este identificador já está em uso. Escolha outro para sua loja.");
        slugField.current?.focus(); return;
      }
      if (!response.ok) {
        setError(response.status === 400 ? "Confira os nomes e o identificador da loja e tente novamente." : requestError(response.status));
        return;
      }
      const store: TenantStore = await response.json();
      setStores(current => [...current, store]);
      setCreated(store); form.reset(); setCreating(false);
    } catch { setError("Não foi possível confirmar a criação. Consulte suas lojas antes de tentar novamente."); }
    finally { submitting.current = false; setPending(false); }
  }

  if (signedOut) return <section className="page" aria-labelledby="stores-title">
    <PageHeader id="stores-title" title="Minhas lojas" />
    <p role="status">Entre para consultar suas lojas. Sua sessão pode ter expirado.</p><Link href="/entrar">Entrar</Link>
  </section>;

  return <section className="page" aria-labelledby="stores-title">
    <PageHeader id="stores-title" title="Minhas lojas" description="Escolha uma loja para gerenciar produtos, vitrine e entrega, ou crie uma nova."
      action={loading || loadError ? undefined : <AddButton onClick={() => { setError(""); setCreated(null); setCreating(true); }}>Nova loja</AddButton>} />
    {created && !creating ? <p ref={feedback} role="status" tabIndex={-1} className="feedback feedback-success">Loja criada. <Link href={`/lojas/${encodeURIComponent(created.id)}`}>Visualizar {created.store.name}</Link></p> : null}
    <div className="stores-panel">
    {loading ? <Loading context="suas lojas" /> : loadError ? <div>
      <p role="alert" className="feedback feedback-error">{loadError}</p>
      <button type="button" onClick={() => { setLoading(true); setLoadError(""); setAttempt(current => current + 1); }}>Consultar lojas novamente</button>
    </div> : stores.length === 0 ? <p>Você ainda não tem uma loja. Use “Nova loja” para criar a primeira.</p> : <ul className="store-list">
      {stores.map(tenant => <li key={tenant.id} className="store-card">
        <h2><Link href={`/lojas/${encodeURIComponent(tenant.id)}`}>{tenant.store.name}</Link></h2>
        <p>{tenant.companyName}</p><p className="store-status">{tenant.store.status === "PUBLISHED" ? "Vitrine publicada" : "Em configuração · Vitrine oculta"} · {tenant.role === "STAFF" ? "Funcionário" : "Proprietário"}</p>
      </li>)}
    </ul>}
    </div>
    <FormSheet open={creating} onOpenChange={setCreating} titleId="store-create-title" title="Criar minha loja" busy={pending} feedbackRef={feedback}
      description="A loja começa em configuração, com a vitrine oculta. Você decide quando publicar a loja e quais produtos exibir.">
      {error ? <p ref={feedback} role="alert" tabIndex={-1} className="feedback feedback-error">{error}</p> : null}
      <form onSubmit={(event) => void createStore(event)} aria-busy={pending} noValidate aria-labelledby="store-create-title">
        <div className="form-field"><label htmlFor="company-name">Nome da empresa</label>
          <input id="company-name" name="companyName" autoComplete="organization" required maxLength={240}
            aria-invalid={nameErrors.companyName ? true : undefined} aria-describedby={nameErrors.companyName ? "company-name-error" : undefined}
            onInput={() => { if (nameErrors.companyName) setNameErrors(current => ({ ...current, companyName: undefined })); }} />
          {nameErrors.companyName ? <p id="company-name-error" className="field-error">{nameErrors.companyName}</p> : null}
        </div>
        <div className="form-field"><label htmlFor="store-name">Nome da loja</label>
          <input id="store-name" name="storeName" autoComplete="off" required maxLength={240}
            aria-invalid={nameErrors.storeName ? true : undefined} aria-describedby={nameErrors.storeName ? "store-name-error" : undefined}
            onInput={() => { if (nameErrors.storeName) setNameErrors(current => ({ ...current, storeName: undefined })); }} />
          {nameErrors.storeName ? <p id="store-name-error" className="field-error">{nameErrors.storeName}</p> : null}
        </div>
        <div className="form-field"><label htmlFor="store-slug">Identificador da loja</label>
          <p id="slug-help" className="field-help">Use de 3 a 63 letras minúsculas sem acentos, números e hífens. Comece e termine com letra ou número. Exemplo: minha-loja.</p>
          <input ref={slugField} id="store-slug" name="slug" autoComplete="off" autoCapitalize="none" spellCheck={false}
            required minLength={3} maxLength={63} pattern="[a-z0-9]+(?:-[a-z0-9]+)*"
            aria-invalid={slugError ? true : undefined} aria-describedby={`slug-help${slugError ? " slug-error" : ""}`}
            onInput={() => { if (slugError) setSlugError(""); }} />
          {slugError ? <p id="slug-error" className="field-error" role="alert">{slugError}</p> : null}
        </div>
        <button type="submit" disabled={pending}>{pending ? "Criando loja…" : "Criar minha loja"}</button>
      </form>
    </FormSheet>
  </section>;
}
