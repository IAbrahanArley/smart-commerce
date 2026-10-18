"use server";

import { randomUUID } from "node:crypto";
import { revalidatePath } from "next/cache";
import { cookies, headers } from "next/headers";
import { notFound, redirect } from "next/navigation";
import { cartCookieName, cartCookieOptions, parseCart, validSlug } from "@/lib/cart-cookie";
import { CHECKOUT_FIELDS, checkoutEnabled, emptyValues, type FulfillmentType, validateCheckout, validRequestId } from "@/lib/checkout-input";
import type { CheckoutState } from "@/lib/checkout-state";
import { orderCookieName, orderCookieOptions, orderPath } from "@/lib/order-cookie";
import { createOrder } from "@/lib/orders";
import { trustedClientIp } from "@/lib/client-ip";

// Runs on the storefront origin (Next rejects cross-origin action posts). The cart comes from the
// HttpOnly cookie, never from the form; prices and totals are confirmed by the backend.

const MESSAGES = {
  fields: "Confira os campos destacados.",
  PRICE_CHANGED: "Os valores mudaram. Confira o resumo atualizado e confirme novamente.",
  INSUFFICIENT_STOCK: "Não há estoque suficiente para algum item. Revise o carrinho antes de confirmar.",
  ITEM_UNAVAILABLE: "Algum item não está mais disponível. Revise o carrinho antes de confirmar.",
  FULFILLMENT_UNAVAILABLE: "A forma de entrega escolhida não está mais disponível. Escolha outra opção.",
  REQUEST_CONFLICT: "Os dados mudaram desde a última tentativa. Confira e confirme novamente.",
  limited: "Muitas tentativas ou pedidos pendentes para este e-mail nesta loja. Tente novamente mais tarde.",
  invalid: "Não foi possível validar os dados do pedido. Confira as informações e tente novamente.",
  missing: "Esta loja não está recebendo pedidos agora.",
  ambiguous: "Não foi possível confirmar o pedido agora. Tente novamente: a mesma tentativa será reaproveitada, sem criar pedido em dobro.",
} as const;

function field(form: FormData, name: string): string {
  const value = form.get(name);
  return typeof value === "string" ? value.slice(0, 1000) : "";
}

export async function placeOrder(previous: CheckoutState, form: FormData): Promise<CheckoutState> {
  if (!checkoutEnabled(process.env.STOREFRONT_CHECKOUT_ENABLED)) notFound();
  const slug = field(form, "slug");
  if (!validSlug(slug)) notFound();
  const submitted = field(form, "requestId");
  const requestId = validRequestId(submitted) ? submitted.toLowerCase() : randomUUID();
  const values = emptyValues();
  for (const name of CHECKOUT_FIELDS) values[name] = field(form, name);
  const state = (patch: Partial<CheckoutState>): CheckoutState => ({ requestId, values, errors: {}, message: null, attempt: previous.attempt + 1, ...patch });

  const jar = await cookies();
  const lines = parseCart(jar.get(cartCookieName(slug))?.value);
  if (lines.length === 0) redirect(`/lojas/${encodeURIComponent(slug)}/carrinho`);
  const totals: Partial<Record<FulfillmentType, string>> = { PICKUP: field(form, "total-PICKUP") || undefined, FIXED_SHIPPING: field(form, "total-FIXED_SHIPPING") || undefined };
  const checked = validateCheckout(values, { requestId, totals });
  if ("errors" in checked) return state({ errors: checked.errors, message: MESSAGES.fields });

  const result = await createOrder(slug, lines, checked.payload, trustedClientIp((await headers()).get("x-forwarded-for"), process.env.STOREFRONT_TRUST_FORWARDED_FOR));
  const production = process.env.NODE_ENV !== "development";
  const clearCart = () => jar.delete({ name: cartCookieName(slug), path: cartCookieOptions(slug, production).path });
  switch (result.kind) {
    case "created":
      jar.set(orderCookieName(result.order.id), result.token, orderCookieOptions(slug, result.order.id, production));
      clearCart();
      redirect(orderPath(encodeURIComponent(slug), result.order.id));
    case "replayed":
      clearCart();
      if (jar.get(orderCookieName(result.order.id))) redirect(orderPath(encodeURIComponent(slug), result.order.id));
      return state({
        message: `Seu pedido nº ${result.order.number} já foi registrado, mas não foi possível recuperar o acompanhamento neste navegador. Guarde este número para falar com a loja.`,
      });
    case "conflict":
      revalidatePath(`/lojas/${slug}/checkout`);
      return state({
        requestId: result.code === "REQUEST_CONFLICT" ? randomUUID() : requestId,
        message: MESSAGES[result.code],
        errors: result.code === "FULFILLMENT_UNAVAILABLE" ? { fulfillment: MESSAGES.FULFILLMENT_UNAVAILABLE } : {},
      });
    default:
      return state({ message: MESSAGES[result.kind] });
  }
}
