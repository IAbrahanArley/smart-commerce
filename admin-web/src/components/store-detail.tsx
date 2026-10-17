"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { apiRequest, requestError } from "@/lib/auth-api";
import { storefrontUrl, type TenantStore } from "@/lib/tenant-api";

export default function StoreDetail({ tenantId }: { tenantId: string }) {
  const [store, setStore] = useState<TenantStore | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "missing" | "signed-out" | "error">("loading");
  const [attempt, setAttempt] = useState(0);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{ text: string; error: boolean } | null>(null);
  const submitting = useRef(false);
  const feedbackRef = useRef<HTMLParagraphElement>(null);
  const publicUrl = store ? storefrontUrl(store.store.slug) : null;

  useEffect(() => { if (feedback) feedbackRef.current?.focus(); }, [feedback]);

  async function setPublication() {
    if (!store || submitting.current) return;
    const published = store.store.status !== "PUBLISHED";
    if (!window.confirm(published
      ? "Publicar a loja? Qualquer pessoa com o link poderá consultar o nome da loja e o nome, preço e disponibilidade dos produtos selecionados. Compras ainda não estão disponíveis."
      : "Ocultar a loja? A vitrine e seus produtos deixarão de estar disponíveis para consulta pública.")) return;
    submitting.current = true; setBusy(true); setFeedback(null);
    try {
      const response = await apiRequest(`/api/tenants/${encodeURIComponent(tenantId)}/publication`, { method: "PUT", body: { published } });
      if (response.status === 401) { setState("signed-out"); return; }
      if (response.status === 403 || response.status === 404) { setState("missing"); return; }
      if (!response.ok) { setFeedback({ text: requestError(response.status), error: true }); return; }
      setStore(await response.json());
      setFeedback({ text: published ? "Loja publicada. A vitrine exibe somente os produtos que você selecionou individualmente." : "Loja oculta. A vitrine não está disponível para consulta pública.", error: false });
    } catch { setFeedback({ text: "Não foi possível confirmar a publicação. Atualize o estado da loja antes de tentar novamente.", error: true }); }
    finally { submitting.current = false; setBusy(false); }
  }

  useEffect(() => {
    const controller = new AbortController();
    apiRequest(`/api/tenants/${encodeURIComponent(tenantId)}`, { signal: controller.signal }).then(async response => {
      if (response.status === 401) { setState("signed-out"); return; }
      if (response.status === 404 || response.status === 403) { setState("missing"); return; }
      if (!response.ok) throw new Error();
      setStore(await response.json()); setState("ready");
    }).catch(() => { if (!controller.signal.aborted) setState("error"); });
    return () => controller.abort();
  }, [tenantId, attempt]);

  return <section className="stores-panel" aria-labelledby="store-title">
    <Link className="back-link" href="/conta">Voltar para minhas lojas</Link>
    <h1 id="store-title">{state === "ready" && store ? store.store.name : "Minha loja"}</h1>
    {state === "loading" ? <p role="status">Consultando sua loja…</p> : null}
    {state === "signed-out" ? <><p role="status">Entre novamente para consultar sua loja. Sua sessão pode ter expirado.</p><Link href="/entrar">Entrar</Link></> : null}
    {state === "missing" ? <p role="status">Loja indisponível. Volte para suas lojas e escolha uma loja à qual você tem acesso.</p> : null}
    {state === "error" ? <><p role="alert" className="feedback feedback-error">Não foi possível consultar sua loja. Tente novamente.</p>
      <button type="button" onClick={() => { setState("loading"); setAttempt(current => current + 1); }}>Consultar loja novamente</button></> : null}
    {state === "ready" && store ? <>
      <p className="notice">{store.store.status === "PUBLISHED" ? "Vitrine publicada. Qualquer pessoa com o link pode consultar os produtos selecionados. Compras ainda não estão disponíveis." : "Vitrine oculta. Selecione os produtos e publique a loja quando estiver pronta."}</p>
      <dl className="store-data"><dt>Empresa</dt><dd>{store.companyName}</dd><dt>Nome da loja</dt><dd>{store.store.name}</dd>
        <dt>Identificador da loja</dt><dd>{store.store.slug}</dd><dt>Seu acesso</dt><dd>Proprietário</dd></dl>
      <Link className="back-link" href={`/lojas/${encodeURIComponent(tenantId)}/produtos`}>Gerenciar produtos</Link>
      <section className="store-create" aria-labelledby="publication-title"><h2 id="publication-title">Publicação da vitrine</h2>
        <p>Somente nome, preço e disponibilidade dos produtos publicados ficam acessíveis. SKU, custo e histórico de estoque continuam restritos ao painel.</p>
        {feedback ? <p ref={feedbackRef} tabIndex={-1} role={feedback.error ? "alert" : "status"} className={`feedback feedback-${feedback.error ? "error" : "success"}`}>{feedback.text}</p> : null}
        <div className="publication-actions"><button type="button" disabled={busy} onClick={() => void setPublication()}>{busy ? "Atualizando publicação…" : store.store.status === "PUBLISHED" ? "Ocultar loja" : "Publicar loja"}</button>
          <button type="button" disabled={busy} onClick={() => { setFeedback(null); setState("loading"); setAttempt(current => current + 1); }}>Atualizar estado da loja</button>
          {publicUrl ? <a className="back-link" href={publicUrl} target="_blank" rel="noopener noreferrer">Abrir vitrine em nova aba</a> : <p className="field-help">Endereço da vitrine indisponível. Confira a configuração da aplicação.</p>}
        </div>
        {store.store.status === "DRAFT" ? <p className="field-help">O endereço público ficará indisponível até você publicar a loja.</p> : null}
      </section>
    </> : null}
  </section>;
}
