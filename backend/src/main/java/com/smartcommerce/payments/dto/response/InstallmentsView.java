package com.smartcommerce.payments.dto.response;

/** The store's installment limit (SC-042); {@code null}: Mercado Pago's default, none promised. */
public record InstallmentsView(Integer maxInstallments) {}
