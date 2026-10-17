package com.smartcommerce.catalog.controllers;

import com.smartcommerce.catalog.exceptions.CatalogException;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = CatalogController.class)
public final class CatalogErrorHandler {
  @ExceptionHandler(CatalogException.class)
  ResponseEntity<Map<String, String>> catalog(CatalogException exception) {
    int status = exception.reason() == CatalogException.Reason.MISSING ? 404 : 409;
    String code = status == 404 ? null : exception.reason().name();
    var body =
        code == null
            ? Map.of("message", exception.getMessage())
            : Map.of("message", exception.getMessage(), "code", code);
    return ResponseEntity.status(status).body(body);
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
                "Dados inválidos. Confira nome, SKU e valores em reais com até duas casas decimais. O preço deve ser maior que zero."));
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String, String>> unavailable() {
    return ResponseEntity.status(503)
        .header("Retry-After", "5")
        .body(Map.of("message", "Serviço temporariamente indisponível."));
  }
}
