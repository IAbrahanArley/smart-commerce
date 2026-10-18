package com.smartcommerce.inventory.controllers;

import com.smartcommerce.catalog.exceptions.CatalogException;
import com.smartcommerce.inventory.domain.InventoryException;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = InventoryController.class)
public final class InventoryErrorHandler {
  @ExceptionHandler(InventoryException.class)
  ResponseEntity<Map<String, Object>> inventory(InventoryException exception) {
    String message =
        switch (exception.reason()) {
          case MISSING -> "Produto ou loja não encontrado.";
          case INSUFFICIENT_STOCK -> "Saldo insuficiente para esse ajuste.";
          case RESERVED_STOCK ->
              "O saldo físico não pode ficar abaixo das unidades reservadas por pedidos pendentes.";
          case BALANCE_LIMIT -> "A quantidade ultrapassa o limite permitido.";
          case REQUEST_CONFLICT -> "Esta solicitação já foi utilizada com outros dados.";
        };
    int status = exception.reason() == InventoryException.Reason.MISSING ? 404 : 409;
    if (status == 404) return ResponseEntity.status(status).body(Map.of("message", message));
    if (exception.reason() == InventoryException.Reason.RESERVED_STOCK)
      return ResponseEntity.status(status)
          .body(
              Map.of(
                  "message",
                  message,
                  "code",
                  exception.reason().name(),
                  "reserved",
                  exception.reserved()));
    return ResponseEntity.status(status)
        .body(Map.of("message", message, "code", exception.reason().name()));
  }

  @ExceptionHandler(CatalogException.class)
  ResponseEntity<Map<String, String>> missing(CatalogException exception) {
    if (exception.reason() == CatalogException.Reason.VARIANT_REQUIRED)
      return ResponseEntity.status(409)
          .body(Map.of("message", exception.getMessage(), "code", "VARIANT_REQUIRED"));
    return ResponseEntity.status(404).body(Map.of("message", "Produto ou loja não encontrado."));
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
                "Dados inválidos. Informe quantidade inteira e motivo de até 240 caracteres."));
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String, String>> unavailable() {
    return ResponseEntity.status(503)
        .header("Retry-After", "5")
        .body(Map.of("message", "Serviço temporariamente indisponível."));
  }
}
