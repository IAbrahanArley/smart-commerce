package com.smartcommerce.billing.controllers;

import com.smartcommerce.billing.domain.BillingException;
import com.smartcommerce.billing.provider.StripeUnavailableException;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Plan limits and inactive accounts, wherever they are raised (company and product creation, the
 * write guard): 402 when the account must be regularized, 409 when a limit is used up or the
 * subscription state does not allow the request. The code lets the panel offer "Mudar de plano".
 * Stripe failures answer 503 without the provider's text (SC-032).
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public final class BillingErrorHandler {
  @ExceptionHandler(BillingException.class)
  ResponseEntity<Map<String, String>> billing(BillingException exception) {
    int status =
        switch (exception.reason()) {
          case PLAN_INACTIVE, PAYMENT_DECLINED -> 402;
          case INVALID_EVENT -> 400;
          case DISABLED -> 404;
          case PLAN_LIMIT,
              SUBSCRIPTION_EXISTS,
              NO_SUBSCRIPTION,
              CHANGE_NOT_ALLOWED,
              PREVIEW_EXPIRED ->
              409;
        };
    return ResponseEntity.status(status)
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", exception.getMessage(), "code", exception.reason().name()));
  }

  @ExceptionHandler(StripeUnavailableException.class)
  ResponseEntity<Map<String, String>> stripe() {
    // Stripe retries webhooks on 5xx; the panel offers to try again.
    return ResponseEntity.status(503)
        .cacheControl(CacheControl.noStore())
        .body(
            Map.of(
                "message",
                "Não foi possível falar com o processador de pagamentos. Tente novamente.",
                "code",
                "STRIPE_UNAVAILABLE"));
  }
}
