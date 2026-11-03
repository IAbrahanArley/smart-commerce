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

/** Cash register errors (SC-052): foreign and absent stores, drawers or sessions are one 404. */
@RestControllerAdvice(assignableTypes = CashController.class)
public final class CashErrorHandler {
  @ExceptionHandler(FinanceException.class)
  ResponseEntity<Map<String, String>> finance(FinanceException error) {
    return switch (error.reason()) {
      case REQUEST_CONFLICT ->
          reply(
              409,
              "Esta tentativa já registrou outro lançamento. Recarregue a página antes de lançar de"
                  + " novo.",
              "REQUEST_CONFLICT");
      case SESSION_OPEN -> reply(409, "Este caixa já está aberto.", "SESSION_OPEN");
      case SESSION_CLOSED ->
          reply(409, "Este caixa já foi fechado. Recarregue a página.", "SESSION_CLOSED");
      case SAME_ACCOUNT ->
          reply(400, "Escolha uma conta diferente da gaveta deste caixa.", "SAME_ACCOUNT");
      case PROVIDER_READ_ONLY ->
          reply(
              409,
              "A conta do Mercado Pago não recebe nem envia dinheiro manualmente.",
              "PROVIDER_READ_ONLY");
      default -> reply(404, "Caixa ou loja não encontrado.", "MISSING");
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
