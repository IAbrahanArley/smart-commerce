package com.smartcommerce.catalog.dto.response;

import com.smartcommerce.catalog.domain.ProductImageData;
import java.util.List;

public record ProductImagesView(List<ProductImageData> images, long version) {}
