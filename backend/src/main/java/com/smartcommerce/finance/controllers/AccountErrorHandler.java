package com.smartcommerce.finance.controllers;

import com.smartcommerce.finance.domain.FinanceException;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Account outcomes (SC-050): absent and not owned are one 404; messages never echo input. */
@RestControllerAdvice(assignableTypes = AccountController.class)
public final class AccountErrorHandler {
  @ExceptionHandler(FinanceException.class)
  ResponseEntity<Map<String, String>> finance(FinanceException error) {
    return switch (error.reason()) {
      case REQUEST_CONFLICT ->
          reply(
              409,
              "Esta tentativa já registrou outro lançamento. Recarregue a página antes de lançar de"
                  + " novo.",
              "REQUEST_CONFLICT");
      case ACCOUNT_LIMIT -> reply(409, "A loja chegou ao limite de 20 contas.", "ACCOUNT_LIMIT");
      case SAME_ACCOUNT ->
          reply(400, "Escolha contas diferentes para a transferência.", "SAME_ACCOUNT");
      case PROVIDER_READ_ONLY ->
          reply(
              409,
              "A conta do Mercado Pago segue os pagamentos do próprio Mercado Pago e não aceita"
                  + " lançamentos manuais.",
              "PROVIDER_READ_ONLY");
      case MISSING, ALREADY_PAID, NOT_PAYABLE, SESSION_OPEN, SESSION_CLOSED ->
          reply(404, "Conta ou loja não encontrada.", "MISSING");
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

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String, String>> unavailable() {
    return reply(503, "Serviço temporariamente indisponível.", "UNAVAILABLE");
  }

  private static ResponseEntity<Map<String, String>> reply(
      int status, String message, String code) {
    return ResponseEntity.status(status)
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", message, "code", code));
  }
}
