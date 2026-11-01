"use client";

import { type FormEvent, useEffect, useRef, useState } from "react";
import { apiRequest, requestError } from "@/lib/auth-api";
import { formatBRL } from "@/lib/catalog";
import { CANCEL_REASON_MAX, type OrderDetail as Order, cancelReason, formatDateTime } from "@/lib/orders";

// Results are reported through the page's own notice (onNotice), so they survive a reload of the
// order and keep the error or success role of what they say (SC-017 review).
type Props = { order: Order; path: string; onChange: (order: Order) => void; onReload: () => void; onNotice: (text: string, error: boolean) => void };
type Step = "closed" | "form" | "confirm";

// SC-016: full refund of a paid order, in two steps: the reason and an explicit stock choice, then
// a summary to confirm. The order only reads "Reembolsado" once the backend has the provider's
// confirmation; until then the refund is shown as in progress.
export default function OrderRefund({ order, path, onChange, onReload, onNotice }: Props) {
  const [step, setStep] = useState<Step>("closed");
  const [reason, setReason] = useState("");
  const [restock, setRestock] = useState<"yes" | "no" | "">("");
  const [errors, setErrors] = useState<{ reason?: string; restock?: string }>({});
  const [busy, setBusy] = useState(false);
  const submitting = useRef(false);
  const openRef = useRef<HTMLButtonElement>(null);
  const retryRef = useRef<HTMLButtonElement>(null);
  const reasonRef = useRef<HTMLInputElement>(null);
  const confirmRef = useRef<HTMLHeadingElement>(null);
  // Closing a step unmounts the focused control: focus returns to the one that opened it.
  const restoreFocus = useRef(false);
  const refund = order.payment?.refund ?? null;
  const stockless = order.paidWithoutStock;

  useEffect(() => {
    if (step === "form") reasonRef.current?.focus();
    if (step === "confirm") confirmRef.current?.focus();
    if (step === "closed" && restoreFocus.current) {
      restoreFocus.current = false;
      (retryRef.current ?? openRef.current)?.focus();
    }
  }, [step]);

  function close() {
    restoreFocus.current = true;
    setStep("closed");
  }

  function review(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const checked = cancelReason(reason);
    const next: typeof errors = {};
    if ("error" in checked) next.reason = checked.error.replace("do cancelamento", "do reembolso");
    if (!restock) next.restock = "Escolha se as unidades voltam ao estoque.";
    setErrors(next);
    if (next.reason) { reasonRef.current?.focus(); return; }
    if (next.restock) { document.getElementById("refund-restock-yes")?.focus(); return; }
    setStep("confirm");
  }

  async function submit() {
    if (submitting.current) return;
    const checked = cancelReason(reason);
    if ("error" in checked || !restock) { setStep("form"); return; }
    submitting.current = true; setBusy(true);
    try {
      const response = await apiRequest(`${path}/refund`, { method: "POST", body: { reason: checked.reason, restock: restock === "yes" } });
      if (response.ok) {
        const updated = await response.json() as Order;
        onChange(updated); setStep("closed"); setReason(""); setRestock("");
        onNotice(updated.status === "REFUNDED"
          ? "Reembolso confirmado pelo Mercado Pago."
          : "Reembolso solicitado ao Mercado Pago. O pedido muda para “Reembolsado” quando o Mercado Pago confirmar.", false);
        return;
      }
      let message: string | null = null;
      try { message = (await response.json() as { message?: string }).message ?? null; } catch { message = null; }
      if (response.status === 401) { onNotice("Sua sessão expirou. Entre novamente antes de reembolsar.", true); return; }
      if (response.status === 409 || response.status === 502) { setStep("closed"); onNotice(message ?? requestError(response.status), true); onReload(); return; }
      if (response.status === 400) { setStep("form"); setErrors({ reason: "Informe o motivo em uma linha, com 3 a 300 caracteres." }); return; }
      if (response.status === 404) { onNotice("Pedido indisponível para esta solicitação. Volte para a lista de pedidos.", true); return; }
      onNotice(requestError(response.status), true);
    } catch {
      onNotice("Não foi possível confirmar o pedido de reembolso. Consulte o pedido novamente antes de tentar outra vez: o mesmo reembolso será reaproveitado.", true);
    } finally { submitting.current = false; setBusy(false); }
  }

  if (order.status === "REFUNDED") return <section className="side-card" aria-labelledby="order-refund-title">
    <h2 id="order-refund-title">Reembolso</h2>
    <p>Reembolso total confirmado pelo Mercado Pago em {formatDateTime(order.refundedAt)}.</p>
    {refund ? <dl className="order-facts">
      <div><dt>Motivo (visível só para a loja)</dt><dd>{refund.reason}</dd></div>
      <div><dt>Estoque</dt><dd>{refund.restock ? (stockless ? "Nada a devolver: o pedido foi pago sem estoque" : "Unidades devolvidas ao estoque") : "Unidades não devolvidas ao estoque"}</dd></div>
      <div><dt>Solicitado em</dt><dd>{formatDateTime(refund.requestedAt)}</dd></div>
    </dl> : null}
  </section>;
  if (order.status !== "PAID") return null;

  return <section className="side-card" aria-labelledby="order-refund-title">
    <h2 id="order-refund-title">Reembolso</h2>
    {refund ? <>
      <p>Reembolso total solicitado em {formatDateTime(refund.requestedAt)}. Aguardando a confirmação do Mercado Pago; o pedido muda para “Reembolsado” assim que ela chegar.</p>
      <dl className="order-facts">
        <div><dt>Motivo (visível só para a loja)</dt><dd>{refund.reason}</dd></div>
        <div><dt>Estoque</dt><dd>{refund.restock ? "Devolver as unidades ao estoque" : "Não devolver as unidades ao estoque"}</dd></div>
      </dl>
      {refund.lastError ? <><p className="feedback feedback-error">O Mercado Pago não confirmou o pedido de reembolso. Tente novamente: o mesmo reembolso será reaproveitado, sem cobrança em dobro.</p>
        <button ref={retryRef} type="button" disabled={busy} onClick={() => { setReason(refund.reason); setRestock(refund.restock ? "yes" : "no"); setStep("confirm"); }}>Tentar o reembolso novamente</button></> : null}
    </> : null}
    {!refund && step === "closed" ? <>
      <p>Devolve ao comprador o valor total de {formatBRL(order.total)} pelo Mercado Pago. Não pode ser desfeito.</p>
      <button ref={openRef} type="button" className="button-danger" onClick={() => setStep("form")}>Reembolsar pedido</button>
    </> : null}
    {step === "form" ? <form onSubmit={review} noValidate>
      <label htmlFor="refund-reason">Motivo do reembolso</label>
      <p id="refund-reason-help" className="field-help">Visível só para a loja. De 3 a {CANCEL_REASON_MAX} caracteres.</p>
      <input ref={reasonRef} id="refund-reason" name="reason" value={reason} maxLength={CANCEL_REASON_MAX * 2} autoComplete="off"
        onChange={event => { setReason(event.target.value); setErrors(current => ({ ...current, reason: undefined })); }}
        aria-invalid={errors.reason ? true : undefined} aria-describedby={errors.reason ? "refund-reason-help refund-reason-error" : "refund-reason-help"} />
      {errors.reason ? <p id="refund-reason-error" className="field-error">{errors.reason}</p> : null}
      <fieldset aria-describedby={errors.restock ? "refund-restock-help refund-restock-error" : "refund-restock-help"}>
        <legend>Estoque</legend>
        <p id="refund-restock-help" className="field-help">{stockless ? "Este pedido foi pago sem estoque: nada foi baixado, então nada volta ao estoque." : "Escolha se as unidades vendidas voltam ao estoque disponível."}</p>
        <label className="check-field"><input id="refund-restock-yes" type="radio" name="restock" value="yes" checked={restock === "yes"} aria-describedby={errors.restock ? "refund-restock-error" : undefined} onChange={() => { setRestock("yes"); setErrors(current => ({ ...current, restock: undefined })); }} /> Devolver as unidades ao estoque</label>
        <label className="check-field"><input type="radio" name="restock" value="no" checked={restock === "no"} aria-describedby={errors.restock ? "refund-restock-error" : undefined} onChange={() => { setRestock("no"); setErrors(current => ({ ...current, restock: undefined })); }} /> Não devolver as unidades ao estoque</label>
        {errors.restock ? <p id="refund-restock-error" className="field-error" role="alert">{errors.restock}</p> : null}
      </fieldset>
      <div className="form-actions">
        <button type="submit">Revisar reembolso</button>
        <button type="button" className="button-secondary" onClick={() => { setReason(""); setRestock(""); setErrors({}); close(); }}>Manter pedido</button>
      </div>
    </form> : null}
    {step === "confirm" ? <div role="group" aria-labelledby="refund-confirm-title">
      <h3 id="refund-confirm-title" ref={confirmRef} tabIndex={-1}>Confirme o reembolso</h3>
      <dl className="order-facts">
        <div><dt>Valor devolvido ao comprador</dt><dd className="numeric"><strong>{formatBRL(order.total)}</strong></dd></div>
        <div><dt>Estoque</dt><dd>{restock === "yes" ? "Devolver as unidades ao estoque" : "Não devolver as unidades ao estoque"}</dd></div>
        <div><dt>Motivo</dt><dd>{reason.trim()}</dd></div>
      </dl>
      <div className="form-actions">
        <button type="button" className="button-danger" disabled={busy} onClick={() => void submit()}>{busy ? "Solicitando reembolso…" : "Confirmar reembolso"}</button>
        <button type="button" className="button-secondary" disabled={busy} onClick={() => (refund ? close() : setStep("form"))}>Voltar</button>
      </div>
    </div> : null}
  </section>;
}
