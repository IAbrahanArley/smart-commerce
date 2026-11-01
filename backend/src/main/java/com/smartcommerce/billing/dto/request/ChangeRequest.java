package com.smartcommerce.billing.dto.request;

import com.smartcommerce.billing.domain.BillingCycle;
import com.smartcommerce.billing.domain.Plan;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * A plan or cycle change (ADR-0017 B13). For an immediate change, {@code prorationDate} and {@code
 * amountDue} are the preview's: the backend asks Stripe again for that date and refuses to charge
 * if the amount differs, so the owner is only ever charged what he saw. Neither decides the charge.
 */
public record ChangeRequest(
    @NotNull Plan plan, @NotNull BillingCycle cycle, Long prorationDate, BigDecimal amountDue) {}
