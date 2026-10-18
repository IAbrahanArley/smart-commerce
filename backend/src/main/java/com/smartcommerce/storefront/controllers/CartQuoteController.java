package com.smartcommerce.storefront.controllers;

import com.smartcommerce.storefront.dto.request.CartQuoteRequest;
import com.smartcommerce.storefront.services.CartQuoteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Public, stateless and side-effect-free quote of an untrusted storefront cart. */
@RestController
@Tag(name = "Public storefront")
@RequestMapping("/api/public/stores/{slug}/cart")
public final class CartQuoteController {
  private final CartQuoteService quotes;

  public CartQuoteController(CartQuoteService quotes) {
    this.quotes = quotes;
  }

  @PostMapping(
      path = "/quote",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> quote(@PathVariable String slug, InputStream body) throws IOException {
    // Bounded read: never buffer more than the limit, whatever Content-Length claims.
    byte[] content = body.readNBytes(CartQuoteRequest.MAX_BYTES + 1);
    if (content.length > CartQuoteRequest.MAX_BYTES)
      return ResponseEntity.status(413)
          .cacheControl(CacheControl.noStore())
          .body(Map.of("message", "Carrinho grande demais."));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(quotes.quote(slug, CartQuoteRequest.parse(content).lines()));
  }
}
