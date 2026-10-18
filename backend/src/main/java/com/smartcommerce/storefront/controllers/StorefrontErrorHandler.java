package com.smartcommerce.storefront.controllers;

import com.smartcommerce.storefront.domain.StorefrontException;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = StorefrontController.class)
public final class StorefrontErrorHandler {
  @ExceptionHandler(StorefrontException.class)
  ResponseEntity<Map<String, String>> missing() {
    return ResponseEntity.status(404)
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", "Loja ou produto não encontrado."));
  }

  @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
  ResponseEntity<Map<String, String>> invalid() {
    return ResponseEntity.badRequest()
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", "Solicitação inválida."));
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String, String>> unavailable() {
    return ResponseEntity.status(503)
        .cacheControl(CacheControl.noStore())
        .header("Retry-After", "5")
        .body(Map.of("message", "Serviço temporariamente indisponível."));
  }
}
