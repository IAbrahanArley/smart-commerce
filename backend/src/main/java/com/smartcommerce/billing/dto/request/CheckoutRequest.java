package com.smartcommerce.billing.dto.request;

import com.smartcommerce.billing.domain.BillingCycle;
import com.smartcommerce.billing.domain.Plan;
import jakarta.validation.constraints.NotNull;

/** The plan and cycle the owner chose; the amount is Stripe's price, never the client's. */
public record CheckoutRequest(@NotNull Plan plan, @NotNull BillingCycle cycle) {}
