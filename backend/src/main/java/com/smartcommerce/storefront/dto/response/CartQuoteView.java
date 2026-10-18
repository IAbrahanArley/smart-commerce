package com.smartcommerce.storefront.dto.response;

import java.util.List;

/**
 * {@code subtotal} sums AVAILABLE lines only; {@code purchasable} is true when every line is
 * AVAILABLE. {@code delivery} lists the store's enabled options (SC-006) with their fees; the buyer
 * picks one and the checkout total is subtotal plus the chosen fee.
 */
public record CartQuoteView(
    String currency,
    List<CartQuoteLineView> lines,
    String subtotal,
    boolean purchasable,
    List<PublicFulfillmentView.Option> delivery) {}
