package com.smartcommerce.payments.controllers;

import com.smartcommerce.payments.domain.PaymentException;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(
    assignableTypes = {
      PaymentConnectionController.class,
      MercadoPagoCallbackController.class,
      PaymentInstallmentsController.class
    })
public final class PaymentErrorHandler {
  @ExceptionHandler(PaymentException.class)
  ResponseEntity<Map<String, String>> payment(PaymentException error) {
    return switch (error.reason()) {
      case UNAVAILABLE ->
          ResponseEntity.status(503)
              .cacheControl(CacheControl.noStore())
              .body(
                  Map.of(
                      "message",
                      "A conexão com o Mercado Pago não está disponível neste ambiente.",
                      "code",
                      "PAYMENTS_UNAVAILABLE"));
      case PROVIDER_ERROR ->
          ResponseEntity.status(502)
              .cacheControl(CacheControl.noStore())
              .body(
                  Map.of(
                      "message",
                      "O Mercado Pago não respondeu como esperado. Tente novamente em instantes.",
                      "code",
                      "PROVIDER_ERROR"));
      default ->
          ResponseEntity.status(404)
              .cacheControl(CacheControl.noStore())
              .body(Map.of("message", "Loja não encontrada."));
    };
  }

  /** Only the installment limit takes a body: an integer from 1 to 12, or null (SC-042). */
  @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
  ResponseEntity<Map<String, String>> invalidInstallments() {
    return ResponseEntity.badRequest()
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", "Escolha de 1 a 12 parcelas."));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<Map<String, String>> invalid() {
    return ResponseEntity.status(404)
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", "Loja não encontrada."));
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String, String>> unavailable() {
    return ResponseEntity.status(503)
        .header("Retry-After", "5")
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", "Serviço temporariamente indisponível."));
  }
}
