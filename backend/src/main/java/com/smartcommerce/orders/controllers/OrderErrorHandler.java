package com.smartcommerce.orders.controllers;

import com.smartcommerce.orders.domain.OrderException;
import java.io.IOException;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public checkout errors. Messages are generic and never echo buyer data, stock quantities or
 * internal identifiers; conflict codes let the storefront react (new quote, retry the same key).
 */
@RestControllerAdvice(assignableTypes = OrderController.class)
public final class OrderErrorHandler {
  @ExceptionHandler(OrderException.class)
  ResponseEntity<Map<String, String>> order(OrderException error) {
    Map<String, String> body = new LinkedHashMap<>();
    int status =
        switch (error.reason()) {
          case MISSING -> 404;
          case RATE_LIMITED, PENDING_LIMIT -> 429;
          case PAYMENT_FAILED -> 502;
          default -> 409;
        };
    body.put(
        "message",
        switch (error.reason()) {
          case MISSING -> "Pedido ou loja não encontrado.";
          case ITEM_UNAVAILABLE -> "Algum item não está mais disponível. Confira o carrinho.";
          case FULFILLMENT_UNAVAILABLE -> "A forma de entrega escolhida não está disponível.";
          case PRICE_CHANGED -> "O total mudou. Confira os valores atualizados antes de confirmar.";
          case INSUFFICIENT_STOCK -> "Não há estoque suficiente para algum item.";
          case REQUEST_CONFLICT -> "Esta tentativa de compra já foi usada com outros dados.";
          case PENDING_LIMIT -> "Há pedidos pendentes demais para este e-mail nesta loja.";
          case RATE_LIMITED -> "Muitas tentativas de compra. Tente novamente mais tarde.";
          case ORDER_EXPIRED -> "O prazo deste pedido terminou.";
          case NOT_PAYABLE -> "Este pedido não está mais aguardando pagamento.";
          case PAYMENT_UNAVAILABLE -> "Esta loja não está recebendo pagamentos agora.";
          case PAYMENT_FAILED -> "Não foi possível iniciar o pagamento. Tente novamente.";
          case NOT_REFUNDABLE, REFUND_CONFLICT -> "Pedido ou loja não encontrado.";
        });
    if (status == 409 || status == 502) body.put("code", error.reason().name());
    if (error.currentTotal() != null)
      body.put(
          "currentTotal",
          error.currentTotal().setScale(2, RoundingMode.UNNECESSARY).toPlainString());
    var response = ResponseEntity.status(status).cacheControl(CacheControl.noStore());
    if (status == 429) response.header("Retry-After", "3600");
    return response.body(body);
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    IOException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class
  })
  ResponseEntity<Map<String, String>> invalid() {
    return ResponseEntity.badRequest()
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", "Confira os dados do pedido."));
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String, String>> unavailable() {
    return ResponseEntity.status(503)
        .cacheControl(CacheControl.noStore())
        .header("Retry-After", "5")
        .body(Map.of("message", "Serviço temporariamente indisponível."));
  }
}
