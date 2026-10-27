package com.smartcommerce.billing.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Startup guard of the platform subscription (SC-032, ADR-0017 B11, B12). With billing on, owners
 * must be able to pay before any limit applies, so the Stripe key and the webhook secret are both
 * required. Keys are never mixed across environments: production ({@code
 * deployment.environment=production}) takes only live keys, every other environment only test keys,
 * so a test environment cannot charge a real card and production cannot sell a fake plan.
 */
@Component
public class BillingSafetyCheck {
  public BillingSafetyCheck(
      @Value("${billing.enabled:false}") boolean enabled,
      @Value("${billing.stripe.secret-key:}") String secretKey,
      @Value("${billing.stripe.webhook-secret:}") String webhookSecret,
      @Value("${deployment.environment:local}") String deployment) {
    verify(enabled, secretKey, webhookSecret, "production".equals(deployment));
  }

  public static void verify(
      boolean enabled, String secretKey, String webhookSecret, boolean production) {
    boolean hasKey = secretKey != null && !secretKey.isBlank();
    if (enabled && (!hasKey || webhookSecret == null || webhookSecret.isBlank()))
      throw new IllegalStateException(
          "billing.enabled requires STRIPE_SECRET_KEY and STRIPE_WEBHOOK_SECRET (ADR-0017 B12)");
    if (!hasKey) return;
    boolean live = secretKey.startsWith("sk_live_") || secretKey.startsWith("rk_live_");
    boolean test = secretKey.startsWith("sk_test_") || secretKey.startsWith("rk_test_");
    if (production && !live)
      throw new IllegalStateException(
          "deployment.environment=production requires a live Stripe key (sk_live_ or rk_live_)");
    if (!production && !test)
      throw new IllegalStateException(
          "Outside deployment.environment=production only Stripe test keys (sk_test_ or rk_test_)"
              + " are accepted");
  }
}
