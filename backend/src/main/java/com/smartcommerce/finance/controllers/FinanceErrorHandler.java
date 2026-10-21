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
@RestControllerAdvice(assignableTypes = ExpenseController.class)
public final class FinanceErrorHandler {
  @ExceptionHandler(FinanceException.class)
  ResponseEntity<Map<String, String>> finance(FinanceException error) {
    if (error.reason() == FinanceException.Reason.REQUEST_CONFLICT)
      return ResponseEntity.status(409)
          .cacheControl(CacheControl.noStore())
          .body(
              Map.of(
                  "message",
                  "Esta tentativa já registrou outra despesa. Recarregue a lista antes de lançar de novo.",
                  "code",
                  "REQUEST_CONFLICT"));
    return ResponseEntity.status(404)
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", "Despesa ou loja não encontrada."));
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<Map<String, String>> invalid() {
    return ResponseEntity.badRequest()
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", "Confira os dados da despesa."));
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String, String>> unavailable() {
    return ResponseEntity.status(503)
        .header("Retry-After", "5")
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", "Serviço temporariamente indisponível."));
  }
}
