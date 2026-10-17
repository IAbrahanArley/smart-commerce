package com.smartcommerce.identity.controllers;

import com.smartcommerce.identity.exceptions.IdentityException;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = IdentityController.class)
public final class IdentityErrorHandler {
  @ExceptionHandler(IdentityException.class)
  ResponseEntity<Map<String, String>> identity(IdentityException exception) {
    int status =
        switch (exception.reason()) {
          case INVALID_PROOF -> 400;
          case INVALID_CREDENTIALS -> 401;
          case THROTTLED -> 429;
          case UNAVAILABLE -> 503;
        };
    var response = ResponseEntity.status(status);
    if (status == 429) response.header("Retry-After", "900");
    if (status == 503) response.header("Retry-After", "5");
    return response.body(Map.of("message", exception.getMessage()));
  }

  @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
  ResponseEntity<Map<String, String>> invalid() {
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "message",
                "Dados inválidos. Confira o e-mail e a política de senha (15 a 128 caracteres Unicode)."));
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String, String>> persistence() {
    return ResponseEntity.status(503)
        .header("Retry-After", "5")
        .body(Map.of("message", "Serviço temporariamente indisponível."));
  }
}
