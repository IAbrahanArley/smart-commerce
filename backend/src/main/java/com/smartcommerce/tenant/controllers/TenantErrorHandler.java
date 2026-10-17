package com.smartcommerce.tenant.controllers;

import com.smartcommerce.tenant.exceptions.TenantException;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = TenantController.class)
public final class TenantErrorHandler {
  @ExceptionHandler(TenantException.class)
  ResponseEntity<Map<String, String>> tenant(TenantException exception) {
    int status =
        switch (exception.reason()) {
          case MISSING -> 404;
          case SLUG_CONFLICT -> 409;
          case INVALID_IDENTITY -> 401;
        };
    return ResponseEntity.status(status).body(Map.of("message", exception.getMessage()));
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
                "Dados inválidos. Use nomes de 1 a 120 caracteres e um endereço de 3 a 63 letras minúsculas, números ou hífens entre palavras."));
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String, String>> unavailable() {
    return ResponseEntity.status(503)
        .header("Retry-After", "5")
        .body(Map.of("message", "Serviço temporariamente indisponível."));
  }
}
