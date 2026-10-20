"use server";

import { cookies } from "next/headers";
import { notFound, redirect } from "next/navigation";
import { validSlug } from "@/lib/cart-cookie";
import { orderCookieName, validOrderId, validOrderToken } from "@/lib/order-cookie";
import { startPayment } from "@/lib/orders";

// Runs on the storefront origin from the order's own pages, where the path-scoped token cookie is
// sent. The token never leaves the server; only the Mercado Pago page URL reaches the browser.

export type PaymentState = { message: string | null; attempt: number };

const MESSAGES = {
  closed: "Este pedido não está mais aguardando pagamento. Atualize a página para ver a situação.",
  unavailable: "O pagamento online não está disponível nesta loja agora. Tente novamente mais tarde ou fale com a loja.",
  failed: "Não foi possível abrir o pagamento agora. Tente novamente: o mesmo pagamento será reaproveitado, sem cobrança em dobro.",
} as const;

function field(form: FormData, name: string): string {
  const value = form.get(name);
  return typeof value === "string" ? value.slice(0, 100) : "";
}

export async function payOrder(previous: PaymentState, form: FormData): Promise<PaymentState> {
  const slug = field(form, "slug");
  const orderId = field(form, "orderId");
  if (!validSlug(slug) || !validOrderId(orderId)) notFound();
  const token = (await cookies()).get(orderCookieName(orderId))?.value;
  if (!validOrderToken(token)) notFound();
  const payment = await startPayment(slug, orderId.toLowerCase(), token);
  if (payment.kind === "redirect") redirect(payment.url);
  return { message: MESSAGES[payment.kind], attempt: previous.attempt + 1 };
}
