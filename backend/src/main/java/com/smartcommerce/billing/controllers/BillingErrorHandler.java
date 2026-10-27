package com.smartcommerce.billing.controllers;

import com.smartcommerce.billing.domain.BillingException;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Plan limits and inactive accounts, wherever they are raised (company and product creation, the
 * write guard): 402 when the account must be regularized, 409 when a limit is used up. The code
 * lets the panel offer "Mudar de plano".
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public final class BillingErrorHandler {
  @ExceptionHandler(BillingException.class)
  ResponseEntity<Map<String, String>> billing(BillingException exception) {
    int status = exception.reason() == BillingException.Reason.PLAN_INACTIVE ? 402 : 409;
    return ResponseEntity.status(status)
        .body(Map.of("message", exception.getMessage(), "code", exception.reason().name()));
  }
}
