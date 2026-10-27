package com.smartcommerce.billing.controllers;

import com.smartcommerce.billing.dto.request.CheckoutRequest;
import com.smartcommerce.billing.dto.response.BillingAccountView;
import com.smartcommerce.billing.services.BillingService;
import com.smartcommerce.billing.services.StripeBillingService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in owner's plan, standing and limits (SC-031), and the way to Stripe's hosted Checkout
 * and customer portal (SC-032, ADR-0017).
 */
@SecurityRequirement(name = "session")
@Tag(name = "Billing")
@RestController
public final class BillingController {
  private final BillingService billing;
  private final StripeBillingService stripe;

  public BillingController(BillingService billing, StripeBillingService stripe) {
    this.billing = billing;
    this.stripe = stripe;
  }

  @GetMapping("/api/billing/account")
  public BillingAccountView account(Authentication actor) {
    return billing.account(owner(actor));
  }

  @PostMapping("/api/billing/checkout")
  public ResponseEntity<Map<String, String>> checkout(
      Authentication actor, @Valid @RequestBody CheckoutRequest request) {
    return url(stripe.checkout(owner(actor), request.plan(), request.cycle()));
  }

  @PostMapping("/api/billing/portal")
  public ResponseEntity<Map<String, String>> portal(Authentication actor) {
    return url(stripe.portal(owner(actor)));
  }

  private static UUID owner(Authentication actor) {
    return UUID.fromString(actor.getName());
  }

  private static ResponseEntity<Map<String, String>> url(String url) {
    // Single-use session links: never cached.
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("url", url));
  }
}
