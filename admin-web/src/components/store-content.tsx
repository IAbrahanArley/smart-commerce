"use client";

import Link from "next/link";
import { type FormEvent, useEffect, useRef, useState } from "react";
import { apiRequest } from "@/lib/auth-api";
import { type Company, type StorePage, cnpjError, contentError, parseCompany, parsePages } from "@/lib/content";
import PageHeader from "@/components/page-header";
import PageState from "@/components/page-state";
import DescriptionEditor from "@/components/description-editor";
import HomeContent from "@/components/home-content";
import { Loading } from "@/components/ui/loading";

type Field = { name: keyof Company; label: string; help?: string; max: number };
const COMPANY_FIELDS: Field[] = [
  { name: "announcement", label: "Faixa de aviso", help: "Aparece no topo de todas as páginas da vitrine, por exemplo “Retirada grátis na loja”. Em branco, não aparece.", max: 120 },
  { name: "legalName", label: "Razão social", max: 120 },
  { name: "cnpj", label: "CNPJ", help: "Aparece no rodapé, como pede o Código de Defesa do Consumidor para lojas on-line.", max: 20 },
  { name: "phone", label: "Telefone", help: "Número brasileiro com DDD.", max: 25 },
  { name: "address", label: "Endereço", help: "Opcional. Aparece no rodapé.", max: 240 },
  { name: "facebook", label: "Facebook", help: "Usuário ou link da página.", max: 120 },
  { name: "tiktok", label: "TikTok", help: "Usuário, com ou sem @.", max: 60 },
  { name: "youtube", label: "YouTube", help: "Usuário do canal (@canal) ou link.", max: 120 },
];

