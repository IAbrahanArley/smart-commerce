package com.smartcommerce.orders.security;

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
 * in code: outside {@code dev}, checkout without payments is only allowed in the hosted test
 * environment ({@code deployment.environment=hosted-test}, ADR-0014 H2). Checkout with the
 * lifecycle routines disabled is only warned about: nothing would expire or be anonymized (D2).
 */
@Component
public class CheckoutSafetyCheck {
  private static final Logger LOG = LoggerFactory.getLogger(CheckoutSafetyCheck.class);

  public CheckoutSafetyCheck(
      @Value("${orders.checkout.enabled:false}") boolean enabled,
      @Value("${orders.client-ip.secret:}") String secret,
      @Value("${orders.maintenance.enabled:true}") boolean maintenance,
      @Value("${deployment.environment:local}") String deployment,
      Environment environment) {
    if (enabled && !maintenance)
      LOG.warn(
          "Checkout is enabled with orders.maintenance.enabled=false: pending orders will not be"
              + " recorded as expired and unpaid orders will not be anonymized");
    verify(
        enabled,
        secret != null && !secret.isBlank(),
        Arrays.asList(environment.getActiveProfiles()).contains("dev"),
        "hosted-test".equals(deployment));
  }

  static void verify(
      boolean enabled, boolean secretConfigured, boolean devProfile, boolean hostedTest) {
    if (enabled && !devProfile && !hostedTest)
      throw new IllegalStateException(
          "D6: orders.checkout.enabled outside the dev profile requires"
              + " deployment.environment=hosted-test (no payments exist yet)");
    if (enabled && !secretConfigured && !devProfile)
      throw new IllegalStateException(
          "orders.checkout.enabled outside the dev profile requires orders.client-ip.secret"
              + " (STOREFRONT_FORWARDING_SECRET)");
  }
}
