package com.smartcommerce.orders.security;

import com.smartcommerce.payments.provider.PaymentProviders;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Startup guard for the public checkout (ADR-0012 D4/D6). Outside the {@code dev} profile, enabling
 * checkout without the signed-IP secret would put every buyer of every store in the single per-IP
 * bucket of the storefront-web server, so one client could block checkout for all tenants. The
 * application refuses to start in that configuration instead of degrading silently. D6 also holds
 * in code: outside {@code dev} and the hosted test environment ({@code
 * deployment.environment=hosted-test}, ADR-0014 H2), checkout requires real payments (D6 revised
 * for production, MVP block 2b): the Mercado Pago integration configured with its webhook and
 * production tokens ({@code payments.mercado-pago.test-token=false}). A store without a connected
 * account still refuses new orders ({@code PAYMENT_UNAVAILABLE}). Checkout with the lifecycle
 * routines disabled is only warned about: nothing would expire or be anonymized (D2).
 */
@Component
public class CheckoutSafetyCheck {
  private static final Logger LOG = LoggerFactory.getLogger(CheckoutSafetyCheck.class);

  public CheckoutSafetyCheck(
      @Value("${orders.checkout.enabled:false}") boolean enabled,
      @Value("${orders.client-ip.secret:}") String secret,
      @Value("${orders.maintenance.enabled:true}") boolean maintenance,
      @Value("${deployment.environment:local}") String deployment,
      @Value("${payments.mercado-pago.test-token:true}") boolean testTokens,
      PaymentProviders providers,
      Environment environment) {
    if (enabled && !maintenance)
      LOG.warn(
          "Checkout is enabled with orders.maintenance.enabled=false: pending orders will not be"
              + " recorded as expired and unpaid orders will not be anonymized");
    verify(
        enabled,
        secret != null && !secret.isBlank(),
        Arrays.asList(environment.getActiveProfiles()).contains("dev"),
        "hosted-test".equals(deployment),
        providers.webhookEnabled() && !testTokens);
  }

  static void verify(
      boolean enabled,
      boolean secretConfigured,
      boolean devProfile,
      boolean hostedTest,
      boolean productionPayments) {
    if (enabled && !devProfile && !hostedTest && !productionPayments)
      throw new IllegalStateException(
          "D6: orders.checkout.enabled outside the dev profile and"
              + " deployment.environment=hosted-test requires the Mercado Pago integration with"
              + " its webhook secret and MERCADO_PAGO_TEST_TOKEN=false");
    if (enabled && !secretConfigured && !devProfile)
      throw new IllegalStateException(
          "orders.checkout.enabled outside the dev profile requires orders.client-ip.secret"
              + " (STOREFRONT_FORWARDING_SECRET)");
  }
}
