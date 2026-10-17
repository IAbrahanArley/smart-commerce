package com.smartcommerce.catalog.controllers;

import com.smartcommerce.catalog.domain.ImageException;
import com.smartcommerce.catalog.exceptions.CatalogException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice(assignableTypes = ProductImageController.class)
public class ProductImageErrors {
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
    var body =
        status == 409
            ? Map.of("message", error.getMessage(), "code", error.reason().name())
            : Map.of("message", error.getMessage());
    return respond(request, status, body);
  }

  @ExceptionHandler(CatalogException.class)
  ResponseEntity<Map<String, String>> missing(HttpServletRequest request) {
    return respond(request, 404, Map.of("message", "Imagem ou produto indisponível."));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<Map<String, String>> large(HttpServletRequest request) {
    return respond(request, 413, Map.of("message", "Envie uma imagem por vez, com até 5 MiB."));
  }

  @ExceptionHandler({
    MultipartException.class,
    IllegalArgumentException.class,
    MissingServletRequestPartException.class,
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class
  })
  ResponseEntity<Map<String, String>> invalid(HttpServletRequest request) {
    return respond(
        request,
        400,
        Map.of("message", "Confira a imagem, descrição alternativa e versão do produto."));
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String, String>> unavailable(HttpServletRequest request) {
    return respond(request, 503, Map.of("message", "Serviço temporariamente indisponível."));
  }

  /**
   * Image content routes are requested with image-only Accept headers (img tags, the storefront
   * proxy). A JSON body cannot be negotiated there and would be rerouted to the denied error
   * dispatch, turning a uniform 404 into 403. Those routes answer with the status alone.
   */
  private static ResponseEntity<Map<String, String>> respond(
      HttpServletRequest request, int status, Map<String, String> body) {
    var response = ResponseEntity.status(status).header("Cache-Control", "no-store");
    return request.getRequestURI().endsWith("/content") ? response.build() : response.body(body);
  }
}
