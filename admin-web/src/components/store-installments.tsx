"use client";

import { type FormEvent, useEffect, useRef, useState } from "react";
import { apiRequest } from "@/lib/auth-api";
import { installmentsError, installmentsText, parseInstallments } from "@/lib/payments";
import { Loading } from "@/components/ui/loading";

// Sprint 8 (SC-042, ADR-0018 V4): the most card installments the store offers in Checkout Pro.
// The storefront shows "em até N× no Mercado Pago" without promising "sem juros": interest follows
// the seller's own Mercado Pago account, which the platform does not see.
export default function StoreInstallments({ tenantId }: { tenantId: string }) {
  const [value, setValue] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ text: string; error: boolean } | null>(null);
  const submitting = useRef(false);
  const api = `/api/tenants/${encodeURIComponent(tenantId)}/payments/installments`;

  useEffect(() => {
    const controller = new AbortController();
    apiRequest(api, { signal: controller.signal }).then(async response => {
      const parsed = response.ok ? parseInstallments(await response.json()) : undefined;
      if (parsed === undefined) throw new Error();
      if (!controller.signal.aborted) setValue(parsed === null ? "" : String(parsed));
    }).catch(() => { if (!controller.signal.aborted) setFailed(true); });
    return () => controller.abort();
  }, [api, attempt]);

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current || value === null) return;
    submitting.current = true; setBusy(true); setMessage(null);
    try {
      const response = await apiRequest(api, { method: "PUT", body: { maxInstallments: value ? Number(value) : null } });
      if (!response.ok) { setMessage({ text: installmentsError(response.status), error: true }); return; }
      const saved = parseInstallments(await response.json());
      setValue(saved === null || saved === undefined ? "" : String(saved));
      setMessage({ text: `Parcelamento salvo: ${installmentsText(saved ?? null)}.`, error: false });
    } catch { setMessage({ text: "Não foi possível confirmar. Recarregue a página.", error: true }); }
    finally { submitting.current = false; setBusy(false); }
  }

  return <section className="variant-definition" aria-labelledby="installments-title">
    <h2 id="installments-title">Parcelamento</h2>
    {failed ? <div><p role="alert" className="feedback feedback-error">Não foi possível consultar o parcelamento.</p>
      <button type="button" className="button-secondary" onClick={() => { setFailed(false); setAttempt(current => current + 1); }}>Consultar novamente</button></div> : null}
    {value === null && !failed ? <Loading context="parcelamento" className="py-4" /> : null}
    {value !== null ? <form onSubmit={event => void save(event)} noValidate className="category-edit">
      <div className="form-field">
        <label htmlFor="max-installments">Parcelas no cartão</label>
        <p id="max-installments-help" className="field-help">A vitrine mostra “em até N× no Mercado Pago”. Os juros seguem a configuração da sua conta Mercado Pago; por isso a vitrine não promete “sem juros”.</p>
        <select id="max-installments" value={value} disabled={busy} onChange={event => setValue(event.target.value)} aria-describedby="max-installments-help">
          <option value="">Padrão do Mercado Pago (não mostrar na vitrine)</option>
          {Array.from({ length: 12 }, (_, index) => index + 1).map(count => <option key={count} value={count}>{capitalized(installmentsText(count))}</option>)}
        </select>
      </div>
      <button type="submit" disabled={busy}>{busy ? "Salvando…" : "Salvar parcelamento"}</button>
    </form> : null}
    {message ? <p role={message.error ? "alert" : "status"} className={`feedback feedback-${message.error ? "error" : "success"}`}>{message.text}</p> : null}
  </section>;
}

function capitalized(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
}
