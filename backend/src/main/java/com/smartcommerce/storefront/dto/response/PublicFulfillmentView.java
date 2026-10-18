package com.smartcommerce.storefront.dto.response;

import java.util.List;

/** Only enabled options are listed; the shipping area is informative, never a coverage check. */
public record PublicFulfillmentView(List<Option> options) {
  public sealed interface Option permits Pickup, FixedShipping {
    String type();
  }

  public record Pickup(String type, String instructions) implements Option {
    public Pickup(String instructions) {
      this("PICKUP", instructions);
    }
  }

  public record FixedShipping(String type, String amount, String currency, String area)
      implements Option {
    public FixedShipping(String amount, String currency, String area) {
      this("FIXED_SHIPPING", amount, currency, area);
    }
  }
}
