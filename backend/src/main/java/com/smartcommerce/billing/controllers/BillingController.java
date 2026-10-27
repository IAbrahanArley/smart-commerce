package com.smartcommerce.billing.controllers;

import com.smartcommerce.billing.dto.response.BillingAccountView;
import com.smartcommerce.billing.services.BillingService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in owner's plan, standing and limits (SC-031, ADR-0017). */
@SecurityRequirement(name = "session")
@Tag(name = "Billing")
@RestController
public final class BillingController {
  private final BillingService billing;

  public BillingController(BillingService billing) {
    this.billing = billing;
  }

  @GetMapping("/api/billing/account")
  public BillingAccountView account(Authentication actor) {
    return billing.account(UUID.fromString(actor.getName()));
  }
}
