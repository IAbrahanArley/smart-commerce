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

/** Finance errors: foreign and absent stores or expenses are the same 404 (SC-020). */
@RestControllerAdvice(assignableTypes = {ExpenseController.class, FinanceReportController.class})
public final class FinanceErrorHandler {
  @ExceptionHandler(FinanceException.class)
  ResponseEntity<Map<String, String>> finance(FinanceException error) {
    return switch (error.reason()) {
      case REQUEST_CONFLICT ->
          conflict(
              "Esta tentativa já registrou outra despesa. Recarregue a lista antes de lançar de"
                  + " novo.",
              "REQUEST_CONFLICT");
      case ALREADY_PAID ->
          conflict(
              "Esta conta já foi paga com outros dados. Recarregue a lista para ver o pagamento.",
              "ALREADY_PAID");
      case NOT_PAYABLE -> conflict("Uma conta cancelada não pode ser paga.", "NOT_PAYABLE");
      case PROVIDER_READ_ONLY ->
          conflict(
              "A conta do Mercado Pago não paga contas manualmente. Escolha outra conta.",
              "PROVIDER_READ_ONLY");
      default ->
          ResponseEntity.status(404)
              .cacheControl(CacheControl.noStore())
              .body(Map.of("message", "Despesa, conta ou loja não encontrada."));
    };
  }

  private static ResponseEntity<Map<String, String>> conflict(String message, String code) {
    return ResponseEntity.status(409)
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", message, "code", code));
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<Map<String, String>> invalid() {
    return ResponseEntity.badRequest()
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", "Confira os dados enviados."));
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String, String>> unavailable() {
    return ResponseEntity.status(503)
        .header("Retry-After", "5")
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", "Serviço temporariamente indisponível."));
  }
}
