"use server";

import { headers } from "next/headers";
import { notFound } from "next/navigation";
import { validSlug } from "@/lib/cart-cookie";
import { checkoutConfigured } from "@/lib/checkout-input";
import { trustedClientIp } from "@/lib/client-ip";
import { type LookupState, validateLookup } from "@/lib/order-lookup";
import { lookupOrder } from "@/lib/orders";

// SC-046: runs on the storefront origin. Typed values stay in the form state, never in the URL.
const MESSAGES = {
  fields: "Confira os campos destacados.",
  missing: "Não encontramos um pedido com esse número e esse e-mail nesta loja. Confira os dados e tente novamente.",
  limited: "Muitas tentativas de consulta. Aguarde uma hora e tente novamente, ou fale com a loja.",
  invalid: "Confira o número do pedido e o e-mail.",
  unavailable: "Não foi possível consultar o pedido agora. Tente novamente em instantes.",
} as const;

function field(form: FormData, name: string): string {
  const value = form.get(name);
  return typeof value === "string" ? value.slice(0, 300) : "";
}

export async function findOrder(previous: LookupState, form: FormData): Promise<LookupState> {
  if (!checkoutConfigured()) notFound();
  const slug = field(form, "slug");
  if (!validSlug(slug)) notFound();
  const values = { number: field(form, "number"), email: field(form, "email") };
  const state = (patch: Partial<LookupState>): LookupState => ({ values, errors: {}, message: null, order: null, attempt: previous.attempt + 1, ...patch });
  const checked = validateLookup(values);
  if ("errors" in checked) return state({ errors: checked.errors, message: MESSAGES.fields });
  const result = await lookupOrder(slug, checked.number, checked.email, trustedClientIp((await headers()).get("x-forwarded-for"), process.env.STOREFRONT_TRUST_FORWARDED_FOR));
  return result.kind === "found" ? state({ order: result.order }) : state({ message: MESSAGES[result.kind] });
}
