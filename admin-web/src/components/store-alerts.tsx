"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { apiRequest } from "@/lib/auth-api";
import { alertLink, alertText, parseAlerts, SEVERITY_TEXT, type StoreAlert } from "@/lib/alerts";
import { explainHref } from "@/lib/advisor";

// SC-024: deterministic store alerts on the overview. They work without the assistant; the
// severity is written out, never conveyed by color alone.
export default function StoreAlerts({ tenantId, explain = false }: { tenantId: string; explain?: boolean }) {
  const [alerts, setAlerts] = useState<StoreAlert[] | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "error">("loading");
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    apiRequest(`/api/tenants/${encodeURIComponent(tenantId)}/alerts`, { signal: controller.signal }).then(async response => {
      if (!response.ok) throw new Error();
      const parsed = parseAlerts(await response.json());
      if (!parsed) throw new Error();
      setAlerts(parsed); setState("ready");
    }).catch(() => { if (!controller.signal.aborted) setState("error"); });
    return () => controller.abort();
  }, [tenantId, attempt]);

  return <section className="side-card store-alerts" aria-labelledby="alerts-title" aria-busy={state === "loading"}>
    <h2 id="alerts-title">Alertas</h2>
    {state === "loading" ? <p role="status">Consultando alertas…</p> : null}
    {state === "error" ? <><p role="alert" className="feedback feedback-error">Não foi possível consultar os alertas.</p>
      <button type="button" onClick={() => { setState("loading"); setAttempt(current => current + 1); }}>Consultar alertas novamente</button></> : null}
    {state === "ready" && alerts ? alerts.length === 0
      ? <p>Nenhum alerta no momento.</p>
      : <ul className="alert-list">
        {alerts.map(alert => {
          const link = alertLink(alert, tenantId);
          return <li key={alert.code} className={`alert-item alert-${alert.severity.toLowerCase()}`}>
            <span className="alert-severity">{SEVERITY_TEXT[alert.severity]}</span>
            <span className="alert-text">{alertText(alert)}</span>
            <Link href={link.href}>{link.label}</Link>
            {explain ? <Link className="explain-link" href={explainHref(tenantId, `ALERT_${alert.code}`)} prefetch={false}>
              Explicar<span className="visually-hidden"> o alerta: {alertText(alert)}</span></Link> : null}
          </li>;
        })}
      </ul> : null}
  </section>;
}
