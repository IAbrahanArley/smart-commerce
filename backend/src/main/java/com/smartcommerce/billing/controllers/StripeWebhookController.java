package com.smartcommerce.billing.controllers;

import com.smartcommerce.billing.services.StripeBillingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stripe webhook of the platform subscription (SC-032, ADR-0017 B6). Public and stateless: the
 * {@code Stripe-Signature} HMAC over the raw body authenticates it, so there is no session or CSRF.
 * Bounded body, 400 for a bad signature, 404 while billing is off, 503 when Stripe or the database
 * fails (Stripe retries), and a generic message only.
 */
@RestController
@Tag(name = "Billing")
public final class StripeWebhookController {
  public static final int MAX_BYTES = 64 * 1024;
  private final StripeBillingService stripe;

  public StripeWebhookController(StripeBillingService stripe) {
    this.stripe = stripe;
  }

  @PostMapping("/api/public/webhooks/stripe")
  public ResponseEntity<Map<String, String>> receive(InputStream body, HttpServletRequest request)
      throws IOException {
    byte[] content = body.readNBytes(MAX_BYTES + 1);
    if (content.length > MAX_BYTES) return reply(413, "Evento grande demais.");
    stripe.receive(content, request.getHeader("Stripe-Signature"));
    return reply(200, "Recebido.");
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String, String>> unavailable() {
    return reply(503, "Serviço temporariamente indisponível.");
  }

  private static ResponseEntity<Map<String, String>> reply(int status, String message) {
    return ResponseEntity.status(status)
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", message));
  }
}
