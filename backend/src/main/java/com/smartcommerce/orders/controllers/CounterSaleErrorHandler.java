package com.smartcommerce.orders.controllers;

import com.smartcommerce.orders.domain.CounterSaleException;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Counter sale errors (SC-053): foreign and absent stores, sales or products are one 404. */
@RestControllerAdvice(assignableTypes = CounterSaleController.class)
public final class CounterSaleErrorHandler {
  @ExceptionHandler(CounterSaleException.class)
  ResponseEntity<Map<String, String>> sale(CounterSaleException error) {
    return switch (error.reason()) {
      case REQUEST_CONFLICT ->
          reply(
              409,
              "Esta tentativa já registrou outra venda. Recarregue a página antes de lançar de novo.",
              "REQUEST_CONFLICT");
      case INSUFFICIENT_STOCK ->
          reply(
              409,
              "Algum item não tem estoque disponível suficiente. Confira as quantidades.",
              "INSUFFICIENT_STOCK");
      case DISCOUNT_LIMIT ->
          reply(409, "O desconto passa do limite definido pelo dono da loja.", "DISCOUNT_LIMIT");
      case PIX_NOT_CONFIGURED ->
          reply(
              409,
              "O dono da loja ainda não escolheu a conta que recebe o Pix.",
              "PIX_NOT_CONFIGURED");
      case SESSION_CLOSED ->
          reply(409, "O caixa escolhido não está aberto. Abra o caixa antes.", "SESSION_CLOSED");
      case PROVIDER_READ_ONLY ->
          reply(
              409, "Escolha uma conta bancária da loja para receber o Pix.", "PROVIDER_READ_ONLY");
      case MISSING -> reply(404, "Venda, produto ou loja não encontrado.", "MISSING");
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
