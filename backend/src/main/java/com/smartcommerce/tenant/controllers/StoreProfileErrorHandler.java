package com.smartcommerce.tenant.controllers;

import com.smartcommerce.catalog.domain.ImageException;
import com.smartcommerce.tenant.exceptions.StoreProfileException;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice(assignableTypes = StoreProfileController.class)
public final class StoreProfileErrorHandler {
  @ExceptionHandler(StoreProfileException.class)
  ResponseEntity<Map<String, String>> profile(StoreProfileException exception) {
    return switch (exception.reason()) {
      case MISSING, NO_LOGO -> respond(404, Map.of("message", exception.getMessage()));
      case VERSION_CONFLICT ->
          respond(409, Map.of("message", exception.getMessage(), "code", "VERSION_CONFLICT"));
    };
  }

  @ExceptionHandler(ImageException.class)
  ResponseEntity<Map<String, String>> image(ImageException exception) {
    int status =
        switch (exception.reason()) {
          case MISSING -> 404;
          case VERSION_CONFLICT, IMAGE_LIMIT -> 409;
          case INVALID -> 400;
          case TOO_LARGE -> 413;
          case BUSY -> 503;
        };
    return respond(status, Map.of("message", exception.getMessage()));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<Map<String, String>> large() {
    return respond(413, Map.of("message", "Envie uma imagem de até 5 MiB."));
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    MultipartException.class,
    MissingServletRequestPartException.class,
    MissingServletRequestParameterException.class
  })
  ResponseEntity<Map<String, String>> invalid() {
    return respond(
        400,
        Map.of(
            "message",
            "Dados inválidos. Confira o nome (até 120 caracteres), o e-mail, o WhatsApp com DDD e o"
                + " usuário do Instagram."));
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String, String>> unavailable() {
    return ResponseEntity.status(503)
        .header("Retry-After", "5")
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", "Serviço temporariamente indisponível."));
  }

  private static ResponseEntity<Map<String, String>> respond(int status, Map<String, String> body) {
    return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
  }
}
