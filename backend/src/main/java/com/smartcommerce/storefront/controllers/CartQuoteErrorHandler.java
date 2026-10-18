package com.smartcommerce.storefront.controllers;

import com.smartcommerce.storefront.domain.StorefrontException;
import java.io.IOException;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = CartQuoteController.class)
public final class CartQuoteErrorHandler {
  @ExceptionHandler(StorefrontException.class)
  ResponseEntity<Map<String, String>> missing() {
    return ResponseEntity.status(404)
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", "Loja não encontrada."));
  }

  @ExceptionHandler({IllegalArgumentException.class, IOException.class})
  ResponseEntity<Map<String, String>> invalid() {
    return ResponseEntity.badRequest()
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", "Carrinho inválido."));
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String, String>> unavailable() {
    return ResponseEntity.status(503)
        .cacheControl(CacheControl.noStore())
        .header("Retry-After", "5")
        .body(Map.of("message", "Serviço temporariamente indisponível."));
  }
}
