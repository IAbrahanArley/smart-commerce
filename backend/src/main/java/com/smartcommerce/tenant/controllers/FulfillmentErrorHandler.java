package com.smartcommerce.tenant.controllers;

import com.smartcommerce.tenant.exceptions.FulfillmentException;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = FulfillmentController.class)
public final class FulfillmentErrorHandler {
  @ExceptionHandler(FulfillmentException.class)
  ResponseEntity<Map<String, String>> fulfillment(FulfillmentException exception) {
    if (exception.reason() == FulfillmentException.Reason.MISSING)
      return ResponseEntity.status(404).body(Map.of("message", exception.getMessage()));
    return ResponseEntity.status(409)
        .body(Map.of("message", exception.getMessage(), "code", exception.reason().name()));
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
                "Dados inválidos. Opções ativas exigem texto; textos têm até 500 caracteres, sem quebras de linha, e o frete fixo usa reais com até duas casas decimais, zero ou maior."));
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String, String>> unavailable() {
    return ResponseEntity.status(503)
        .header("Retry-After", "5")
        .body(Map.of("message", "Serviço temporariamente indisponível."));
  }
}
