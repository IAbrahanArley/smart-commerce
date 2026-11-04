package com.smartcommerce.finance.controllers;

import com.smartcommerce.finance.domain.CreditException;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Store credit errors (SC-055): foreign and absent stores, customers or accounts are one 404. */
@RestControllerAdvice(assignableTypes = CreditController.class)
public final class CreditErrorHandler {
  @ExceptionHandler(CreditException.class)
  ResponseEntity<Map<String, String>> credit(CreditException error) {
    return switch (error.reason()) {
      case REQUEST_CONFLICT ->
          reply(
              409,
              "Esta tentativa já registrou outro recebimento. Recarregue a página antes de lançar"
                  + " de novo.",
              "REQUEST_CONFLICT");
      case SESSION_CLOSED ->
          reply(409, "O caixa escolhido não está aberto. Abra o caixa antes.", "SESSION_CLOSED");
      case ACCOUNT_NOT_ALLOWED ->
          reply(
              409, "Receba no caixa aberto ou numa conta bancária da loja.", "ACCOUNT_NOT_ALLOWED");
      case OVERPAYMENT -> reply(409, "O valor passa do que o cliente deve.", "OVERPAYMENT");
      case MISSING -> reply(404, "Cliente, conta ou loja não encontrado.", "MISSING");
    };
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<Map<String, String>> invalid() {
    return reply(400, "Confira os dados enviados.", "INVALID_REQUEST");
  }

  /** A database guard refused the write (SC-055 triggers and constraints): nothing was saved. */
  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  ResponseEntity<Map<String, String>> refused() {
    return reply(
        409, "Os dados mudaram enquanto você registrava. Recarregue a página.", "DATA_CHANGED");
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String, String>> unavailable() {
    return ResponseEntity.status(503)
        .header("Retry-After", "5")
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", "Serviço temporariamente indisponível."));
  }

  private static ResponseEntity<Map<String, String>> reply(
      int status, String message, String code) {
    return ResponseEntity.status(status)
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", message, "code", code));
  }
}
