package com.smartcommerce.finance.controllers;

import com.smartcommerce.finance.domain.ReportPeriod;
import com.smartcommerce.finance.dto.response.FinanceReportView;
import com.smartcommerce.finance.services.FinanceReportService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Store OWNER financial report (SC-021); read only, never cached. */
@SecurityRequirement(name = "session")
@Tag(name = "Finance")
@RestController
@RequestMapping("/api/tenants/{tenantId}/finance")
public final class FinanceReportController {
  private final FinanceReportService reports;

  public FinanceReportController(FinanceReportService reports) {
    this.reports = reports;
  }

  @GetMapping
  public ResponseEntity<FinanceReportView> report(
      @PathVariable UUID tenantId,
      @RequestParam(required = false) String period,
      @RequestParam(required = false) String month,
      Authentication actor) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            reports.report(
                UUID.fromString(actor.getName()),
                tenantId,
                ReportPeriod.of(period, month, reports.today())));
  }
}
