package com.smartcommerce.orders.security;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Startup guard for the public checkout (ADR-0012 D4/D6). Outside the {@code dev} profile, enabling
 * checkout without the signed-IP secret would put every buyer of every store in the single per-IP
 * bucket of the storefront-web server, so one client could block checkout for all tenants. The
 * application refuses to start in that configuration instead of degrading silently.
 */
@Component
public class CheckoutSafetyCheck {
  public CheckoutSafetyCheck(
      @Value("${orders.checkout.enabled:false}") boolean enabled,
      @Value("${orders.client-ip.secret:}") String secret,
      Environment environment) {
    verify(
        enabled,
        secret != null && !secret.isBlank(),
        Arrays.asList(environment.getActiveProfiles()).contains("dev"));
  }

  static void verify(boolean enabled, boolean secretConfigured, boolean devProfile) {
    if (enabled && !secretConfigured && !devProfile)
      throw new IllegalStateException(
          "orders.checkout.enabled outside the dev profile requires orders.client-ip.secret"
              + " (STOREFRONT_FORWARDING_SECRET)");
  }
}
