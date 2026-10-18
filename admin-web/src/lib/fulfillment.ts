import { decimalInput } from "./catalog.ts";

export type Fulfillment = {
  pickupEnabled: boolean;
  pickupInstructions: string | null;
  fixedShippingEnabled: boolean;
  fixedShippingAmount: string | null;
  currency: "BRL";
  fixedShippingArea: string | null;
  version: number;
};

export type FulfillmentFields = {
  pickupEnabled: boolean;
  pickupInstructions: string;
  fixedShippingEnabled: boolean;
  fixedShippingAmount: string;
  fixedShippingArea: string;
};

export type FulfillmentErrors = Partial<Record<"pickupInstructions" | "fixedShippingAmount" | "fixedShippingArea", string>>;

export const FULFILLMENT_TEXT_LIMIT = 500;

export function fulfillmentFields(saved: Fulfillment): FulfillmentFields {
  return {
    pickupEnabled: saved.pickupEnabled,
    pickupInstructions: saved.pickupInstructions ?? "",
    fixedShippingEnabled: saved.fixedShippingEnabled,
    fixedShippingAmount: saved.fixedShippingAmount?.replace(".", ",") ?? "",
    fixedShippingArea: saved.fixedShippingArea ?? "",
  };
}

// Mirrors the backend rule: no control, format or line/paragraph separators; limit in code points.
export function fulfillmentText(value: string): { text: string | null } | { error: "invalid" | "long" } {
  if (/[\p{Cc}\p{Cf}\u2028\u2029]/u.test(value)) return { error: "invalid" };
  const text = value.trim();
  if ([...text].length > FULFILLMENT_TEXT_LIMIT) return { error: "long" };
  return { text: text || null };
}

export function fulfillmentPayload(fields: FulfillmentFields, version: number):
  { body: Record<string, unknown> } | { errors: FulfillmentErrors } {
  const errors: FulfillmentErrors = {};
  const textMessage = (error: "invalid" | "long") => error === "long"
    ? `Use até ${FULFILLMENT_TEXT_LIMIT} caracteres.`
    : "Escreva em uma única linha, sem quebras de linha ou caracteres especiais invisíveis.";
  const instructions = fulfillmentText(fields.pickupInstructions);
  const area = fulfillmentText(fields.fixedShippingArea);
  if ("error" in instructions) errors.pickupInstructions = textMessage(instructions.error);
  else if (fields.pickupEnabled && !instructions.text) errors.pickupInstructions = "Informe onde e como o comprador retira o pedido.";
  if ("error" in area) errors.fixedShippingArea = textMessage(area.error);
  else if (fields.fixedShippingEnabled && !area.text) errors.fixedShippingArea = "Informe a área atendida pela entrega.";
  const rawAmount = fields.fixedShippingAmount.trim();
  const amount = rawAmount ? decimalInput(rawAmount) : null;
  if (rawAmount && !amount) errors.fixedShippingAmount = "Informe um valor a partir de zero, com até 17 dígitos antes da vírgula e 2 casas decimais.";
  else if (fields.fixedShippingEnabled && !amount) errors.fixedShippingAmount = "Informe o valor do frete. Use 0 para frete grátis.";
  if (Object.keys(errors).length) return { errors };
  return {
    body: {
      pickupEnabled: fields.pickupEnabled,
      pickupInstructions: "text" in instructions ? instructions.text : null,
      fixedShippingEnabled: fields.fixedShippingEnabled,
      fixedShippingAmount: amount,
      fixedShippingArea: "text" in area ? area.text : null,
      version,
    },
  };
}
