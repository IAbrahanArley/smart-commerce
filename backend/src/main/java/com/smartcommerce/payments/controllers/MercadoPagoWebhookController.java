package com.smartcommerce.payments.controllers;

import com.smartcommerce.payments.domain.PaymentException;
import com.smartcommerce.payments.dto.request.WebhookNotification;
import com.smartcommerce.payments.security.WebhookRateLimit;
import com.smartcommerce.payments.services.PaymentWebhookService;
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
 * Mercado Pago webhook (SC-014, ADR-0013 P5). Public and stateless (no session, no CSRF: the HMAC
 * signature authenticates it). Bounded body, 401 for any signature failure, and a fast 200 once the
 * event is recorded; processing happens later. The rate limit only counts failed signatures, so an
 * anonymous flood never makes a genuine signed delivery wait (review of SC-014). Responses carry no
 * detail beyond a generic message.
 */
@RestController
@Tag(name = "Payments")
public final class MercadoPagoWebhookController {
  private final PaymentWebhookService webhooks;
  private final WebhookRateLimit rateLimit;

  public MercadoPagoWebhookController(PaymentWebhookService webhooks, WebhookRateLimit rateLimit) {
    this.webhooks = webhooks;
    this.rateLimit = rateLimit;
  }

  @PostMapping("/api/public/webhooks/mercado-pago")
  public ResponseEntity<Map<String, String>> receive(InputStream body, HttpServletRequest request)
      throws IOException {
    if (!webhooks.enabled()) return reply(404, "Não encontrado.");
    byte[] content = body.readNBytes(WebhookNotification.MAX_BYTES + 1);
    if (content.length > WebhookNotification.MAX_BYTES)
      return reply(413, "Notificação grande demais.");
    webhooks.receive(
        request.getHeader("x-signature"),
        request.getHeader("x-request-id"),
        request.getParameter("data.id"),
        request.getParameter("type"),
        content);
    return reply(200, "Recebido.");
  }

  @ExceptionHandler(PaymentException.class)
  ResponseEntity<Map<String, String>> payment(PaymentException error) {
    return switch (error.reason()) {
      case INVALID_SIGNATURE ->
          rateLimit.tryAcquire()
              ? reply(401, "Assinatura inválida.")
              : reply(429, "Muitas notificações.");
      case INVALID_NOTIFICATION -> reply(400, "Notificação inválida.");
      case UNAVAILABLE -> reply(404, "Não encontrado.");
      default -> reply(503, "Serviço temporariamente indisponível.");
    };
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String, String>> unavailable() {
    // The provider retries; nothing was recorded.
    return reply(503, "Serviço temporariamente indisponível.");
  }

  private static ResponseEntity<Map<String, String>> reply(int status, String message) {
    return ResponseEntity.status(status)
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", message));
  }
}
