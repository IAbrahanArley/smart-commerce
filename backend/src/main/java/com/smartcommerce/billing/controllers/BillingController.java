package com.smartcommerce.billing.controllers;

import com.smartcommerce.billing.dto.request.ChangeRequest;
import com.smartcommerce.billing.dto.request.CheckoutRequest;
import com.smartcommerce.billing.dto.response.BillingAccountView;
import com.smartcommerce.billing.dto.response.ChangePreview;
import com.smartcommerce.billing.dto.response.SubscriptionView;
import com.smartcommerce.billing.services.BillingService;
import com.smartcommerce.billing.services.StripeBillingService;
import com.smartcommerce.billing.services.SubscriptionChangeService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in owner's plan, standing and limits (SC-031), the way to Stripe's hosted Checkout and
 * customer portal (SC-032, ADR-0017), and plan changes, cancellation and resumption in the panel
 * (B13). The owner always comes from the session, never from the request.
 */
@SecurityRequirement(name = "session")
@Tag(name = "Billing")
@RestController
public final class BillingController {
  private final BillingService billing;
  private final StripeBillingService stripe;
  private final SubscriptionChangeService changes;

  public BillingController(
      BillingService billing, StripeBillingService stripe, SubscriptionChangeService changes) {
    this.billing = billing;
    this.stripe = stripe;
    this.changes = changes;
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

  @GetMapping("/api/billing/subscription")
  public ResponseEntity<SubscriptionView> subscription(Authentication actor) {
    return fresh(changes.subscription(owner(actor)));
  }

  @PostMapping("/api/billing/change/preview")
  public ResponseEntity<ChangePreview> preview(
      Authentication actor, @Valid @RequestBody ChangeRequest request) {
    return fresh(changes.preview(owner(actor), request.plan(), request.cycle()));
  }

  @PostMapping("/api/billing/change")
  public ResponseEntity<SubscriptionView> change(
      Authentication actor, @Valid @RequestBody ChangeRequest request) {
    return fresh(
        changes.change(
            owner(actor),
            request.plan(),
            request.cycle(),
            request.prorationDate(),
            request.amountDue()));
  }

  @DeleteMapping("/api/billing/change")
  public ResponseEntity<SubscriptionView> undoChange(Authentication actor) {
    return fresh(changes.undoPendingChange(owner(actor)));
  }

  @PostMapping("/api/billing/cancel")
  public ResponseEntity<SubscriptionView> cancel(Authentication actor) {
    return fresh(changes.cancel(owner(actor)));
  }

  @PostMapping("/api/billing/resume")
  public ResponseEntity<SubscriptionView> resume(Authentication actor) {
    return fresh(changes.resume(owner(actor)));
  }

  private static <T> ResponseEntity<T> fresh(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }

  private static UUID owner(Authentication actor) {
    return UUID.fromString(actor.getName());
  }

  private static ResponseEntity<Map<String, String>> url(String url) {
    // Single-use session links: never cached.
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("url", url));
  }
}
