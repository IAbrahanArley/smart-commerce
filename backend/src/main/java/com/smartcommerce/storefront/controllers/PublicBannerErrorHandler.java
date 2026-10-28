package com.smartcommerce.storefront.controllers;

import com.smartcommerce.storefront.domain.StorefrontException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Banner images are requested by img tags, with image-only Accept headers: a JSON body could not be
 * negotiated, so errors answer with the status alone.
 */
@RestControllerAdvice(assignableTypes = PublicBannerController.class)
public final class PublicBannerErrorHandler {
  @ExceptionHandler({
    StorefrontException.class,
    IllegalArgumentException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<Void> missing() {
    return ResponseEntity.status(404).header("Cache-Control", "no-store").build();
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Void> unavailable() {
    return ResponseEntity.status(503).header("Retry-After", "5").build();
  }
}
