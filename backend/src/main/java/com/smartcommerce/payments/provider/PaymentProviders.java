package com.smartcommerce.payments.provider;

import com.smartcommerce.payments.domain.PaymentException;
import com.smartcommerce.payments.security.TokenCipher;

/**
 * The Mercado Pago clients and the token cipher, present only when the platform credentials are
 * configured (ADR-0013 P10: sandbox application created by the owner). Without them the payment
 * section reports "unavailable", checkout keeps working without payment (D6 environments only) and
 * nothing reaches the provider.
 */
public record PaymentProviders(
    MercadoPagoOAuthClient mercadoPago, MercadoPagoOrdersClient orders, TokenCipher cipher) {
  public static PaymentProviders unavailable() {
    return new PaymentProviders(null, null, null);
  }

  public boolean available() {
    return mercadoPago != null && orders != null && cipher != null;
  }

  public MercadoPagoOAuthClient requireMercadoPago() {
    if (!available()) throw new PaymentException(PaymentException.Reason.UNAVAILABLE);
    return mercadoPago;
  }

  public MercadoPagoOrdersClient requireOrders() {
    if (!available()) throw new PaymentException(PaymentException.Reason.UNAVAILABLE);
    return orders;
  }

  public TokenCipher requireCipher() {
    if (!available()) throw new PaymentException(PaymentException.Reason.UNAVAILABLE);
    return cipher;
  }
}
