package com.smartcommerce.catalog.dto.response;

import java.util.List;
import java.util.UUID;

/** Decimal strings preserve the BRL contract without binary floating-point conversion. */
public record ProductView(
    UUID id,
    String name,
    String sku,
    String price,
    String cost,
    String currency,
    long version,
    boolean published,
    String descriptionHtml,
    String kind,
    List<VariantView> variants,
    UUID coverImageId,
    String coverImageAlt,
    int imageCount,
    UUID categoryId) {}
