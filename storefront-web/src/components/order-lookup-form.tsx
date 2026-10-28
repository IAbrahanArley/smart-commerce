"use client";

import { useActionState, useEffect, useRef } from "react";
import { findOrder } from "@/lib/order-lookup-action";
import { EMPTY_LOOKUP, type LookupField } from "@/lib/order-lookup";
import { ORDER_STATUS, OrderDeadline, OrderSummary } from "@/components/order-summary";
import { SubmitButton } from "@/components/submit-button";

// SC-046: number and e-mail in a POST form (server action); the answer replaces the form state.
// Focus moves to the result or to the explanation, so screen readers announce it.
export function OrderLookupForm({ slug }: { slug: string }) {
  const [state, action] = useActionState(findOrder, EMPTY_LOOKUP);
  const result = useRef<HTMLDivElement>(null);
  const { values, errors, order } = state;
  const invalid = Object.keys(errors) as LookupField[];

  useEffect(() => {
    if (state.attempt > 0) result.current?.focus();
  }, [state.attempt]);

  const describedBy = (name: LookupField, hint: string) => [hint, errors[name] ? `lookup-${name}-error` : null].filter(Boolean).join(" ");
  return <>
    <form action={action} className="checkout-form lookup-form" noValidate>
      <input type="hidden" name="slug" value={slug} />
      <div className="checkout-field">
        <label htmlFor="lookup-number">Número do pedido</label>
        <p id="lookup-number-hint" className="field-hint">Aparece na página do pedido, por exemplo “Pedido nº 1024”.</p>
        <input id="lookup-number" name="number" inputMode="numeric" autoComplete="off" defaultValue={values.number} maxLength={20}
          aria-invalid={errors.number ? true : undefined} aria-describedby={describedBy("number", "lookup-number-hint")} />
        {errors.number ? <p id="lookup-number-error" className="field-error">{errors.number}</p> : null}
      </div>
      <div className="checkout-field">
        <label htmlFor="lookup-email">E-mail usado na compra</label>
        <p id="lookup-email-hint" className="field-hint">Letras maiúsculas e espaços não fazem diferença.</p>
        <input id="lookup-email" name="email" type="email" inputMode="email" autoComplete="email" defaultValue={values.email} maxLength={254} spellCheck={false}
          aria-invalid={errors.email ? true : undefined} aria-describedby={describedBy("email", "lookup-email-hint")} />
        {errors.email ? <p id="lookup-email-error" className="field-error">{errors.email}</p> : null}
      </div>
      <SubmitButton pendingLabel="Consultando…">Consultar pedido</SubmitButton>
    </form>
    <div ref={result} tabIndex={-1} className="lookup-result" role={state.message ? "alert" : order ? "status" : undefined}>
      {state.message ? <div className="checkout-alert">
        <p>{state.message}</p>
        {invalid.length > 0 ? <ul>{invalid.map(name => <li key={name}><a href={`#lookup-${name}`}>{errors[name]}</a></li>)}</ul> : null}
      </div> : null}
      {order ? <section className="order-status" aria-labelledby="lookup-order-title">
        <h2 id="lookup-order-title">Pedido nº {order.number}: {ORDER_STATUS[order.status].label}</h2>
        <p>{ORDER_STATUS[order.status].text}</p>
        <OrderDeadline order={order} />
        {order.status === "PENDING_PAYMENT" ? <p>Para pagar, use o navegador em que o pedido foi feito, ou fale com a loja.</p> : null}
        <OrderSummary order={order} headingLevel={3} />
      </section> : null}
    </div>
  </>;
}
