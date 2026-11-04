package com.smartcommerce.orders.controllers;

import com.smartcommerce.finance.domain.ExpenseInput;
import com.smartcommerce.orders.domain.CounterSaleCursor;
import com.smartcommerce.orders.domain.CounterSaleInput;
import com.smartcommerce.orders.dto.request.CounterSaleRequests;
import com.smartcommerce.orders.dto.response.CounterSaleViews;
import com.smartcommerce.orders.services.CounterSaleService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Sales at the physical store (SC-053): the OWNER and the store's employees search the stock and
 * register sales; only the OWNER cancels and changes the settings. Session and CSRF on mutations;
 * responses never cached.
 */
@SecurityRequirement(name = "session")
@Tag(name = "Orders")
@RestController
@RequestMapping("/api/tenants/{tenantId}/counter-sales")
public final class CounterSaleController {
  private final CounterSaleService sales;

  public CounterSaleController(CounterSaleService sales) {
    this.sales = sales;
  }

  @GetMapping
  public ResponseEntity<CounterSaleViews.Overview> overview(
      @PathVariable UUID tenantId,
      @RequestParam(required = false) String before,
      Authentication actor) {
    return fresh(
        CounterSaleViews.Overview.of(
            sales.overview(actor(actor), tenantId, CounterSaleCursor.parse(before))));
  }

  @GetMapping("/products")
  public ResponseEntity<List<CounterSaleViews.Product>> search(
      @PathVariable UUID tenantId, @RequestParam(required = false) String q, Authentication actor) {
    return fresh(
        sales.search(actor(actor), tenantId, q).stream()
            .map(CounterSaleViews.Product::of)
            .toList());
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping
  public ResponseEntity<CounterSaleViews.Sale> register(
      @PathVariable UUID tenantId,
      @RequestBody CounterSaleRequests.Sale request,
      Authentication actor) {
    if (request == null || request.items() == null)
      throw new IllegalArgumentException("Missing body");
    var input =
        CounterSaleInput.Sale.of(
            request.requestId(),
            request.items().stream()
                .map(
                    line -> {
                      if (line == null || line.quantity() == null)
                        throw new IllegalArgumentException("Invalid item");
                      return new CounterSaleInput.Line(parse(line.variantId()), line.quantity());
                    })
                .toList(),
            request.discount(),
            request.paymentMethod(),
            request.cashSessionId(),
            request.customerId(),
            request.installments(),
            request.firstDueOn());
    var registered = sales.register(actor(actor), tenantId, input);
    return fresh(CounterSaleViews.Sale.of(registered.sale(), registered.owner(), true));
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping("/{saleId}/cancel")
  public ResponseEntity<CounterSaleViews.Sale> cancel(
      @PathVariable UUID tenantId,
      @PathVariable UUID saleId,
      @RequestBody CounterSaleRequests.Cancel request,
      Authentication actor) {
    if (request == null || request.restock() == null)
      throw new IllegalArgumentException("Missing body");
    String reason = ExpenseInput.line(request.reason(), 300);
    return fresh(
        CounterSaleViews.Sale.of(
            sales.cancel(actor(actor), tenantId, saleId, reason, request.restock())));
  }

  @GetMapping("/settings")
  public ResponseEntity<CounterSaleViews.Settings> settings(
      @PathVariable UUID tenantId, Authentication actor) {
    return fresh(CounterSaleViews.Settings.of(sales.settings(actor(actor), tenantId)));
  }

  @SecurityRequirement(name = "csrf")
  @PutMapping("/settings")
  public ResponseEntity<CounterSaleViews.Settings> saveSettings(
      @PathVariable UUID tenantId,
      @RequestBody CounterSaleRequests.Settings request,
      Authentication actor) {
    if (request == null) throw new IllegalArgumentException("Missing body");
    var input =
        CounterSaleInput.Settings.of(
            request.staffDiscountLimit(),
            request.pixAccountId(),
            request.debitFeePercent(),
            request.debitReleaseDays(),
            request.creditFeePercent(),
            request.creditReleaseDays());
    return fresh(CounterSaleViews.Settings.of(sales.saveSettings(actor(actor), tenantId, input)));
  }

  private static UUID parse(String value) {
    try {
      return UUID.fromString(value);
    } catch (RuntimeException invalid) {
      throw new IllegalArgumentException("Invalid id");
    }
  }

  private static UUID actor(Authentication actor) {
    return UUID.fromString(actor.getName());
  }

  private static <T> ResponseEntity<T> fresh(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }
}
