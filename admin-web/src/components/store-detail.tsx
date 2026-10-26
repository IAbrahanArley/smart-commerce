"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { apiRequest, requestError } from "@/lib/auth-api";
import { storefrontUrl, type TenantStore } from "@/lib/tenant-api";
import PageHeader from "@/components/page-header";
import StatusBadge from "@/components/status-badge";
import { useConfirm } from "@/components/confirm-dialog";
import StoreSummary from "@/components/store-summary";
import StoreDashboard from "@/components/store-dashboard";
import StoreAlerts from "@/components/store-alerts";
import { parseStatus } from "@/lib/advisor";
import type { ReportPeriodKey } from "@/lib/finance";
import PageState from "@/components/page-state";
import { Loading } from "@/components/ui/loading";

export default function StoreDetail({ tenantId, period }: { tenantId: string; period: ReportPeriodKey }) {
  const [store, setStore] = useState<TenantStore | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "missing" | "signed-out" | "error">("loading");
  const [attempt, setAttempt] = useState(0);
  const [explain, setExplain] = useState(false);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{ text: string; error: boolean } | null>(null);
  const submitting = useRef(false);
  const feedbackRef = useRef<HTMLParagraphElement>(null);
  const publicUrl = store ? storefrontUrl(store.store.slug) : null;
  const { confirm, dialog } = useConfirm();

  useEffect(() => { if (feedback) feedbackRef.current?.focus(); }, [feedback]);

  async function setPublication() {
    if (!store || submitting.current) return;
    const published = store.store.status !== "PUBLISHED";
    if (!(await confirm(published
      ? { title: "Publicar a loja?", message: "Qualquer pessoa com o link poderá consultar o nome da loja e o nome, preço e disponibilidade dos produtos selecionados.", confirmLabel: "Publicar loja" }
      : { title: "Ocultar a loja?", message: "A vitrine e seus produtos deixarão de estar disponíveis para consulta pública.", confirmLabel: "Ocultar loja" }))) return;
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

  // SC-028: "Explicar" only appears when the assistant answers here; any failure hides it.
  useEffect(() => {
    const controller = new AbortController();
    apiRequest(`/api/tenants/${encodeURIComponent(tenantId)}/advisor`, { signal: controller.signal })
      .then(async response => { if (response.ok) setExplain(Boolean(parseStatus(await response.json())?.enabled)); })
      .catch(() => undefined);
    return () => controller.abort();
  }, [tenantId]);

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

  return <section className="page" aria-labelledby="store-title">{dialog}
    <PageHeader id="store-title" title={state === "ready" && store ? <span translate="no">{store.store.name}</span> : "Minha loja"}
      description={state === "ready" && store ? store.store.status === "PUBLISHED" ? <StatusBadge tone="positive">Vitrine publicada</StatusBadge> : <StatusBadge tone="neutral">Vitrine oculta</StatusBadge> : undefined}
      action={state === "ready" ? <Link className="button-link" href={`/lojas/${encodeURIComponent(tenantId)}/produtos`}>Gerenciar produtos</Link> : undefined} />
    {state === "loading" ? <Loading context="sua loja" /> : null}
    {state === "signed-out" ? <><p role="status">Entre novamente para consultar sua loja. Sua sessão pode ter expirado.</p><Link href="/entrar">Entrar</Link></> : null}
    {state === "missing" ? <PageState icon="store" title="Loja indisponível" href="/lojas" actionLabel="Voltar para minhas lojas">Escolha uma loja à qual você tem acesso.</PageState> : null}
    {state === "error" ? <PageState kind="error" icon="store" title="Não foi possível consultar sua loja" action={() => { setState("loading"); setAttempt(current => current + 1); }} actionLabel="Consultar loja novamente">Confira a conexão e tente carregar os dados novamente.</PageState> : null}
    {state === "ready" && store ? <>
      <p className="notice">{store.store.status === "PUBLISHED" ? "Vitrine publicada. Qualquer pessoa com o link pode consultar os produtos selecionados." : "Vitrine oculta. Selecione os produtos e publique a loja quando estiver pronta."}</p>
      <StoreAlerts tenantId={tenantId} explain={explain} />
      <StoreSummary tenantId={tenantId} published={store.store.status === "PUBLISHED"} />
      <StoreDashboard tenantId={tenantId} period={period} explain={explain} />
      <div className="overview-grid">
      <section className="side-card store-summary" aria-labelledby="store-data-title"><h2 id="store-data-title">Dados da loja</h2><dl className="store-data"><dt>Empresa</dt><dd>{store.companyName}</dd><dt>Nome da loja</dt><dd>{store.store.name}</dd>
        <dt>Identificador da loja</dt><dd>{store.store.slug}</dd><dt>Seu acesso</dt><dd>Proprietário</dd></dl>
        <p><Link href={`/lojas/${encodeURIComponent(tenantId)}/dados`}>Editar nome, logo e contatos</Link></p></section>
      <section className="side-card" aria-labelledby="publication-title"><h2 id="publication-title">Publicação da vitrine</h2>
        <p>Somente nome, preço e disponibilidade dos produtos publicados ficam acessíveis. SKU, custo e histórico de estoque continuam restritos ao painel.</p>
        {feedback ? <p ref={feedbackRef} tabIndex={-1} role={feedback.error ? "alert" : "status"} className={`feedback feedback-${feedback.error ? "error" : "success"}`}>{feedback.text}</p> : null}
        <div className="publication-actions"><button type="button" disabled={busy} onClick={() => void setPublication()}>{busy ? "Atualizando publicação…" : store.store.status === "PUBLISHED" ? "Ocultar loja" : "Publicar loja"}</button>
          <button type="button" disabled={busy} onClick={() => { setFeedback(null); setState("loading"); setAttempt(current => current + 1); }}>Atualizar estado da loja</button>
          {publicUrl ? <a className="back-link" href={publicUrl} target="_blank" rel="noopener noreferrer">Abrir vitrine em nova aba</a> : <p className="field-help">Endereço da vitrine indisponível. Confira a configuração da aplicação.</p>}
        </div>
        {store.store.status === "DRAFT" ? <p className="field-help">O endereço público ficará indisponível até você publicar a loja.</p> : null}
      </section>
      <section className="side-card" aria-labelledby="store-fulfillment-title"><h2 id="store-fulfillment-title">Entrega</h2>
        <p>Retirada na loja e frete fixo exibidos na vitrine publicada.</p>
        <Link className="back-link" href={`/lojas/${encodeURIComponent(tenantId)}/entrega`}>Configurar entrega</Link></section>
      </div>
    </> : null}
  </section>;
}
