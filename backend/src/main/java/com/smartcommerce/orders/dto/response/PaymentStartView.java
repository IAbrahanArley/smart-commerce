package com.smartcommerce.orders.dto.response;

/** Where the storefront redirects the buyer to pay (SC-013): a Mercado Pago HTTPS page. */
public record PaymentStartView(String checkoutUrl) {}
