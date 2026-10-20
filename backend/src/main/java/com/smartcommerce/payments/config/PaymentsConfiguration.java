package com.smartcommerce.payments.config;

import com.smartcommerce.payments.provider.MercadoPagoOAuthClient;
import com.smartcommerce.payments.provider.PaymentProviders;
import com.smartcommerce.payments.security.TokenCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SC-012: the integration is enabled by setting the application's client id. Once it is set, the
 * secret, the redirect URI and the encryption key are mandatory and validated at startup, so a
 * half-configured environment fails fast instead of storing tokens in clear or leaking errors.
 */
@Configuration(proxyBeanMethods = false)
public class PaymentsConfiguration {
  @Bean
  PaymentProviders paymentProviders(
      @Value("${payments.mercado-pago.client-id:}") String clientId,
      @Value("${payments.mercado-pago.client-secret:}") String clientSecret,
      @Value("${payments.mercado-pago.redirect-uri:}") String redirectUri,
      @Value("${payments.mercado-pago.authorization-url:}") String authorizationUrl,
      @Value("${payments.mercado-pago.token-url:}") String tokenUrl,
      @Value("${payments.mercado-pago.test-token:true}") boolean testToken,
      @Value("${payments.token-encryption-key:}") String encryptionKey) {
    if (clientId == null || clientId.isBlank()) return PaymentProviders.unavailable();
    return new PaymentProviders(
        new MercadoPagoOAuthClient(
            clientId, clientSecret, redirectUri, authorizationUrl, tokenUrl, testToken),
        TokenCipher.fromBase64(encryptionKey));
  }
}
