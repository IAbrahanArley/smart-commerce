// Checkout Pro destination check (SC-013). The backend already validates the provider answer; the
// storefront checks again before redirecting the buyer, so only an HTTPS Mercado Pago page is ever
// a redirect target. Free of Next imports for the smoke test.

const HOST = /^([a-z0-9-]+\.)*mercadopago\.com(\.[a-z]{2})?$/;

export function checkoutPage(value: unknown): value is string {
  if (typeof value !== "string" || value.length > 2048) return false;
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    return false;
  }
  return url.protocol === "https:" && !url.username && !url.password && !url.port && !url.hash && HOST.test(url.hostname);
}

export function paymentReturnPath(orderPathValue: string): string {
  return `${orderPathValue}/pagamento`;
}
