"use client";

import { useActionState, useEffect, useRef } from "react";
import { payOrder, type PaymentState } from "@/lib/payment-actions";

type Props = { slug: string; orderId: string; label: string };

// Opens Checkout Pro for a pending order (SC-013). Works without JavaScript through the server
// action; with it, the button is blocked while the request runs and failures are announced.
export function PaymentForm({ slug, orderId, label }: Props) {
  const [state, action, pending] = useActionState(payOrder, { message: null, attempt: 0 } satisfies PaymentState);
  const notice = useRef<HTMLParagraphElement>(null);
  useEffect(() => {
    if (state.attempt > 0 && state.message) notice.current?.focus();
  }, [state.attempt, state.message]);
  return (
    <form action={action} className="payment-form">
      <input type="hidden" name="slug" value={slug} />
      <input type="hidden" name="orderId" value={orderId} />
      {state.message ? <p ref={notice} className="checkout-alert" role="alert" tabIndex={-1}>{state.message}</p> : null}
      <button type="submit" disabled={pending} aria-describedby="payment-note">{pending ? "Abrindo o Mercado Pago…" : label}</button>
      <p id="payment-note" className="cart-note">Você será levado ao Mercado Pago para pagar com cartão, Pix ou saldo da conta. A confirmação chega à loja pelo Mercado Pago, não por esta página.</p>
    </form>
  );
}
