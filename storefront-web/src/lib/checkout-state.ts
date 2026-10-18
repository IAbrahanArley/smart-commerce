import type { CheckoutValues, FieldErrors } from "@/lib/checkout-input";

// Form state kept by React between attempts: typed values stay in memory, never in the URL or
// browser storage. requestId survives ambiguous failures so a retry cannot create a second order.
export type CheckoutState = {
  requestId: string;
  values: CheckoutValues;
  errors: FieldErrors;
  message: string | null;
  attempt: number;
};
