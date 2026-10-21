"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { apiRequest, requestError } from "@/lib/auth-api";
import { formatDateTime } from "@/lib/orders";
import { authorizationTarget, callbackMessage, parseConnection, STATUS_TEXT, type PaymentConnection } from "@/lib/payments";
import PageHeader from "@/components/page-header";
import StatusBadge from "@/components/status-badge";
import { useConfirm } from "@/components/confirm-dialog";

// SC-012: connect the store's own Mercado Pago account (ADR-0003). The backend owns the OAuth state,
// the PKCE verifier and the encrypted tokens; this page only starts the attempt, shows the
// provider account id and disconnects.
export default function StorePayments({ tenantId, result }: { tenantId: string; result: string | null }) {
  const [connection, setConnection] = useState<PaymentConnection | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "signed-out" | "missing" | "error">("loading");
  const [attempt, setAttempt] = useState(0);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState(() => callbackMessage(result));
  const { confirm, dialog } = useConfirm();
  const feedbackRef = useRef<HTMLParagraphElement>(null);
  const path = `/api/tenants/${encodeURIComponent(tenantId)}/payments/mercado-pago`;
  const router = useRouter();
  const pathname = usePathname();

  // The result word is shown once; reloading the page must not repeat it. Deferred until the
  // initial navigation settles.
  useEffect(() => {
    if (!result) return;
    const timer = window.setTimeout(() => router.replace(pathname, { scroll: false }), 0);
    return () => window.clearTimeout(timer);
  }, [result, router, pathname]);

  useEffect(() => {
    const controller = new AbortController();
    apiRequest(path, { signal: controller.signal }).then(async response => {
      if (response.status === 401) { setState("signed-out"); return; }
      if (response.status === 403 || response.status === 404) { setState("missing"); return; }
      if (!response.ok) throw new Error();
      const current = parseConnection(await response.json());
      if (!current) throw new Error();
      setConnection(current); setState("ready");
    }).catch(() => { if (!controller.signal.aborted) setState("error"); });
    return () => controller.abort();
  }, [path, attempt]);

  // Next frame: the streamed page may still be moving into place when the effect first runs.
  useEffect(() => {
    if (!feedback) return;
    const frame = requestAnimationFrame(() => feedbackRef.current?.focus());
    return () => cancelAnimationFrame(frame);
  }, [feedback]);

  async function connect() {
    let navigating = false;
    if (busy) return;
    setBusy(true); setFeedback(null);
    try {
      const response = await apiRequest(`${path}/authorization`, { method: "POST" });
      if (response.status === 401) { setFeedback({ text: "Sua sessão expirou. Entre novamente antes de conectar.", error: true }); return; }
      if (response.status === 503) { setFeedback({ text: "A conexão com o Mercado Pago não está disponível neste ambiente.", error: true }); return; }
      if (!response.ok) { setFeedback({ text: requestError(response.status), error: true }); return; }
      const target = authorizationTarget(await response.json());
      if (!target) { setFeedback({ text: "Não foi possível iniciar a conexão. Tente novamente.", error: true }); return; }
      // Keep the button disabled while the browser leaves for Mercado Pago (no second attempt).
      navigating = true;
      window.location.assign(target);
      return;
    } catch { setFeedback({ text: "Não foi possível iniciar a conexão. Tente novamente.", error: true }); }
    finally { if (!navigating) setBusy(false); }
  }

  async function disconnect() {
    if (busy || !(await confirm({
      title: "Desconectar o Mercado Pago?",
      message: "A loja deixa de receber pagamentos por esta conta. Os acessos guardados são apagados e, para voltar, será preciso autorizar de novo no Mercado Pago. Pagamentos ainda em confirmação só são confirmados depois que a mesma conta for conectada de novo. Para encerrar também o acesso do lado do Mercado Pago, revogue a aplicação no painel da sua conta.",
      confirmLabel: "Desconectar", tone: "danger",
    }))) return;
    setBusy(true); setFeedback(null);
    try {
      const response = await apiRequest(path, { method: "DELETE" });
      if (response.status === 401) { setFeedback({ text: "Sua sessão expirou. Entre novamente antes de desconectar.", error: true }); return; }
      if (!response.ok) { setFeedback({ text: requestError(response.status), error: true }); return; }
      const current = parseConnection(await response.json());
      if (current) setConnection(current);
      setFeedback({ text: "Conta Mercado Pago desconectada.", error: false });
    } catch { setFeedback({ text: "Não foi possível confirmar a desconexão. Consulte a página novamente.", error: true }); }
    finally { setBusy(false); }
  }

  const status = connection?.status;
  const canConnect = status === "NOT_CONNECTED" || status === "EXPIRED" || status === "REVOKED";

  return <section className="page page-panel" aria-labelledby="payments-title" aria-busy={state === "loading" || busy}>{dialog}
    <PageHeader id="payments-title" title="Pagamentos"
      description="Conecte a conta Mercado Pago da sua loja. O pagamento cai direto na sua conta; a plataforma não guarda dados de cartão." />
    {feedback ? <p ref={feedbackRef} tabIndex={-1} role={feedback.error ? "alert" : "status"} className={`feedback feedback-${feedback.error ? "error" : "success"}`}>{feedback.text}</p> : null}
    {state === "loading" ? <p role="status">Consultando a conexão de pagamentos…</p> : null}
    {state === "signed-out" ? <><p role="status">Entre novamente para consultar os pagamentos. Sua sessão pode ter expirado.</p><Link href="/entrar">Entrar</Link></> : null}
    {state === "missing" ? <p role="status">Loja indisponível. Volte para suas lojas e escolha uma loja à qual você tem acesso.</p> : null}
    {state === "error" ? <><p role="alert" className="feedback feedback-error">Não foi possível consultar a conexão de pagamentos.</p>
      <button type="button" onClick={() => { setState("loading"); setAttempt(current => current + 1); }}>Consultar novamente</button></> : null}
    {state === "ready" && connection ? <div className="variant-definition">
      <h2>Mercado Pago</h2>
      <p><StatusBadge tone={status === "CONNECTED" ? "positive" : "neutral"}>{STATUS_TEXT[connection.status]}</StatusBadge></p>
      {connection.accountId ? <dl className="order-facts">
        <div><dt>Conta</dt><dd translate="no">{connection.accountId}</dd></div>
        {status === "CONNECTED" || status === "EXPIRED" ? <>
          <div><dt>Conectada em</dt><dd>{formatDateTime(connection.connectedAt)}</dd></div>
          <div><dt>Autorização válida até</dt><dd>{formatDateTime(connection.accessExpiresAt)}</dd></div>
          {connection.refreshedAt ? <div><dt>Renovada em</dt><dd>{formatDateTime(connection.refreshedAt)}</dd></div> : null}
        </> : <div><dt>Encerrada em</dt><dd>{formatDateTime(connection.closedAt)}</dd></div>}
      </dl> : null}
      {status === "UNAVAILABLE" ? <p className="field-help">As credenciais da aplicação Mercado Pago ainda não foram configuradas neste ambiente.</p> : null}
      {status === "REVOKED" ? <p className="field-help">A autorização foi retirada no Mercado Pago ou expirou. Conecte de novo para voltar a receber.</p> : null}
      {status === "EXPIRED" ? <p className="field-help">A autorização venceu sem renovação. Conecte de novo para voltar a receber.</p> : null}
      <p className="field-help">Ambiente de testes (sandbox): use somente contas de teste do Mercado Pago.</p>
      <div className="publication-actions">
        {canConnect ? <button type="button" disabled={busy} onClick={() => void connect()}>{busy ? "Abrindo o Mercado Pago…" : "Conectar Mercado Pago"}</button> : null}
        {status === "CONNECTED" ? <button type="button" className="button-danger" disabled={busy} onClick={() => void disconnect()}>{busy ? "Desconectando…" : "Desconectar"}</button> : null}
      </div>
    </div> : null}
  </section>;
}
