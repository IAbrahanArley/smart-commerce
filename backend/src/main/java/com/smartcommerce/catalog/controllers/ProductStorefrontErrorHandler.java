package com.smartcommerce.catalog.controllers;

import com.smartcommerce.catalog.exceptions.CatalogException;
import com.smartcommerce.catalog.exceptions.ProductStorefrontException;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = ProductStorefrontController.class)
public final class ProductStorefrontErrorHandler {
  @ExceptionHandler(ProductStorefrontException.class)
  ResponseEntity<Map<String, String>> storefront(ProductStorefrontException exception) {
    return switch (exception.reason()) {
      case MISSING -> ResponseEntity.status(404).body(Map.of("message", exception.getMessage()));
      case SLUG_CONFLICT ->
          ResponseEntity.status(409)
              .body(Map.of("message", exception.getMessage(), "code", exception.reason().name()));
      case COMPARE_AT_NOT_ABOVE ->
          ResponseEntity.badRequest()
              .body(Map.of("message", exception.getMessage(), "code", exception.reason().name()));
    };
  }

  /** Reading the saved product back (CatalogService) can only fail as a missing product. */
  @ExceptionHandler(CatalogException.class)
  ResponseEntity<Map<String, String>> catalog(CatalogException exception) {
    return ResponseEntity.status(404).body(Map.of("message", exception.getMessage()));
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<Map<String, String>> invalid() {
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "message",
                "Confira o endereço (letras sem acento, números e hífens) e os preços em reais."));
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String, String>> unavailable() {
    return ResponseEntity.status(503)
        .header("Retry-After", "5")
        .body(Map.of("message", "Serviço temporariamente indisponível."));
  }
}
