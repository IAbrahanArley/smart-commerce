package com.smartcommerce.content.controllers;

import com.smartcommerce.catalog.domain.ImageException;
import com.smartcommerce.content.domain.ContentException;
import com.smartcommerce.content.domain.ContentLimitException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice(assignableTypes = HomeContentController.class)
public final class HomeContentErrorHandler {
  @ExceptionHandler(ContentException.class)
  ResponseEntity<Map<String, String>> missing(HttpServletRequest request) {
    return respond(request, 404, Map.of("message", "Banner, categoria ou loja não encontrados."));
  }

  @ExceptionHandler(ContentLimitException.class)
  ResponseEntity<Map<String, String>> limit(HttpServletRequest request) {
    return respond(
        request,
        409,
        Map.of("message", "A loja já tem o máximo de 5 banners.", "code", "BANNER_LIMIT"));
  }

  @ExceptionHandler(ImageException.class)
  ResponseEntity<Map<String, String>> image(ImageException error, HttpServletRequest request) {
    int status =
        switch (error.reason()) {
          case MISSING -> 404;
          case VERSION_CONFLICT, IMAGE_LIMIT -> 409;
          case INVALID -> 400;
          case TOO_LARGE -> 413;
          case BUSY -> 503;
        };
    return respond(request, status, Map.of("message", error.getMessage()));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<Map<String, String>> large(HttpServletRequest request) {
    return respond(request, 413, Map.of("message", "Envie uma imagem por vez, com até 5 MiB."));
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    MultipartException.class,
    MissingServletRequestPartException.class
  })
  ResponseEntity<Map<String, String>> invalid(HttpServletRequest request) {
    return respond(
        request,
        400,
        Map.of("message", "Confira os campos: algum valor não está no formato esperado."));
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String, String>> unavailable(HttpServletRequest request) {
    return respond(request, 503, Map.of("message", "Serviço temporariamente indisponível."));
  }

  /** Image GETs are requested with image-only Accept headers: they get the status alone. */
  private static ResponseEntity<Map<String, String>> respond(
      HttpServletRequest request, int status, Map<String, String> body) {
    var response = ResponseEntity.status(status).header("Cache-Control", "no-store");
    if (status == 503) response.header("Retry-After", "5");
    return "GET".equals(request.getMethod()) && request.getRequestURI().contains("/images/")
        ? response.build()
        : response.body(body);
  }
}
