package com.smartcommerce.finance.controllers;

import com.smartcommerce.finance.domain.FinanceAccount;
import com.smartcommerce.finance.domain.MovementInput;
import com.smartcommerce.finance.dto.request.AccountRequest;
import com.smartcommerce.finance.dto.request.AdjustmentRequest;
import com.smartcommerce.finance.dto.request.TransferRequest;
import com.smartcommerce.finance.services.AccountService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Accounts of the store, their balances and statements, adjustments and transfers (SC-050, ADR-0019
 * C2). OWNER only, checked in the service; the actor always comes from the session.
 */
@SecurityRequirement(name = "session")
@Tag(name = "Finance")
@RestController
@RequestMapping("/api/tenants/{tenantId}/finance")
public final class AccountController {
  private final AccountService accounts;

  public AccountController(AccountService accounts) {
    this.accounts = accounts;
  }

  @GetMapping("/accounts")
  public ResponseEntity<List<FinanceAccount>> list(
      @PathVariable UUID tenantId, Authentication actor) {
    return fresh(accounts.list(actor(actor), tenantId));
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping("/accounts")
  public ResponseEntity<FinanceAccount> create(
      @PathVariable UUID tenantId, @RequestBody AccountRequest request, Authentication actor) {
    return fresh(
        accounts.create(
            actor(actor),
            tenantId,
            MovementInput.account(request.requestId(), request.type(), request.name())));
  }

  @GetMapping("/accounts/{accountId}/statement")
  public ResponseEntity<AccountService.Statement> statement(
      @PathVariable UUID tenantId,
      @PathVariable UUID accountId,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) String before,
      Authentication actor) {
    return fresh(accounts.statement(actor(actor), tenantId, accountId, from, to, before));
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping("/accounts/{accountId}/adjustments")
  public ResponseEntity<FinanceAccount> adjust(
      @PathVariable UUID tenantId,
      @PathVariable UUID accountId,
      @RequestBody AdjustmentRequest request,
      Authentication actor) {
    return fresh(
        accounts.adjust(
            actor(actor),
            tenantId,
            accountId,
            MovementInput.adjustment(
                request.requestId(),
                request.amount(),
                request.occurredOn(),
                request.reason(),
                accounts.today())));
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping("/transfers")
  public ResponseEntity<List<FinanceAccount>> transfer(
      @PathVariable UUID tenantId, @RequestBody TransferRequest request, Authentication actor) {
    return fresh(
        accounts.transfer(
            actor(actor),
            tenantId,
            MovementInput.transfer(
                request.requestId(),
                request.fromAccountId(),
                request.toAccountId(),
                request.amount(),
                request.occurredOn(),
                request.note(),
                accounts.today())));
  }

  private static UUID actor(Authentication actor) {
    return UUID.fromString(actor.getName());
  }

  private static <T> ResponseEntity<T> fresh(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }
}
