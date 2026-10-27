package com.smartcommerce.billing.config;

import com.smartcommerce.billing.provider.StripeGateway;
import com.smartcommerce.billing.provider.StripeSdkGateway;
import com.stripe.StripeClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The Stripe client of the platform subscription (SC-032, ADR-0017 B11). The key is only read from
 * the environment; {@code billing.stripe.api-base} exists for tests that point at a fake server.
 * Without a key the client is never called: every Stripe endpoint answers 404 while billing is off,
 * and {@link BillingSafetyCheck} refuses to start with billing on and no key.
 */
@Configuration
public class StripeConfiguration {
  @Bean
  StripeGateway stripeGateway(
      @Value("${billing.stripe.secret-key:}") String secretKey,
      @Value("${billing.stripe.api-base:}") String apiBase) {
    if (secretKey.isBlank()) return new StripeSdkGateway(null);
    var builder =
        StripeClient.builder()
            .setApiKey(secretKey)
            .setConnectTimeout(5_000)
            .setReadTimeout(15_000)
            .setMaxNetworkRetries(2);
    if (!apiBase.isBlank()) builder.setApiBase(apiBase);
    return new StripeSdkGateway(builder.build());
  }
}
