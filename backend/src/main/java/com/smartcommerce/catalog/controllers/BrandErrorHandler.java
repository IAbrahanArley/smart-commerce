package com.smartcommerce.catalog.controllers;

import com.smartcommerce.catalog.exceptions.BrandException;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = BrandController.class)
public final class BrandErrorHandler {
  @ExceptionHandler(BrandException.class)
  ResponseEntity<Map<String, String>> brand(BrandException exception) {
    return switch (exception.reason()) {
      case MISSING -> ResponseEntity.status(404).body(Map.of("message", exception.getMessage()));
      case NAME_CONFLICT, LIMIT ->
          ResponseEntity.status(409)
              .body(Map.of("message", exception.getMessage(), "code", exception.reason().name()));
    };
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<Map<String, String>> invalid() {
    return ResponseEntity.badRequest()
        .body(Map.of("message", "Informe um nome de marca com até 60 caracteres."));
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String, String>> unavailable() {
    return ResponseEntity.status(503)
        .header("Retry-After", "5")
        .body(Map.of("message", "Serviço temporariamente indisponível."));
  }
}
