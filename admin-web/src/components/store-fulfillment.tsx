"use client";

import { type FormEvent, useEffect, useRef, useState } from "react";
import { apiRequest, requestError } from "@/lib/auth-api";
import { formatBRL } from "@/lib/catalog";
import { type Fulfillment, type FulfillmentErrors, type FulfillmentFields, FULFILLMENT_TEXT_LIMIT, fulfillmentFields, fulfillmentPayload } from "@/lib/fulfillment";

type TextField = keyof FulfillmentErrors;

export default function StoreFulfillment({ tenantId }: { tenantId: string }) {
  const [saved, setSaved] = useState<Fulfillment | null>(null);
  const [fields, setFields] = useState<FulfillmentFields | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "error">("loading");
  const [attempt, setAttempt] = useState(0);
  const [busy, setBusy] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [errors, setErrors] = useState<FulfillmentErrors>({});
  const [feedback, setFeedback] = useState<{ text: string; error: boolean } | null>(null);
  const submitting = useRef(false);
  const pendingErrorFocus = useRef<TextField | null>(null);
  const formRef = useRef<HTMLFormElement>(null);
  const feedbackRef = useRef<HTMLParagraphElement>(null);
  const path = `/api/tenants/${encodeURIComponent(tenantId)}/fulfillment`;
  const dirty = !!saved && !!fields && JSON.stringify(fields) !== JSON.stringify(fulfillmentFields(saved));

  useEffect(() => {
    const controller = new AbortController();
    apiRequest(path, { signal: controller.signal }).then(async response => {
      if (!response.ok) throw new Error();
      const current: Fulfillment = await response.json();
      setSaved(current); setFields(fulfillmentFields(current)); setConflict(false); setState("ready");
    }).catch(() => { if (!controller.signal.aborted) setState("error"); });
    return () => controller.abort();
  }, [path, attempt]);

  useEffect(() => { if (feedback) feedbackRef.current?.focus(); }, [feedback]);
  useEffect(() => {
    if (busy || !pendingErrorFocus.current) return;
    const field = pendingErrorFocus.current;
    pendingErrorFocus.current = null;
    formRef.current?.querySelector<HTMLElement>(`[name="${field}"]`)?.focus();
  }, [busy, errors]);

  function update<K extends keyof FulfillmentFields>(field: K, value: FulfillmentFields[K]) {
    setFields(current => current ? { ...current, [field]: value } : current);
    setErrors(current => ({ ...current, [field]: undefined }));
  }

  function reload() {
    if (dirty && !window.confirm("Carregar a configuração atual descartará suas alterações não salvas. Deseja continuar?")) return;
    setFeedback(null); setErrors({}); setState("loading"); setAttempt(current => current + 1);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!saved || !fields || submitting.current || conflict) return;
    const result = fulfillmentPayload(fields, saved.version);
    setFeedback(null);
    if ("errors" in result) {
      setErrors(result.errors);
      pendingErrorFocus.current = (["pickupInstructions", "fixedShippingAmount", "fixedShippingArea"] as const).find(field => result.errors[field]) ?? null;
      return;
    }
    setErrors({});
    submitting.current = true; setBusy(true);
    try {
      const response = await apiRequest(path, { method: "PUT", body: result.body });
      if (response.status === 409) { setConflict(true); setFeedback({ text: "A entrega foi alterada em outra sessão. Nada foi sobrescrito. Carregue a configuração atual antes de editar novamente.", error: true }); return; }
      if (response.status === 401) { setFeedback({ text: "Sua sessão expirou. Entre novamente antes de salvar a entrega.", error: true }); return; }
      if (response.status === 404 || response.status === 403) { setFeedback({ text: "Loja indisponível para esta solicitação. Volte para suas lojas ou tente novamente.", error: true }); return; }
      if (response.status === 400) { setFeedback({ text: "Dados inválidos. Confira os textos e o valor do frete e tente novamente.", error: true }); return; }
      if (!response.ok) { setFeedback({ text: requestError(response.status), error: true }); return; }
      const current: Fulfillment = await response.json();
      setSaved(current); setFields(fulfillmentFields(current));
      setFeedback({ text: current.pickupEnabled || current.fixedShippingEnabled ? "Entrega salva." : "Entrega salva. Sem opção ativa, a loja não poderá receber pedidos quando as compras forem habilitadas.", error: false });
    } catch { setFeedback({ text: "Não foi possível confirmar o salvamento. Carregue a configuração atual antes de tentar novamente.", error: true }); }
    finally { submitting.current = false; setBusy(false); }
  }

  const disabled = busy || conflict;
  const textField = (field: "pickupInstructions" | "fixedShippingArea", label: string, hint: string) => fields ? <div className="form-field">
    <label htmlFor={`fulfillment-${field}`}>{label}</label>
    <textarea id={`fulfillment-${field}`} name={field} rows={3} value={fields[field]} onChange={event => update(field, event.target.value)}
      maxLength={FULFILLMENT_TEXT_LIMIT * 2} spellCheck disabled={disabled} aria-invalid={!!errors[field]}
      aria-describedby={`fulfillment-${field}-help${errors[field] ? ` fulfillment-${field}-error` : ""}`} />
    <p id={`fulfillment-${field}-help`} className="field-help">{hint}</p>
    {errors[field] ? <p id={`fulfillment-${field}-error`} className="field-error">{errors[field]}</p> : null}
  </div> : null;

  return <section className="store-create" aria-labelledby="fulfillment-title" aria-busy={state === "loading" || busy}>
    <h2 id="fulfillment-title">Entrega</h2>
    <p>Escolha como o comprador recebe o pedido. Opções ativas, valor e textos ficam visíveis na vitrine publicada; opções inativas não aparecem.</p>
    {state === "loading" ? <p role="status">Consultando a configuração de entrega…</p> : null}
    {state === "error" ? <><p role="alert" className="feedback feedback-error">Não foi possível consultar a configuração de entrega.</p>
      <button type="button" onClick={() => { setState("loading"); setAttempt(current => current + 1); }}>Consultar entrega novamente</button></> : null}
    {state === "ready" && fields && saved ? <form ref={formRef} className="fulfillment-form" onSubmit={(event) => void submit(event)} noValidate aria-busy={busy}>
      <fieldset className="variant-definition">
        <legend>Retirada na loja</legend>
        <label className="check-field"><input type="checkbox" name="pickupEnabled" checked={fields.pickupEnabled} disabled={disabled} onChange={event => update("pickupEnabled", event.target.checked)} /> Oferecer retirada</label>
        {textField("pickupInstructions", "Endereço e instruções de retirada", `Obrigatório para ativar. Até ${FULFILLMENT_TEXT_LIMIT} caracteres, em uma única linha. Exemplo: Rua das Flores, 10 — seg. a sex., 9h às 18h.`)}
      </fieldset>
      <fieldset className="variant-definition">
        <legend>Frete fixo</legend>
        <label className="check-field"><input type="checkbox" name="fixedShippingEnabled" checked={fields.fixedShippingEnabled} disabled={disabled} onChange={event => update("fixedShippingEnabled", event.target.checked)} /> Oferecer entrega com frete fixo</label>
        <div className="form-field">
          <label htmlFor="fulfillment-fixedShippingAmount">Valor do frete (R$)</label>
          <input id="fulfillment-fixedShippingAmount" name="fixedShippingAmount" type="text" inputMode="decimal" autoComplete="off" maxLength={20}
            value={fields.fixedShippingAmount} onChange={event => update("fixedShippingAmount", event.target.value)} disabled={disabled}
            aria-invalid={!!errors.fixedShippingAmount} aria-describedby={`fulfillment-fixedShippingAmount-help${errors.fixedShippingAmount ? " fulfillment-fixedShippingAmount-error" : ""}`} />
          <p id="fulfillment-fixedShippingAmount-help" className="field-help">Valor único por pedido. Use 0 para frete grátis. Exemplo: 12,50. Sem separador de milhares.</p>
          {errors.fixedShippingAmount ? <p id="fulfillment-fixedShippingAmount-error" className="field-error">{errors.fixedShippingAmount}</p> : null}
        </div>
        {textField("fixedShippingArea", "Área atendida", `Obrigatória para ativar. Informativa: o sistema não confere CEP e a vitrine avisa isso ao comprador. Até ${FULFILLMENT_TEXT_LIMIT} caracteres.`)}
      </fieldset>
      <p className="field-help">{saved.pickupEnabled || saved.fixedShippingEnabled
        ? `Configuração salva: ${[saved.pickupEnabled ? "retirada" : null, saved.fixedShippingEnabled && saved.fixedShippingAmount ? `frete fixo de ${formatBRL(saved.fixedShippingAmount)}` : null].filter(Boolean).join(" e ")}.`
        : "Nenhuma opção de entrega ativa salva."}</p>
      {feedback ? <p ref={feedbackRef} tabIndex={-1} role={feedback.error ? "alert" : "status"} className={`feedback feedback-${feedback.error ? "error" : "success"}`}>{feedback.text}</p> : null}
      <div className="publication-actions">
        {conflict ? <button type="button" onClick={reload}>Carregar configuração atual</button>
          : <button type="submit" disabled={busy}>{busy ? "Salvando entrega…" : "Salvar entrega"}</button>}
        {!conflict ? <button type="button" disabled={busy} onClick={reload}>Recarregar entrega</button> : null}
      </div>
    </form> : null}
  </section>;
}
