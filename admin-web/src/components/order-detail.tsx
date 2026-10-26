"use client";

import Link from "next/link";
import { type FormEvent, useEffect, useRef, useState } from "react";
import { apiRequest, requestError } from "@/lib/auth-api";
import { formatBRL } from "@/lib/catalog";
import { CANCEL_REASON_MAX, FULFILLMENT_LABELS, type OrderDetail as Order, STATUS_LABELS, cancelReason, formatDateTime, formatPhone, formatPostalCode, PAYMENT_STATUS_LABELS, paymentDivergence, providerStatusLabel } from "@/lib/orders";
import PageHeader from "@/components/page-header";
import StatusBadge from "@/components/status-badge";
import OrderRefund from "@/components/order-refund";
import { Loading } from "@/components/ui/loading";

export default function OrderDetail({ tenantId, orderId }: { tenantId: string; orderId: string }) {
  const [order, setOrder] = useState<Order | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "signed-out" | "missing" | "error">("loading");
  const [attempt, setAttempt] = useState(0);
  const [cancelling, setCancelling] = useState(false);
  const [reason, setReason] = useState("");
  const [reasonError, setReasonError] = useState("");
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{ text: string; error: boolean } | null>(null);
  const submitting = useRef(false);
  const reasonRef = useRef<HTMLInputElement>(null);
  const feedbackRef = useRef<HTMLParagraphElement>(null);
  const cancelButtonRef = useRef<HTMLButtonElement>(null);
  const restoreCancelFocus = useRef(false);
  const storePath = `/lojas/${encodeURIComponent(tenantId)}`;
  const path = `/api/tenants/${encodeURIComponent(tenantId)}/orders/${encodeURIComponent(orderId)}`;

  useEffect(() => {
    const controller = new AbortController();
    apiRequest(path, { signal: controller.signal }).then(async response => {
      if (response.status === 401) { setState("signed-out"); return; }
      if (response.status === 400 || response.status === 403 || response.status === 404) { setState("missing"); return; }
      if (!response.ok) throw new Error();
      const current: Order = await response.json();
      if (controller.signal.aborted) return;
      setOrder(current); setState("ready");
    }).catch(() => { if (!controller.signal.aborted) setState("error"); });
    return () => controller.abort();
  }, [path, attempt]);

  useEffect(() => { if (feedback) feedbackRef.current?.focus(); }, [feedback]);
  useEffect(() => {
    if (cancelling) reasonRef.current?.focus();
    // Closing the form unmounts the focused button: send focus back to the control that opened it.
    else if (restoreCancelFocus.current) { restoreCancelFocus.current = false; cancelButtonRef.current?.focus(); }
  }, [cancelling]);

  async function cancel(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current) return;
    const checked = cancelReason(reason);
    if ("error" in checked) { setReasonError(checked.error); reasonRef.current?.focus(); return; }
    setReasonError(""); setFeedback(null);
    submitting.current = true; setBusy(true);
    try {
      const response = await apiRequest(`${path}/cancel`, { method: "POST", body: { reason: checked.reason } });
      if (response.ok) {
        setOrder(await response.json() as Order); setCancelling(false); setReason("");
        setFeedback({ text: "Pedido cancelado. As unidades reservadas voltaram ao estoque disponível.", error: false });
        return;
      }
      if (response.status === 409) { setCancelling(false); setFeedback({ text: "O prazo deste pedido já terminou e as reservas foram liberadas. Não é preciso cancelar.", error: true }); setAttempt(current => current + 1); return; }
      if (response.status === 401) { setFeedback({ text: "Sua sessão expirou. Entre novamente antes de cancelar o pedido.", error: true }); return; }
      if (response.status === 404) { setFeedback({ text: "Pedido indisponível para esta solicitação. Volte para a lista de pedidos.", error: true }); return; }
      if (response.status === 400) { setReasonError("Informe o motivo em uma linha, com 3 a 300 caracteres."); reasonRef.current?.focus(); return; }
      setFeedback({ text: requestError(response.status), error: true });
    } catch {
      setFeedback({ text: "Não foi possível confirmar o cancelamento. Consulte o pedido novamente antes de tentar outra vez.", error: true });
    } finally { submitting.current = false; setBusy(false); }
  }

  const title = order ? `Pedido nº ${order.number}` : "Pedido";
  return <section className="page" aria-labelledby="order-title">
    <PageHeader id="order-title" title={title} breadcrumbs={[{ href: `${storePath}/pedidos`, label: "Pedidos" }]}
      description={order ? <>Criado em {formatDateTime(order.createdAt)} (horário de Brasília)</> : undefined} />
    {state === "loading" ? <Loading context="pedido" /> : null}
    {state === "signed-out" ? <><p role="status">Sua sessão expirou. Entre novamente para acessar o pedido.</p><Link href="/entrar">Entrar</Link></> : null}
    {state === "missing" ? <p role="status">Pedido indisponível. Volte para a lista de pedidos da sua loja.</p> : null}
    {state === "error" ? <><p role="alert" className="feedback feedback-error">Não foi possível consultar o pedido. Tente novamente.</p><button type="button" onClick={() => { setState("loading"); setAttempt(current => current + 1); }}>Consultar pedido novamente</button></> : null}
    {feedback ? <p ref={feedbackRef} tabIndex={-1} role={feedback.error ? "alert" : "status"} className={`feedback ${feedback.error ? "feedback-error" : "feedback-success"}`}>{feedback.text}</p> : null}
    {state === "ready" && order ? <div className="order-workspace">
      <div className="order-main">
        <section className="side-card" aria-labelledby="order-status-title">
          <h2 id="order-status-title">Situação</h2>
          <p><StatusBadge tone={order.status === "PENDING_PAYMENT" || order.status === "PAID" ? "positive" : "neutral"}>{STATUS_LABELS[order.status]}</StatusBadge></p>
          {order.status === "PAID" ? <p>Pagamento confirmado pelo Mercado Pago em {formatDateTime(order.paidAt)}.{order.paidWithoutStock ? "" : " As unidades foram baixadas do estoque."}</p> : null}
          {order.paidWithoutStock && order.status === "PAID" ? <p className="feedback feedback-error">Pago sem estoque: o pagamento chegou depois do fim da reserva e não havia unidades suficientes. Nada foi baixado do estoque. Reponha as unidades ou fale com o comprador; o reembolso não é automático.</p> : null}
          {order.status === "PENDING_PAYMENT" ? <p>Estoque reservado até {formatDateTime(order.reservationExpiresAt)}. Sem pagamento confirmado, a reserva termina sozinha nesse horário.</p> : null}
          {order.status === "EXPIRED" && !order.paidAt ? <p>A reserva terminou{order.expiredAt ? ` em ${formatDateTime(order.expiredAt)}` : ` às ${formatDateTime(order.reservationExpiresAt)}`} e as unidades voltaram ao estoque disponível.</p> : null}
          {order.cancellation ? <dl className="order-facts"><div><dt>Cancelado em</dt><dd>{formatDateTime(order.cancellation.cancelledAt)}</dd></div><div><dt>Motivo (visível só para a loja)</dt><dd>{order.cancellation.reason}</dd></div></dl> : null}
        </section>
        <div className="data-table-wrap"><table className="data-table data-table-wrapping" role="table">
          <caption>Itens do pedido</caption>
          <thead role="rowgroup"><tr role="row"><th role="columnheader" scope="col">Produto</th><th role="columnheader" scope="col">SKU</th><th role="columnheader" scope="col" className="numeric">Preço</th><th role="columnheader" scope="col" className="numeric">Custo</th><th role="columnheader" scope="col" className="numeric">Quantidade</th><th role="columnheader" scope="col" className="numeric">Total</th></tr></thead>
          <tbody role="rowgroup">{order.items.map((item, index) => <tr role="row" key={index}>
            <th role="rowheader" scope="row">{item.productName}{item.options.length ? <span className="field-help"> · {item.options.map(option => `${option.name}: ${option.value}`).join(" · ")}</span> : null}</th>
            <td role="cell" data-label="SKU"><span translate="no">{item.sku}</span></td>
            <td role="cell" data-label="Preço" className="numeric">{formatBRL(item.unitPrice)}</td>
            <td role="cell" data-label="Custo" className="numeric">{item.unitCost ? formatBRL(item.unitCost) : "Não informado"}</td>
            <td role="cell" data-label="Quantidade" className="numeric">{item.quantity}</td>
            <td role="cell" data-label="Total" className="numeric">{formatBRL(item.lineTotal)}</td>
          </tr>)}</tbody>
        </table></div>
      </div>
      <aside className="order-aside">
        <section className="side-card" aria-labelledby="order-values-title">
          <h2 id="order-values-title">Valores</h2>
          <dl className="order-facts">
            <div><dt>Itens</dt><dd className="numeric">{formatBRL(order.subtotal)}</dd></div>
            <div><dt>Entrega</dt><dd className="numeric">{order.shipping === "0.00" ? "Sem custo" : formatBRL(order.shipping)}</dd></div>
            <div><dt>Total</dt><dd className="numeric"><strong>{formatBRL(order.total)}</strong></dd></div>
          </dl>
        </section>
        {order.payment ? <section className="side-card" aria-labelledby="order-payment-title">
          <h2 id="order-payment-title">Pagamento</h2>
          <p>{PAYMENT_STATUS_LABELS[order.payment.status] ?? "Situação desconhecida"}</p>
          <dl className="order-facts">
            {order.payment.providerOrderId ? <div><dt>Pedido no Mercado Pago</dt><dd><span translate="no">{order.payment.providerOrderId}</span></dd></div> : null}
            <div><dt>Valor cobrado</dt><dd className="numeric">{formatBRL(order.payment.amount)}</dd></div>
            <div><dt>Valor pago</dt><dd className="numeric">{order.payment.paidAmount ? formatBRL(order.payment.paidAmount) : "—"}</dd></div>
            <div><dt>Taxa e líquido</dt><dd>Não informados pelo Mercado Pago</dd></div>
            {order.payment.providerStatus ? <div><dt>Situação no Mercado Pago</dt><dd>{providerStatusLabel(order.payment.providerStatus, order.payment.providerStatusDetail)} <span className="field-help" translate="no">({order.payment.providerStatus}{order.payment.providerStatusDetail ? ` / ${order.payment.providerStatusDetail}` : ""})</span></dd></div> : null}
            <div><dt>Última consulta</dt><dd>{formatDateTime(order.payment.checkedAt)}</dd></div>
          </dl>
          {paymentDivergence(order.payment.lastCheckError) ? <p className="feedback feedback-error">{paymentDivergence(order.payment.lastCheckError)}</p> : null}
        </section> : null}
        <section className="side-card" aria-labelledby="order-buyer-title">
          <h2 id="order-buyer-title">Comprador e entrega</h2>
          {order.buyer ? <dl className="order-facts">
            <div><dt>Nome</dt><dd>{order.buyer.name}</dd></div>
            <div><dt>E-mail</dt><dd><a href={`mailto:${order.buyer.email}`}>{order.buyer.email}</a></dd></div>
            <div><dt>Telefone</dt><dd><a href={`tel:+55${order.buyer.phone.replace(/^\+?55/, "")}`}>{formatPhone(order.buyer.phone)}</a></dd></div>
          </dl> : <p>Os dados pessoais do comprador foram removidos em {formatDateTime(order.anonymizedAt)}, conforme a política de retenção.</p>}
          <p><strong>{FULFILLMENT_LABELS[order.fulfillment.type]}:</strong> {order.fulfillment.text}</p>
          {order.address ? <address className="order-address">{order.address.line1}{order.address.line2 ? `, ${order.address.line2}` : ""}<br />{order.address.district} · {order.address.city}/{order.address.state}<br />CEP {formatPostalCode(order.address.postalCode)}</address> : null}
        </section>
        <OrderRefund order={order} path={path} onChange={setOrder} onReload={() => setAttempt(current => current + 1)} onNotice={(text, error) => setFeedback({ text, error })} />
        {order.status === "PENDING_PAYMENT" ? <section className="side-card" aria-labelledby="order-cancel-title">
          <h2 id="order-cancel-title">Cancelar pedido</h2>
          {!cancelling ? <><p>O cancelamento libera as unidades reservadas na hora e não pode ser desfeito.</p>
            <button ref={cancelButtonRef} type="button" className="button-danger" onClick={() => { setFeedback(null); setCancelling(true); }}>Cancelar pedido</button></>
          : <form onSubmit={event => void cancel(event)} noValidate>
            <label htmlFor="cancel-reason">Motivo do cancelamento</label>
            <p id="cancel-reason-help" className="field-help">Visível só para a loja. De 3 a {CANCEL_REASON_MAX} caracteres. O comprador verá apenas “Pedido cancelado”.</p>
            <input ref={reasonRef} id="cancel-reason" name="reason" value={reason} maxLength={CANCEL_REASON_MAX * 2} autoComplete="off"
              onChange={event => { setReason(event.target.value); setReasonError(""); }}
              aria-invalid={reasonError ? true : undefined} aria-describedby={reasonError ? "cancel-reason-help cancel-reason-error" : "cancel-reason-help"} />
            {reasonError ? <p id="cancel-reason-error" className="field-error">{reasonError}</p> : null}
            <div className="form-actions">
              <button type="submit" className="button-danger" disabled={busy}>{busy ? "Cancelando…" : "Confirmar cancelamento"}</button>
              <button type="button" className="button-secondary" disabled={busy} onClick={() => { restoreCancelFocus.current = true; setCancelling(false); setReason(""); setReasonError(""); }}>Manter pedido</button>
            </div>
          </form>}
        </section> : null}
      </aside>
    </div> : null}
  </section>;
}
