package com.smartcommerce.payments.controllers;

import com.smartcommerce.identity.config.IdentityConfiguration;
import com.smartcommerce.payments.domain.PaymentException;
import com.smartcommerce.payments.services.PaymentConnectionService;
import io.swagger.v3.oas.annotations.Hidden;
import java.net.URI;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where Mercado Pago sends the seller back (the application's static redirect URI, on the admin
 * origin). The session identifies the user; the single-use state identifies the store and must have
 * been started by that same user. The answer is always a redirect to a fixed admin page built from
 * the configured origin and the stored tenant, never from request input, and it carries only a
 * result word, never the code or the state.
 */
@Hidden
@RestController
@RequestMapping("/api/payments/mercado-pago")
public final class MercadoPagoCallbackController {
  private final PaymentConnectionService connections;
  private final String origin;

  public MercadoPagoCallbackController(
      PaymentConnectionService connections,
      @Value("${identity.public-origin:http://127.0.0.1:3000}") String origin) {
    this.connections = connections;
    this.origin = IdentityConfiguration.validatedOrigin(origin);
  }

  @GetMapping("/callback")
  public ResponseEntity<Void> callback(
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String error,
      Authentication actor) {
    var completion =
        connections.complete(UUID.fromString(actor.getName()), state, code, error != null);
    String result = completion.succeeded() ? "conectado" : word(completion.failure());
    String path =
        completion.tenant() == null
            ? "/lojas?mercadoPago=" + result
            : "/lojas/" + completion.tenant() + "/pagamentos?mercadoPago=" + result;
    return ResponseEntity.status(HttpStatus.SEE_OTHER)
        .location(URI.create(origin + path))
        .cacheControl(CacheControl.noStore())
        .build();
  }

  private static String word(PaymentException.Reason reason) {
    return switch (reason) {
      case DENIED -> "negado";
      case INVALID_STATE, MISSING -> "expirado";
      case UNAVAILABLE -> "indisponivel";
      default -> "erro";
    };
  }
}
