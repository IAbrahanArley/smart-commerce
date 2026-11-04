package com.smartcommerce.finance.controllers;

import com.smartcommerce.finance.domain.CashInput;
import com.smartcommerce.finance.domain.ExpenseInput;
import com.smartcommerce.finance.domain.PageCursor;
import com.smartcommerce.finance.dto.request.CashRequests;
import com.smartcommerce.finance.dto.response.CashHistoryView;
import com.smartcommerce.finance.dto.response.CashOverviewView;
import com.smartcommerce.finance.dto.response.CashSessionView;
import com.smartcommerce.finance.services.CashSessionService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * The store's cash register (SC-052): the OWNER and the store's employees open, move and close
 * drawer sessions; only the OWNER reopens and reads the history. Session and CSRF on mutations;
 * responses never cached.
 */
@SecurityRequirement(name = "session")
@Tag(name = "Finance")
@RestController
@RequestMapping("/api/tenants/{tenantId}/cash")
public final class CashController {
  private final CashSessionService cash;

  public CashController(CashSessionService cash) {
    this.cash = cash;
  }

  @GetMapping
  public ResponseEntity<CashOverviewView> overview(
      @PathVariable UUID tenantId, Authentication actor) {
    return fresh(CashOverviewView.of(cash.overview(actor(actor), tenantId)));
  }

  @GetMapping("/sessions")
  public ResponseEntity<CashHistoryView> history(
      @PathVariable UUID tenantId,
      @RequestParam(required = false) String before,
      Authentication actor) {
    var history = cash.history(actor(actor), tenantId, PageCursor.parse(before, false));
    return fresh(
        new CashHistoryView(
            history.sessions().stream()
                .map(session -> CashSessionView.of(session, true, null))
                .toList(),
            history.nextCursor() == null ? null : history.nextCursor().text()));
  }

  @GetMapping("/sessions/{sessionId}")
  public ResponseEntity<CashSessionView> session(
      @PathVariable UUID tenantId, @PathVariable UUID sessionId, Authentication actor) {
    return fresh(CashSessionView.of(cash.session(actor(actor), tenantId, sessionId)));
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping("/sessions")
  public ResponseEntity<CashSessionView> open(
      @PathVariable UUID tenantId, @RequestBody CashRequests.Open request, Authentication actor) {
    if (request == null) throw new IllegalArgumentException("Missing body");
    var input = CashInput.Open.of(request.requestId(), request.accountId(), request.amount());
    return fresh(CashSessionView.of(cash.open(actor(actor), tenantId, input)));
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping("/sessions/{sessionId}/movements")
  public ResponseEntity<CashSessionView> move(
      @PathVariable UUID tenantId,
      @PathVariable UUID sessionId,
      @RequestBody CashRequests.Movement request,
      Authentication actor) {
    if (request == null) throw new IllegalArgumentException("Missing body");
    var input =
        CashInput.Movement.of(
            request.requestId(),
            request.type(),
            request.amount(),
            request.reason(),
            request.accountId(),
            request.category());
    return fresh(CashSessionView.of(cash.move(actor(actor), tenantId, sessionId, input)));
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping("/sessions/{sessionId}/close")
  public ResponseEntity<CashSessionView> close(
      @PathVariable UUID tenantId,
      @PathVariable UUID sessionId,
      @RequestBody CashRequests.Close request,
      Authentication actor) {
    if (request == null) throw new IllegalArgumentException("Missing body");
    var input = CashInput.Close.of(request.requestId(), request.counted());
    return fresh(CashSessionView.of(cash.close(actor(actor), tenantId, sessionId, input)));
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping("/sessions/{sessionId}/reopen")
  public ResponseEntity<CashSessionView> reopen(
      @PathVariable UUID tenantId,
      @PathVariable UUID sessionId,
      @RequestBody CashRequests.Reopen request,
      Authentication actor) {
    String reason = ExpenseInput.line(request == null ? null : request.reason(), 300);
    return fresh(CashSessionView.of(cash.reopen(actor(actor), tenantId, sessionId, reason)));
  }

  private static UUID actor(Authentication actor) {
    return UUID.fromString(actor.getName());
  }

  private static <T> ResponseEntity<T> fresh(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }
}
