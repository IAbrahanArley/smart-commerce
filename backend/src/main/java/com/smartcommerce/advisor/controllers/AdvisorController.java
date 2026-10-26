package com.smartcommerce.advisor.controllers;

import com.smartcommerce.advisor.domain.AdvisorException;
import com.smartcommerce.advisor.dto.request.AdvisorQuestionRequest;
import com.smartcommerce.advisor.dto.response.AdvisorAnswerView;
import com.smartcommerce.advisor.dto.response.AdvisorStatusView;
import com.smartcommerce.advisor.services.AdvisorAssistantService;
import com.smartcommerce.finance.domain.FinanceException;
import com.smartcommerce.orders.domain.OrderException;
import com.smartcommerce.payments.domain.PaymentException;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * The store OWNER's assistant (SC-026): status and one question at a time, never cached. Absent or
 * foreign store: one 404. Switched off, without a key, over the platform cap or with the provider
 * failing: "unavailable"; the store's own quota has its own message.
 */
@SecurityRequirement(name = "session")
@Tag(name = "Advisor")
@RestController
@RequestMapping("/api/tenants/{tenantId}/advisor")
public final class AdvisorController {
  private final AdvisorAssistantService assistant;

  public AdvisorController(AdvisorAssistantService assistant) {
    this.assistant = assistant;
  }

  @GetMapping
  public ResponseEntity<AdvisorStatusView> status(
      @PathVariable UUID tenantId, Authentication actor) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(assistant.status(UUID.fromString(actor.getName()), tenantId));
  }

  @PostMapping("/questions")
  public ResponseEntity<AdvisorAnswerView> ask(
      @PathVariable UUID tenantId,
      @RequestBody AdvisorQuestionRequest request,
      Authentication actor) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            assistant.ask(
                UUID.fromString(actor.getName()),
                tenantId,
                request == null ? null : request.question(),
                request == null ? null : request.history()));
  }

  @ExceptionHandler(AdvisorException.class)
  ResponseEntity<Map<String, String>> advisor(AdvisorException failure) {
    return switch (failure.reason()) {
      case INVALID_QUESTION ->
          error(400, "INVALID_QUESTION", "Escreva uma pergunta de até 500 caracteres.");
      case QUOTA_EXCEEDED ->
          error(429, "QUOTA_EXCEEDED", "A loja já usou todas as perguntas deste mês.");
      case DISABLED, GLOBAL_CAP_REACHED, PROVIDER_UNAVAILABLE ->
          error(503, "UNAVAILABLE", "O assistente está indisponível no momento.");
      case MISSING, INVALID_TOOL_CALL -> missing();
    };
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<Map<String, String>> unreadable() {
    return error(400, "INVALID_QUESTION", "Escreva uma pergunta de até 500 caracteres.");
  }

  @ExceptionHandler({
    FinanceException.class,
    OrderException.class,
    PaymentException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<Map<String, String>> missing() {
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

  private static ResponseEntity<Map<String, String>> error(int status, String code, String text) {
    return ResponseEntity.status(status)
        .cacheControl(CacheControl.noStore())
        .body(Map.of("code", code, "message", text));
  }
}