// Sprint 8 (SC-043): what the storefront shows besides the catalog. Company details for the
// footer and the announcement bar, and the institutional pages. WhatsApp, e-mail, Instagram and
// the logo stay in "Dados da loja".
export default function StoreContent({ tenantId }: { tenantId: string }) {
  const [company, setCompany] = useState<Company | null>(null);
  const [pages, setPages] = useState<StorePage[] | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "signed-out" | "missing" | "error">("loading");
  const [attempt, setAttempt] = useState(0);
  const [errors, setErrors] = useState<Partial<Record<keyof Company, string>>>({});
  const [page, setPage] = useState("sobre");
  const [draft, setDraft] = useState("");
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{ text: string; error: boolean } | null>(null);
  const submitting = useRef(false);
  const feedbackRef = useRef<HTMLParagraphElement>(null);
  const api = `/api/tenants/${encodeURIComponent(tenantId)}/content`;
  const storePath = `/lojas/${encodeURIComponent(tenantId)}`;

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([apiRequest(`${api}/company`, { signal: controller.signal }), apiRequest(`${api}/pages`, { signal: controller.signal })]).then(async ([companyResponse, pagesResponse]) => {
      if (companyResponse.status === 401) { setState("signed-out"); return; }
      if (companyResponse.status === 404 || companyResponse.status === 403) { setState("missing"); return; }
      if (!companyResponse.ok || !pagesResponse.ok) throw new Error();
      const parsedCompany = parseCompany(await companyResponse.json()), parsedPages = parsePages(await pagesResponse.json());
      if (!parsedCompany || !parsedPages) throw new Error();
      if (controller.signal.aborted) return;
      setCompany(parsedCompany); setPages(parsedPages);
      setDraft(parsedPages.find(item => item.slug === "sobre")?.bodyHtml ?? "");
      setState("ready");
    }).catch(() => { if (!controller.signal.aborted) setState("error"); });
    return () => controller.abort();
  }, [api, attempt]);

  useEffect(() => { if (feedback) feedbackRef.current?.focus(); }, [feedback]);

  async function send(path: string, body: object, success: string, apply: (value: unknown) => boolean) {
    if (submitting.current) return;
    submitting.current = true; setBusy(true); setFeedback(null);
    try {
      const response = await apiRequest(`${api}${path}`, { method: "PUT", body });
      if (!response.ok) { setFeedback({ text: contentError(response.status), error: true }); return; }
      if (!apply(await response.json())) throw new Error();
      setFeedback({ text: success, error: false });
    } catch { setFeedback({ text: "Não foi possível confirmar. Recarregue a página antes de tentar de novo.", error: true }); }
    finally { submitting.current = false; setBusy(false); }
  }

  function saveCompany(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!company) return;
    const problem = cnpjError(company.cnpj);
    setErrors(problem ? { cnpj: problem } : {});
    if (problem) { document.getElementById("company-cnpj")?.focus(); return; }
    void send("/company", company, "Dados da empresa salvos.", value => { const parsed = parseCompany(value); if (parsed) setCompany(parsed); return !!parsed; });
  }

  function choosePage(slug: string) {
    setPage(slug);
    setDraft(pages?.find(item => item.slug === slug)?.bodyHtml ?? "");
  }

  function savePage(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const title = pages?.find(item => item.slug === page)?.title ?? "Página";
    void send(`/pages/${encodeURIComponent(page)}`, { bodyHtml: draft }, draft.replace(/<[^>]*>/g, "").trim() ? `Página “${title}” salva.` : `Página “${title}” removida da vitrine.`, value => {
      const parsed = parsePages(value);
      if (!parsed) return false;
      setPages(parsed); setDraft(parsed.find(item => item.slug === page)?.bodyHtml ?? "");
      return true;
    });
  }

  return <section className="page store-content" aria-labelledby="content-title" aria-busy={state === "loading" || busy}>
    <PageHeader id="content-title" title="Aparência e conteúdo"
      description="O que a vitrine mostra além dos produtos: faixa de aviso, dados da empresa no rodapé, páginas institucionais, banners e seções da página inicial. WhatsApp, e-mail, Instagram e logo ficam em Dados da loja." />
    {state === "loading" ? <Loading context="conteúdo da loja" /> : null}
    {state === "signed-out" ? <><p role="status">Sua sessão expirou. Entre novamente para continuar.</p><Link href="/entrar">Entrar</Link></> : null}
    {state === "missing" ? <PageState icon="store" title="Loja indisponível" href="/lojas" actionLabel="Voltar para minhas lojas">Escolha uma loja à qual você tem acesso.</PageState> : null}
    {state === "error" ? <PageState kind="error" icon="box" title="Não foi possível consultar o conteúdo" action={() => { setState("loading"); setAttempt(current => current + 1); }} actionLabel="Consultar novamente">Confira a conexão e tente novamente.</PageState> : null}
    {feedback ? <p ref={feedbackRef} tabIndex={-1} role={feedback.error ? "alert" : "status"} className={`feedback feedback-${feedback.error ? "error" : "success"}`}>{feedback.text}</p> : null}
    {state === "ready" && company && pages ? <>
      <form className="side-card content-card" onSubmit={saveCompany} noValidate aria-labelledby="company-title">
        <h2 id="company-title">Empresa e faixa de aviso</h2>
        <p className="field-help">Todos os campos são opcionais; em branco, não aparecem. <Link href={`${storePath}/dados`}>Dados da loja</Link></p>
        <div className="content-fields">
          {COMPANY_FIELDS.map(field => <div className="form-field" key={field.name}>
            <label htmlFor={`company-${field.name}`}>{field.label}</label>
            {field.help ? <p id={`company-${field.name}-help`} className="field-help">{field.help}</p> : null}
            <input id={`company-${field.name}`} value={company[field.name]} maxLength={field.max} autoComplete="off" disabled={busy}
              inputMode={field.name === "cnpj" || field.name === "phone" ? "numeric" : undefined}
              onChange={event => { setCompany({ ...company, [field.name]: event.target.value }); setErrors(current => ({ ...current, [field.name]: undefined })); }}
              aria-invalid={!!errors[field.name]}
              aria-describedby={[field.help ? `company-${field.name}-help` : "", errors[field.name] ? `company-${field.name}-error` : ""].filter(Boolean).join(" ") || undefined} />
            {errors[field.name] ? <p id={`company-${field.name}-error`} className="field-error">{errors[field.name]}</p> : null}
          </div>)}
        </div>
        <button type="submit" disabled={busy}>{busy ? "Salvando…" : "Salvar dados da empresa"}</button>
      </form>
      <form className="side-card content-card" onSubmit={savePage} noValidate aria-labelledby="pages-title">
        <h2 id="pages-title">Páginas institucionais</h2>
        <p className="field-help">Aparecem no rodapé da vitrine. Uma página em branco não aparece.</p>
        <div className="form-field">
          <label htmlFor="content-page">Página</label>
          <select id="content-page" value={page} disabled={busy} onChange={event => choosePage(event.target.value)}>
            {pages.map(item => <option key={item.slug} value={item.slug}>{item.title}{item.bodyHtml ? "" : " (em branco)"}</option>)}
          </select>
        </div>
        <DescriptionEditor id="content-page-body" label={`Texto de “${pages.find(item => item.slug === page)?.title ?? ""}”`} rows={12} value={draft} onChange={setDraft} disabled={busy} />
        <button type="submit" disabled={busy}>{busy ? "Salvando…" : "Salvar página"}</button>
      </form>
      <HomeContent tenantId={tenantId} />
    </> : null}
  </section>;
}
