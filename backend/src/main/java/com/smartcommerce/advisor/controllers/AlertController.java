package com.smartcommerce.advisor.controllers;

import com.smartcommerce.advisor.dto.response.AlertsView;
import com.smartcommerce.advisor.services.AlertService;
import com.smartcommerce.finance.domain.FinanceException;
import com.smartcommerce.orders.domain.OrderException;
import com.smartcommerce.payments.domain.PaymentException;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Store OWNER alerts (SC-024); read only, never cached. Absent or foreign store: one 404. */
@SecurityRequirement(name = "session")
@Tag(name = "Advisor")
@RestController
@RequestMapping("/api/tenants/{tenantId}/alerts")
public final class AlertController {
  private final AlertService alerts;

  public AlertController(AlertService alerts) {
    this.alerts = alerts;
  }

  @GetMapping
  public ResponseEntity<AlertsView> alerts(@PathVariable UUID tenantId, Authentication actor) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(alerts.alerts(UUID.fromString(actor.getName()), tenantId));
  }

  @ExceptionHandler({
    FinanceException.class,
    OrderException.class,
    PaymentException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<Map<String, String>> missing() {
    return ResponseEntity.status(404)
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", "Loja não encontrada."));
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String, String>> unavailable() {
    return ResponseEntity.status(503)
        .header("Retry-After", "5")
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", "Serviço temporariamente indisponível."));
  }
}
