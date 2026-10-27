package com.smartcommerce.payments.services;

import com.smartcommerce.payments.domain.OAuthTokens;
import com.smartcommerce.payments.domain.PaymentAccountRecord;
import com.smartcommerce.payments.domain.PaymentException;
import com.smartcommerce.payments.domain.PendingAuthorization;
import com.smartcommerce.payments.domain.Pkce;
import com.smartcommerce.payments.domain.SellerCredentials;
import com.smartcommerce.payments.dto.response.AuthorizationStartView;
import com.smartcommerce.payments.dto.response.InstallmentsView;
import com.smartcommerce.payments.dto.response.PaymentConnectionView;
import com.smartcommerce.payments.provider.PaymentProviders;
import com.smartcommerce.payments.repositories.PaymentAccountRepository;
import com.smartcommerce.payments.security.TokenCipher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Mercado Pago account connection for the store OWNER (SC-012, ADR-0013 P3).
 *
 * <ul>
 *   <li>The tenant comes from the route but is honoured only through the actor's OWNER membership;
 *       anything else is one 404.
 *   <li>The OAuth state is random, stored hashed, single use, valid for {@link #STATE_TTL} and
 *       bound to the user and the store that started it. The PKCE verifier is kept encrypted.
 *   <li>Tokens are encrypted at rest and bound to the tenant; refreshing locks the account row so
 *       the rotated refresh token is never lost to a concurrent refresh.
 * </ul>
 */
@Service
public class PaymentConnectionService {
  static final Duration STATE_TTL = Duration.ofMinutes(10);

  /** Access tokens last 180 days; refresh once fewer than 30 remain. */
  static final Duration REFRESH_MARGIN = Duration.ofDays(30);

  static final String PROVIDER = "MERCADO_PAGO";
  private static final String VERIFIER = "mp-pkce-verifier";
  private static final String ACCESS = "mp-access-token";
  private static final String REFRESH = "mp-refresh-token";
  private final PaymentAccountRepository accounts;
  private final PaymentProviders providers;
  private final Clock clock;
  private final TransactionTemplate writes;
  private final TransactionTemplate reads;

  public PaymentConnectionService(
      PaymentAccountRepository accounts,
      PaymentProviders providers,
      Clock clock,
      PlatformTransactionManager transactions) {
    this.accounts = accounts;
    this.providers = providers;
    this.clock = clock;
    this.writes = new TransactionTemplate(transactions);
    this.writes.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    this.reads = new TransactionTemplate(transactions);
    this.reads.setReadOnly(true);
  }

  public PaymentConnectionView status(UUID actor, UUID tenant) {
    return reads.execute(
        tx -> {
          requireOwner(actor, tenant);
          return view(accounts.find(tenant).orElse(null), now());
        });
  }

  /** Starts an attempt and returns the provider URL; a new attempt replaces the previous one. */
  public AuthorizationStartView startAuthorization(UUID actor, UUID tenant) {
    var client = providers.requireMercadoPago();
    TokenCipher cipher = providers.requireCipher();
    String state = Pkce.randomValue();
    String verifier = Pkce.randomValue();
    writes.executeWithoutResult(
        tx -> {
          requireOwner(actor, tenant);
          Instant now = now();
          accounts.deleteExpiredAuthorizations(now);
          accounts.savePendingAuthorization(
              Pkce.stateHash(state),
              tenant,
              actor,
              cipher.encrypt(verifier, tenant, VERIFIER),
              now,
              now.plus(STATE_TTL));
        });
    return new AuthorizationStartView(client.authorizationUrl(state, Pkce.challenge(verifier)));
  }

  /**
   * Finishes the attempt the provider redirected back with. The state is consumed first, in its own
   * transaction, so it stays used even when the exchange fails; the exchange runs outside any lock
   * and the tokens are stored afterwards. Returns the store, also on failure when known.
   */
  public Completion complete(UUID actor, String state, String code, boolean denied) {
    if (!Pkce.wellFormed(state))
      return Completion.failed(null, PaymentException.Reason.INVALID_STATE);
    Claimed claimed;
    try {
      claimed = writes.execute(tx -> claim(actor, state));
    } catch (PaymentException failure) {
      return Completion.failed(null, failure.reason());
    }
    if (claimed.verifier() == null)
      return Completion.failed(null, PaymentException.Reason.INVALID_STATE);
    if (denied) return Completion.failed(claimed.tenant(), PaymentException.Reason.DENIED);
    if (code == null || code.isBlank() || code.length() > 512 || !code.matches("[\\x21-\\x7e]+"))
      return Completion.failed(claimed.tenant(), PaymentException.Reason.PROVIDER_ERROR);
    OAuthTokens tokens;
    try {
      tokens = providers.requireMercadoPago().exchange(code, claimed.verifier(), now());
    } catch (PaymentException failure) {
      return Completion.failed(claimed.tenant(), failure.reason());
    }
    TokenCipher cipher = providers.requireCipher();
    UUID tenant = claimed.tenant();
    writes.executeWithoutResult(
        tx -> {
          // Membership may have changed while the seller was at the provider.
          requireOwner(actor, tenant);
          accounts.saveConnected(
              tenant,
              tokens.providerUserId(),
              cipher.encrypt(tokens.accessToken(), tenant, ACCESS),
              cipher.encrypt(tokens.refreshToken(), tenant, REFRESH),
              tokens.accessExpiresAt(),
              TokenCipher.KEY_VERSION,
              tokens.liveMode(),
              actor,
              now());
          accounts.requeuePayments(tenant, now());
        });
    return new Completion(tenant, null);
  }

  /** The store's installment limit (SC-042); only its OWNER reads it. */
  public InstallmentsView installments(UUID actor, UUID tenant) {
    return reads.execute(
        tx -> {
          requireOwner(actor, tenant);
          return new InstallmentsView(accounts.maxInstallments(tenant));
        });
  }

  /** Sets ({@code null}: clears) the store's installment limit, used by new checkouts. */
  public InstallmentsView installments(UUID actor, UUID tenant, Integer installments) {
    return writes.execute(
        tx -> {
          requireOwner(actor, tenant);
          if (!accounts.maxInstallments(tenant, installments))
            throw new PaymentException(PaymentException.Reason.MISSING);
          return new InstallmentsView(accounts.maxInstallments(tenant));
        });
  }

  /** The installment limit for a checkout of {@code tenant}; tenant already trusted. */
  public Integer maxInstallments(UUID tenant) {
    return accounts.maxInstallments(tenant);
  }

  public PaymentConnectionView disconnect(UUID actor, UUID tenant) {
    return writes.execute(
        tx -> {
          requireOwner(actor, tenant);
          Instant now = now();
          accounts.lock(tenant);
          accounts.close(tenant, "DISCONNECTED", now);
          return view(accounts.find(tenant).orElse(null), now);
        });
  }

  /**
   * The seller's access token for provider calls on behalf of {@code tenant} (SC-013 onwards),
   * refreshed first when it is close to expiry. Never exposed through HTTP.
   */
  public String accessToken(UUID tenant) {
    return sellerCredentials(tenant).accessToken();
  }

  /** {@link #accessToken} with the seller account it belongs to (SC-013 records it). */
  public SellerCredentials sellerCredentials(UUID tenant) {
    TokenCipher cipher = providers.requireCipher();
    try {
      return writes.execute(tx -> lockedCredentials(tenant, cipher));
    } catch (PaymentException failure) {
      // The grant is gone at the provider: erase the tokens in a transaction of its own, since
      // the refresh transaction rolled back. The row lock serialized refreshes, so this was not a
      // stale refresh token racing a concurrent rotation.
      if (failure.reason() == PaymentException.Reason.REVOKED)
        writes.executeWithoutResult(
            tx -> {
              accounts.lock(tenant);
              accounts.close(tenant, "REVOKED", now());
            });
      throw failure;
    }
  }

  private SellerCredentials lockedCredentials(UUID tenant, TokenCipher cipher) {
    PaymentAccountRecord account =
        accounts
            .lock(tenant)
            .filter(found -> "CONNECTED".equals(found.status()))
            .orElseThrow(() -> new PaymentException(PaymentException.Reason.NOT_CONNECTED));
    Instant now = now();
    String token =
        account.accessExpiresAt().isAfter(now.plus(REFRESH_MARGIN))
            ? cipher.decrypt(account.accessToken(), tenant, ACCESS)
            : refreshLocked(tenant, account, cipher, now).accessToken();
    return new SellerCredentials(account.providerUserId(), token);
  }

  /** Refreshes up to {@code limit} accounts near expiry; returns how many were processed. */
  public int refreshDue(int limit) {
    List<UUID> due = accounts.dueForRefresh(now().plus(REFRESH_MARGIN), limit);
    for (UUID tenant : due) {
      try {
        accessToken(tenant);
      } catch (PaymentException failure) {
        // REVOKED already closed the account; anything else is retried on the next run.
      }
    }
    return due.size();
  }

  private OAuthTokens refreshLocked(
      UUID tenant, PaymentAccountRecord account, TokenCipher cipher, Instant now) {
    OAuthTokens tokens =
        providers
            .requireMercadoPago()
            .refresh(cipher.decrypt(account.refreshToken(), tenant, REFRESH), now);
    if (!tokens.providerUserId().equals(account.providerUserId()))
      throw new PaymentException(PaymentException.Reason.PROVIDER_ERROR);
    accounts.saveRefreshed(
        tenant,
        cipher.encrypt(tokens.accessToken(), tenant, ACCESS),
        cipher.encrypt(tokens.refreshToken(), tenant, REFRESH),
        tokens.accessExpiresAt(),
        TokenCipher.KEY_VERSION,
        now);
    return tokens;
  }

  private Claimed claim(UUID actor, String state) {
    byte[] hash = Pkce.stateHash(state);
    PendingAuthorization pending =
        accounts
            .lockPendingAuthorization(hash)
            .orElseThrow(() -> new PaymentException(PaymentException.Reason.INVALID_STATE));
    Instant now = now();
    if (pending.usedAt() != null
        || !pending.expiresAt().isAfter(now)
        || !pending.user().equals(actor))
      throw new PaymentException(PaymentException.Reason.INVALID_STATE);
    accounts.markUsed(hash, now);
    // Checked after consuming, and reported without rolling back, so the state stays used.
    if (!accounts.ownsStore(actor, pending.tenant())) return new Claimed(pending.tenant(), null);
    return new Claimed(
        pending.tenant(),
        providers.requireCipher().decrypt(pending.codeVerifier(), pending.tenant(), VERIFIER));
  }

  private void requireOwner(UUID actor, UUID tenant) {
    if (!accounts.ownsStore(actor, tenant))
      throw new PaymentException(PaymentException.Reason.MISSING);
  }

  private PaymentConnectionView view(PaymentAccountRecord account, Instant now) {
    if (!providers.available())
      return new PaymentConnectionView(PROVIDER, "UNAVAILABLE", null, null, null, null, null);
    if (account == null || "DISCONNECTED".equals(account.status()))
      return new PaymentConnectionView(PROVIDER, "NOT_CONNECTED", null, null, null, null, null);
    String status =
        "CONNECTED".equals(account.status()) && !account.accessExpiresAt().isAfter(now)
            ? "EXPIRED"
            : account.status();
    return new PaymentConnectionView(
        PROVIDER,
        status,
        account.providerUserId(),
        account.connectedAt(),
        account.accessExpiresAt(),
        account.refreshedAt(),
        account.closedAt());
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }

  /** Outcome of a provider redirect: the store (when known) and the failure, if any. */
  public record Completion(UUID tenant, PaymentException.Reason failure) {
    static Completion failed(UUID tenant, PaymentException.Reason reason) {
      return new Completion(tenant, reason);
    }

    public boolean succeeded() {
      return failure == null;
    }
  }

  private record Claimed(UUID tenant, String verifier) {
    @Override
    public String toString() {
      return "Claimed[" + tenant + "]";
    }
  }
}
