package com.smartcommerce.tenant.controllers;

import com.smartcommerce.identity.exceptions.IdentityException;
import com.smartcommerce.tenant.exceptions.StaffException;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Employee outcomes (SC-049). A company that is not the actor's own is one 404, like elsewhere; a
 * failed invitation e-mail is 503 and nothing is kept. Plan limits go through BillingErrorHandler.
 */
@RestControllerAdvice(assignableTypes = StaffController.class)
public final class StaffErrorHandler {
  @ExceptionHandler(StaffException.class)
  ResponseEntity<Map<String, String>> staff(StaffException exception) {
    int status =
        switch (exception.reason()) {
          case MISSING -> 404;
          case INVALID_EMAIL -> 400;
          case ALREADY_MEMBER -> 409;
          case INVALID_INVITATION -> 410;
          case WRONG_ACCOUNT -> 403;
          case THROTTLED -> 429;
          case OWNER_LIMIT, CONFLICT -> 409;
        };
    return reply(status, exception.getMessage(), exception.reason().name());
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<Map<String, String>> conflict() {
    // Two invitations to the same e-mail at once: the open-invitation index keeps one.
    return reply(
        409, "Já existe um convite sendo enviado para esse e-mail. Confira a lista.", "CONFLICT");
  }

  @ExceptionHandler(IdentityException.class)
  ResponseEntity<Map<String, String>> identity(IdentityException exception) {
    return exception.reason() == IdentityException.Reason.UNAVAILABLE
        ? reply(503, "Não foi possível enviar o convite agora. Tente novamente.", "UNAVAILABLE")
        : reply(401, "Sessão inválida.", "INVALID_SESSION");
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<Map<String, String>> invalid() {
    return reply(400, "Requisição inválida.", "INVALID_REQUEST");
  }

  private static ResponseEntity<Map<String, String>> reply(
      int status, String message, String code) {
    return ResponseEntity.status(status)
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", message, "code", code));
  }
}
