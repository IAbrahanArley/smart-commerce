package com.smartcommerce.storefront.controllers;

import com.smartcommerce.storefront.domain.StorefrontException;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Draft, missing and malformed slugs share one public 404. */
@RestControllerAdvice(assignableTypes = PublicFulfillmentController.class)
public final class PublicFulfillmentErrorHandler {
  @ExceptionHandler(StorefrontException.class)
  ResponseEntity<Map<String, String>> missing() {
    return ResponseEntity.status(404)
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", "Loja não encontrada."));
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String, String>> unavailable() {
    return ResponseEntity.status(503)
        .cacheControl(CacheControl.noStore())
        .header("Retry-After", "5")
        .body(Map.of("message", "Serviço temporariamente indisponível."));
  }
}
