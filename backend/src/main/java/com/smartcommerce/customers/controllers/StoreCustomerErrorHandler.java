package com.smartcommerce.customers.controllers;

import com.smartcommerce.customers.domain.CustomerException;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Store customer errors (SC-055): foreign and absent stores or customers are one 404. */
@RestControllerAdvice(assignableTypes = StoreCustomerController.class)
public final class StoreCustomerErrorHandler {
  @ExceptionHandler(CustomerException.class)
  ResponseEntity<Map<String, String>> customer(CustomerException error) {
    return switch (error.reason()) {
      case REQUEST_CONFLICT ->
          reply(
              409,
              "Esta tentativa já cadastrou outro cliente. Recarregue a página antes de cadastrar de"
                  + " novo.",
              "REQUEST_CONFLICT");
      case EMAIL_TAKEN -> reply(409, "Outro cliente da loja já usa este e-mail.", "EMAIL_TAKEN");
      case MISSING -> reply(404, "Cliente ou loja não encontrado.", "MISSING");
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
