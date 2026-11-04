package com.smartcommerce.customers.controllers;

import com.smartcommerce.customers.domain.CustomerInput;
import com.smartcommerce.customers.dto.request.CustomerRequests;
import com.smartcommerce.customers.dto.response.CustomerViews;
import com.smartcommerce.customers.services.StoreCustomerService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * The store's customers (SC-055): the OWNER and the store's employees search and create them at the
 * counter; only the OWNER edits. Session and CSRF on every route (the search is a POST so its text
 * stays out of URLs); responses never cached.
 */
@SecurityRequirement(name = "session")
@Tag(name = "Customers")
@RestController
@RequestMapping("/api/tenants/{tenantId}/customers")
public final class StoreCustomerController {
  private final StoreCustomerService customers;

  public StoreCustomerController(StoreCustomerService customers) {
    this.customers = customers;
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping("/search")
  public ResponseEntity<List<CustomerViews.Customer>> search(
      @PathVariable UUID tenantId,
      @RequestBody CustomerRequests.Search request,
      Authentication actor) {
    if (request == null) throw new IllegalArgumentException("Missing body");
    var found = customers.search(actor(actor), tenantId, request.q());
    return fresh(CustomerViews.Customer.of(found.customers(), found.owner()));
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping
  public ResponseEntity<CustomerViews.Customer> create(
      @PathVariable UUID tenantId,
      @RequestBody CustomerRequests.Customer request,
      Authentication actor) {
    if (request == null) throw new IllegalArgumentException("Missing body");
    var saved =
        customers.create(
            actor(actor), tenantId, CustomerInput.Create.of(request.requestId(), data(request)));
    return fresh(CustomerViews.Customer.of(saved.customer(), saved.owner()));
  }

  @SecurityRequirement(name = "csrf")
  @PutMapping("/{customerId}")
  public ResponseEntity<CustomerViews.Customer> update(
      @PathVariable UUID tenantId,
      @PathVariable UUID customerId,
      @RequestBody CustomerRequests.Customer request,
      Authentication actor) {
    if (request == null) throw new IllegalArgumentException("Missing body");
    return fresh(
        CustomerViews.Customer.of(
            customers.update(actor(actor), tenantId, customerId, data(request)), true));
  }

  private static CustomerInput.Data data(CustomerRequests.Customer request) {
    return CustomerInput.Data.of(
        request.name(), request.phone(), request.email(), request.note(), request.creditLimit());
  }

  private static UUID actor(Authentication actor) {
    return UUID.fromString(actor.getName());
  }

  private static <T> ResponseEntity<T> fresh(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }
}
