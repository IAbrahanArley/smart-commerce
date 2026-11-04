package com.smartcommerce.finance.controllers;

import com.smartcommerce.finance.domain.PageCursor;
import com.smartcommerce.finance.dto.request.CreditRequests;
import com.smartcommerce.finance.dto.response.CreditViews;
import com.smartcommerce.finance.services.CreditService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Store credit (SC-055): the OWNER lists customers with what they owe; the OWNER and the store's
 * employees open a customer and register what they pay. Session and CSRF on mutations; responses
 * never cached.
 */
@SecurityRequirement(name = "session")
@Tag(name = "Finance")
@RestController
@RequestMapping("/api/tenants/{tenantId}/credit")
public final class CreditController {
  private static final String UUID_PATTERN =
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

  private final CreditService credit;

  public CreditController(CreditService credit) {
    this.credit = credit;
  }

  /** {@code filter}: ALL (default), OPEN or OVERDUE. */
  @GetMapping
  public ResponseEntity<CreditViews.Overview> overview(
      @PathVariable UUID tenantId,
      @RequestParam(required = false) String filter,
      @RequestParam(required = false) String before,
      Authentication actor) {
    return fresh(
        CreditViews.Overview.of(
            credit.overview(actor(actor), tenantId, filter, PageCursor.parse(before, false))));
  }

  @GetMapping("/customers/{customerId}")
  public ResponseEntity<CreditViews.Customer> customer(
      @PathVariable UUID tenantId,
      @PathVariable UUID customerId,
      @RequestParam(required = false) String before,
      Authentication actor) {
    return fresh(
        CreditViews.Customer.of(
            credit.customer(actor(actor), tenantId, customerId, PageCursor.parse(before, false))));
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping("/customers/{customerId}/receipts")
  public ResponseEntity<CreditViews.Customer> receive(
      @PathVariable UUID tenantId,
      @PathVariable UUID customerId,
      @RequestBody CreditRequests.Receipt request,
      Authentication actor) {
    if (request == null) throw new IllegalArgumentException("Missing body");
    var input =
        new CreditService.ReceiptInput(
            id(request.requestId()),
            money(request.amount()),
            optionalId(request.cashSessionId()),
            optionalId(request.accountId()));
    return fresh(
        CreditViews.Customer.of(credit.receive(actor(actor), tenantId, customerId, input)));
  }

  private static BigDecimal money(String value) {
    if (value == null || !value.matches("[0-9]{1,15}(\\.[0-9]{1,2})?"))
      throw new IllegalArgumentException("Invalid amount");
    return new BigDecimal(value).setScale(2);
  }

  private static UUID id(String value) {
    if (value == null || !value.matches(UUID_PATTERN))
      throw new IllegalArgumentException("Invalid id");
    return UUID.fromString(value);
  }

  private static UUID optionalId(String value) {
    return value == null || value.isBlank() ? null : id(value);
  }

  private static UUID actor(Authentication actor) {
    return UUID.fromString(actor.getName());
  }

  private static <T> ResponseEntity<T> fresh(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }
}
