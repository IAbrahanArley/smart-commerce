package com.smartcommerce.orders.controllers;

import com.smartcommerce.orders.domain.OrderException;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Admin order errors: foreign and absent orders are the same 404; no buyer data is echoed. */
@RestControllerAdvice(assignableTypes = OrderAdminController.class)
public final class OrderAdminErrorHandler {
  @ExceptionHandler(OrderException.class)
  ResponseEntity<Map<String, String>> order(OrderException error) {
    if (error.reason() == OrderException.Reason.ORDER_EXPIRED)
      return ResponseEntity.status(409)
          .cacheControl(CacheControl.noStore())
          .body(
              Map.of(
                  "message",
                  "O prazo deste pedido já terminou e as reservas foram liberadas. Não é preciso cancelar.",
                  "code",
                  "ORDER_EXPIRED"));
    return ResponseEntity.status(404)
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", "Pedido ou loja não encontrado."));
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<Map<String, String>> invalid() {
    return ResponseEntity.badRequest()
        .cacheControl(CacheControl.noStore())
        .body(
            Map.of(
                "message",
                "Dados inválidos. Informe o motivo do cancelamento em uma linha, com 3 a 300 caracteres."));
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String, String>> unavailable() {
    return ResponseEntity.status(503)
        .header("Retry-After", "5")
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", "Serviço temporariamente indisponível."));
  }
}
