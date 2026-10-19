// Checkout input rules shared by the server action and the smoke test. Kept free of Next/server
// imports. The backend repeats every rule; these exist so the buyer gets field-level messages.

export type FulfillmentType = "PICKUP" | "FIXED_SHIPPING";
export type CheckoutField = "fulfillment" | "name" | "email" | "phone" | "line1" | "line2" | "district" | "city" | "state" | "postalCode";
export type CheckoutValues = Record<CheckoutField, string>;
export type FieldErrors = Partial<Record<CheckoutField, string>>;
export type CheckoutPayload = {
  requestId: string;
  fulfillment: { type: "PICKUP" } | { type: "FIXED_SHIPPING"; address: Record<string, string> };
  buyer: { name: string; email: string; phone: string };
  expectedTotal: string;
};

export const CHECKOUT_FIELDS: CheckoutField[] = ["fulfillment", "name", "email", "phone", "line1", "line2", "district", "city", "state", "postalCode"];
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const MONEY = /^\d{1,20}\.\d{2}$/;
// Control characters plus Unicode line and paragraph separators (U+2028, U+2029).
const CONTROL = /[\p{Cc}\p{Zl}\p{Zp}]/u;
const EMAIL = /^[^\s@]{1,64}@[^\s@]+\.[^\s@]{2,}$/;

// D6: the checkout exists only when explicitly enabled; anything else keeps it hidden.
// D6 (ADR-0012) with the ADR-0014 H2 exception: outside development builds, checkout without
// payments exists only in the hosted test environment, and always needs the explicit flag.
export function checkoutEnabled(value: string | undefined, deployment: string | undefined, production: boolean): boolean {
  return value === "true" && (!production || hostedTestEnvironment(deployment));
}

export function hostedTestEnvironment(value: string | undefined): boolean {
  return value === "hosted-test";
}

export function checkoutConfigured(): boolean {
  return checkoutEnabled(process.env.STOREFRONT_CHECKOUT_ENABLED, process.env.DEPLOYMENT_ENVIRONMENT, process.env.NODE_ENV === "production");
}

export function validRequestId(value: unknown): value is string {
  return typeof value === "string" && UUID.test(value);
}

export function emptyValues(): CheckoutValues {
  return Object.fromEntries(CHECKOUT_FIELDS.map(field => [field, ""])) as CheckoutValues;
}

function text(value: string, max: number): string | null {
  const normalized = value.trim().normalize("NFC");
  if (!normalized || [...normalized].length > max || CONTROL.test(normalized)) return null;
  return normalized;
}

export function validateCheckout(
  values: CheckoutValues,
  context: { requestId: string; totals: Partial<Record<FulfillmentType, string>> },
): { errors: FieldErrors } | { payload: CheckoutPayload } {
  const errors: FieldErrors = {};
  const type = values.fulfillment === "PICKUP" || values.fulfillment === "FIXED_SHIPPING" ? values.fulfillment : null;
  const expectedTotal = type ? context.totals[type] : undefined;
  if (!type || !expectedTotal || !MONEY.test(expectedTotal)) errors.fulfillment = "Escolha uma forma de entrega.";
  const name = text(values.name, 120);
  if (!name) errors.name = "Informe seu nome, com até 120 caracteres.";
  const email = values.email.trim();
  if (email.length > 254 || !EMAIL.test(email)) errors.email = "Informe um e-mail válido, por exemplo nome@exemplo.com.";
  const phone = values.phone.replace(/[\s().-]/g, "");
  if (!/^\+?\d{10,15}$/.test(phone)) errors.phone = "Informe o telefone com DDD, somente números.";
  let address: Record<string, string> | null = null;
  if (type === "FIXED_SHIPPING") {
    const line1 = text(values.line1, 200);
    const line2 = values.line2.trim() ? text(values.line2, 200) : "";
    const district = text(values.district, 100);
    const city = text(values.city, 100);
    const state = values.state.trim().toUpperCase();
    const postalCode = values.postalCode.replace(/[\s.-]/g, "");
    if (!line1) errors.line1 = "Informe rua e número.";
    if (line2 === null) errors.line2 = "O complemento aceita até 200 caracteres, em uma linha.";
    if (!district) errors.district = "Informe o bairro.";
    if (!city) errors.city = "Informe a cidade.";
    if (!/^[A-Z]{2}$/.test(state)) errors.state = "Informe a sigla do estado, por exemplo SP.";
    if (!/^\d{8}$/.test(postalCode)) errors.postalCode = "Informe o CEP com 8 números.";
    if (line1 && line2 !== null && district && city)
      address = { line1, ...(line2 ? { line2 } : {}), district, city, state, postalCode };
  }
  if (Object.keys(errors).length > 0 || !type || !expectedTotal || !name || !validRequestId(context.requestId)) return { errors };
  return {
    payload: {
      requestId: context.requestId,
      fulfillment: type === "PICKUP" ? { type } : { type, address: address as Record<string, string> },
      buyer: { name, email, phone },
      expectedTotal,
    },
  };
}
