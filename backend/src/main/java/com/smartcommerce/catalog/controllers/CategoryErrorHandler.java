package com.smartcommerce.catalog.controllers;

import com.smartcommerce.catalog.exceptions.CategoryException;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = CategoryController.class)
public final class CategoryErrorHandler {
  @ExceptionHandler(CategoryException.class)
  ResponseEntity<Map<String, String>> category(CategoryException exception) {
    return switch (exception.reason()) {
      case MISSING -> ResponseEntity.status(404).body(Map.of("message", exception.getMessage()));
      case NAME_CONFLICT, SLUG_CONFLICT, LIMIT, DEPTH, CYCLE, HAS_CHILDREN ->
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
        .body(
            Map.of(
                "message",
                "Confira os dados: nome com até 60 caracteres, endereço com letras minúsculas,"
                    + " números e hífens."));
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String, String>> unavailable() {
    return ResponseEntity.status(503)
        .header("Retry-After", "5")
        .body(Map.of("message", "Serviço temporariamente indisponível."));
  }
}
