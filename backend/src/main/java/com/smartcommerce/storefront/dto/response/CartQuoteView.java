package com.smartcommerce.storefront.dto.response;

import java.util.List;

/**
 * {@code subtotal} sums AVAILABLE lines only; {@code purchasable} is true when every line is
 * AVAILABLE. Delivery options join this view after SC-006 (see CartQuoteService).
 */
public record CartQuoteView(
    String currency, List<CartQuoteLineView> lines, String subtotal, boolean purchasable) {}
