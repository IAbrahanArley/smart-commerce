package com.smartcommerce.storefront.services;

import com.smartcommerce.storefront.domain.PublicFulfillment;
import com.smartcommerce.storefront.domain.PublicLocator;
import com.smartcommerce.storefront.domain.StorefrontException;
import com.smartcommerce.storefront.dto.response.PublicFulfillmentView;
import com.smartcommerce.storefront.repositories.PublicFulfillmentRepository;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicFulfillmentService {
  private final PublicFulfillmentRepository fulfillment;

  public PublicFulfillmentService(PublicFulfillmentRepository fulfillment) {
    this.fulfillment = fulfillment;
  }

  @Transactional(readOnly = true)
  public PublicFulfillmentView find(String slug) {
    return view(fulfillment.find(PublicLocator.slug(slug)).orElseThrow(StorefrontException::new));
  }

  private static PublicFulfillmentView view(PublicFulfillment fulfillment) {
    List<PublicFulfillmentView.Option> options = new ArrayList<>();
    if (fulfillment.pickupInstructions() != null)
      options.add(new PublicFulfillmentView.Pickup(fulfillment.pickupInstructions()));
    if (fulfillment.shippingAmount() != null)
      options.add(
          new PublicFulfillmentView.FixedShipping(
              fulfillment.shippingAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
              fulfillment.currency(),
              fulfillment.shippingArea()));
    return new PublicFulfillmentView(List.copyOf(options));
  }
}
