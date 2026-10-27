package com.smartcommerce.billing.provider;

/**
 * Stripe did not answer or refused the request. The cause stays for the logs' class name only; the
 * owner sees a generic message (no provider text, which may echo request data).
 */
public final class StripeUnavailableException extends RuntimeException {
  public StripeUnavailableException(Throwable cause) {
    super("Stripe unavailable", cause);
  }
}
