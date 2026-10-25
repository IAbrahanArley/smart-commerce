package com.smartcommerce.payments.provider;

import com.smartcommerce.payments.domain.PaymentException;
import com.smartcommerce.payments.security.TokenCipher;
import com.smartcommerce.payments.security.WebhookSignature;

/**
 * The Mercado Pago clients and the token cipher, present only when the platform credentials are
 * configured (ADR-0013 P10: sandbox application created by the owner). Without them the payment
 * section reports "unavailable", checkout keeps working without payment (D6 environments only) and
 * nothing reaches the provider. {@code webhook} is optional (SC-014): without its secret the
 * webhook endpoint is off and confirmation relies on querying the provider (SC-015, P6).
 */
public record PaymentProviders(
    MercadoPagoOAuthClient mercadoPago,
    MercadoPagoCheckoutClient checkout,
    TokenCipher cipher,
    WebhookSignature webhook) {
  public static PaymentProviders unavailable() {
    return new PaymentProviders(null, null, null, null);
  }

  public boolean webhookEnabled() {
    return available() && webhook != null;
  }

  public boolean available() {
    return mercadoPago != null && checkout != null && cipher != null;
  }

  public MercadoPagoOAuthClient requireMercadoPago() {
    if (!available()) throw new PaymentException(PaymentException.Reason.UNAVAILABLE);
    return mercadoPago;
  }

  public MercadoPagoCheckoutClient requireCheckout() {
    if (!available()) throw new PaymentException(PaymentException.Reason.UNAVAILABLE);
    return checkout;
  }

  public TokenCipher requireCipher() {
    if (!available()) throw new PaymentException(PaymentException.Reason.UNAVAILABLE);
    return cipher;
  }
}
