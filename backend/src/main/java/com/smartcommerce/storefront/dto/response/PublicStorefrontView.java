package com.smartcommerce.storefront.dto.response;

import java.util.List;

public record PublicStorefrontView(
    String name, String slug, List<PublicProductView> products, String nextCursor) {}
