package com.smartcommerce.analytics.controllers;

import com.smartcommerce.analytics.dto.response.DashboardView;
import com.smartcommerce.analytics.services.DashboardService;
import com.smartcommerce.finance.domain.FinanceException;
import com.smartcommerce.finance.domain.ReportPeriod;
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

/** Store OWNER dashboard (SC-022); read only, never cached. Absent or foreign store: one 404. */
@SecurityRequirement(name = "session")
@Tag(name = "Analytics")
@RestController
@RequestMapping("/api/tenants/{tenantId}/dashboard")
public final class DashboardController {
  private final DashboardService dashboards;

  public DashboardController(DashboardService dashboards) {
    this.dashboards = dashboards;
  }

  @GetMapping
  public ResponseEntity<DashboardView> dashboard(
      @PathVariable UUID tenantId,
      @RequestParam(required = false) String period,
      Authentication actor) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            dashboards.dashboard(
                UUID.fromString(actor.getName()),
                tenantId,
                ReportPeriod.of(period, null, dashboards.today())));
  }

  @ExceptionHandler(FinanceException.class)
  ResponseEntity<Map<String, String>> missing() {
    return ResponseEntity.status(404)
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", "Loja não encontrada."));
  }

  @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
  ResponseEntity<Map<String, String>> invalid() {
    return ResponseEntity.badRequest()
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", "Período inválido."));
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String, String>> unavailable() {
    return ResponseEntity.status(503)
        .header("Retry-After", "5")
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", "Serviço temporariamente indisponível."));
  }
}
