"use client";

import Link from "next/link";
import { useActionState, useEffect, useRef } from "react";
import { placeOrder } from "@/lib/checkout-actions";
import type { CheckoutField } from "@/lib/checkout-input";
import { emptyValues } from "@/lib/checkout-input";
import type { CheckoutState } from "@/lib/checkout-state";
import { formatBRL } from "@/lib/price";

export type CheckoutOption = { type: "PICKUP" | "FIXED_SHIPPING"; text: string; fee: string; total: string };
type Props = { slug: string; storeName: string; requestId: string; options: CheckoutOption[] };

const LABELS = { PICKUP: "Retirar na loja", FIXED_SHIPPING: "Receber em casa (frete fixo)" };

export function CheckoutForm({ slug, storeName, requestId, options }: Props) {
  const initial: CheckoutState = { requestId, values: { ...emptyValues(), fulfillment: options.length === 1 ? options[0].type : "" }, errors: {}, message: null, link: null, attempt: 0 };
  const [state, action, pending] = useActionState(placeOrder, initial);
  const summary = useRef<HTMLDivElement>(null);
  const { values, errors } = state;
  const invalid = Object.keys(errors) as CheckoutField[];
  const replayed = state.link?.href.includes("/pedidos/") ?? false;

  // After a rejected attempt, move focus to the explanation so screen readers announce it.
  useEffect(() => {
    if (state.attempt > 0 && state.message) summary.current?.focus();
  }, [state.attempt, state.message]);

  const describedBy = (name: CheckoutField, hint?: string) => [hint, errors[name] ? `${name}-error` : null].filter(Boolean).join(" ") || undefined;
  const input = (name: CheckoutField, label: string, extra: { autoComplete: string; type?: string; inputMode?: "email" | "tel" | "numeric" | "text"; hint?: string; maxLength: number; optional?: boolean }) => (
    <div className="checkout-field">
      <label htmlFor={`checkout-${name}`}>{label}{extra.optional ? <span className="field-optional"> (opcional)</span> : null}</label>
      {extra.hint ? <p id={`${name}-hint`} className="field-hint">{extra.hint}</p> : null}
      <input id={`checkout-${name}`} name={name} type={extra.type ?? "text"} inputMode={extra.inputMode} autoComplete={extra.autoComplete}
        defaultValue={values[name]} maxLength={extra.maxLength} aria-invalid={errors[name] ? true : undefined}
        aria-describedby={describedBy(name, extra.hint ? `${name}-hint` : undefined)} spellCheck={false} />
      {errors[name] ? <p id={`${name}-error`} className="field-error">{errors[name]}</p> : null}
    </div>
  );

  return (
    <form action={action} className="checkout-form" noValidate>
      <input type="hidden" name="slug" value={slug} />
      <input type="hidden" name="requestId" value={state.requestId} />
      {options.map(option => <input key={option.type} type="hidden" name={`total-${option.type}`} value={option.total} />)}

      {/* A replayed order (link to its status page) is good news: informative style and polite status. */}
      <div ref={summary} tabIndex={-1} role={state.message ? (replayed ? "status" : "alert") : undefined}
        className={state.message ? (replayed ? "checkout-alert checkout-alert-info" : "checkout-alert") : undefined}>
        {state.message ? <>
          <p>{state.message}</p>
          {state.link ? <p><a href={state.link.href}>{state.link.label}</a></p> : null}
          {invalid.length > 0 ? <ul>{invalid.map(name => <li key={name}><a href={name === "fulfillment" ? `#checkout-fulfillment-${options[0]?.type ?? ""}` : `#checkout-${name}`}>{errors[name]}</a></li>)}</ul> : null}
        </> : null}
      </div>

      <fieldset id="checkout-delivery" className="checkout-section" aria-describedby={errors.fulfillment ? "fulfillment-error" : undefined}>
        <legend>Entrega</legend>
        {errors.fulfillment ? <p id="fulfillment-error" className="field-error">{errors.fulfillment}</p> : null}
        <div className="delivery-options">
          {options.map(option => (
            <label key={option.type} className="delivery-option">
              <input id={`checkout-fulfillment-${option.type}`} type="radio" name="fulfillment" value={option.type} defaultChecked={values.fulfillment === option.type}
                aria-describedby={errors.fulfillment ? "fulfillment-error" : undefined} />
              <span className="delivery-option-body">
                <span className="delivery-option-title">{LABELS[option.type]}</span>
                <span className="delivery-option-text">{option.text}</span>
                <span className="delivery-option-price">{option.type === "PICKUP" ? "Sem custo de entrega" : option.fee === "0.00" ? "Frete grátis" : `Frete ${formatBRL(option.fee)}`} · Total {formatBRL(option.total)}</span>
              </span>
            </label>
          ))}
        </div>
        {options.some(option => option.type === "FIXED_SHIPPING") ? <p className="field-hint">A área de entrega é informada pela loja; o CEP não é conferido automaticamente.</p> : null}
      </fieldset>

      <fieldset className="checkout-section">
        <legend>Seus dados</legend>
        {input("name", "Nome completo", { autoComplete: "name", maxLength: 120 })}
        {input("email", "E-mail", { autoComplete: "email", type: "email", inputMode: "email", maxLength: 254 })}
        {input("phone", "Telefone com DDD", { autoComplete: "tel", type: "tel", inputMode: "tel", maxLength: 20, hint: "Exemplo: (11) 98888-7777." })}
      </fieldset>

      {options.some(option => option.type === "FIXED_SHIPPING") ? (
        <fieldset className="checkout-section checkout-address">
          <legend>Endereço de entrega</legend>
          <p className="field-hint">Preencha somente se escolher receber em casa.</p>
          {input("postalCode", "CEP", { autoComplete: "postal-code", inputMode: "numeric", maxLength: 9, hint: "8 números, com ou sem hífen." })}
          {input("line1", "Rua e número", { autoComplete: "address-line1", maxLength: 200 })}
          {input("line2", "Complemento", { autoComplete: "address-line2", maxLength: 200, optional: true })}
          {input("district", "Bairro", { autoComplete: "address-level3", maxLength: 100 })}
          {input("city", "Cidade", { autoComplete: "address-level2", maxLength: 100 })}
          {input("state", "Estado (sigla)", { autoComplete: "address-level1", maxLength: 2, hint: "Exemplo: SP." })}
        </fieldset>
      ) : null}

      <p id="checkout-privacy" className="checkout-privacy">
        Seus dados (nome, e-mail, telefone e, se escolher entrega, o endereço) são usados apenas pela <span translate="no">{storeName}</span> para processar e entregar este pedido e para falar com você sobre ele. Eles não são compartilhados com outras lojas. Se o pedido não for pago, seus dados pessoais são anonimizados em até 90 dias. O pagamento é feito no Mercado Pago, que recebe os dados de pagamento diretamente. <Link href="/privacidade" prefetch={false}>Política de privacidade</Link>.
      </p>
      <button type="submit" disabled={pending} aria-describedby="checkout-privacy">{pending ? "Confirmando pedido…" : "Confirmar pedido"}</button>
    </form>
  );
}
