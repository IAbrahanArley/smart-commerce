package com.smartcommerce.storefront.controllers;

import com.smartcommerce.storefront.dto.response.PublicFulfillmentView;
import com.smartcommerce.storefront.services.PublicFulfillmentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Public storefront")
public final class PublicFulfillmentController {
  private final PublicFulfillmentService fulfillment;

  public PublicFulfillmentController(PublicFulfillmentService fulfillment) {
    this.fulfillment = fulfillment;
  }

  @GetMapping("/api/public/stores/{slug}/fulfillment")
  public ResponseEntity<PublicFulfillmentView> find(@PathVariable String slug) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(fulfillment.find(slug));
  }
}
