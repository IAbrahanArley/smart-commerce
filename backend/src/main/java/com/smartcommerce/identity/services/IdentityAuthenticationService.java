package com.smartcommerce.identity.services;

import com.smartcommerce.identity.domain.EmailAddress;
import com.smartcommerce.identity.domain.IdentityStatus;
import com.smartcommerce.identity.domain.IdentityUser;
import com.smartcommerce.identity.domain.PasswordHash;
import com.smartcommerce.identity.domain.PasswordPolicy;
import com.smartcommerce.identity.exceptions.IdentityException;
import com.smartcommerce.identity.mail.VerificationMail;
import com.smartcommerce.identity.repositories.JdbcIdentityRepository;
import com.smartcommerce.identity.repositories.JdbcPasswordResetRepository;
import com.smartcommerce.identity.repositories.JdbcRateLimitRepository;
import com.smartcommerce.identity.repositories.JdbcVerificationRepository;
import com.smartcommerce.identity.security.IdentityPasswordEncoder;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import org.springframework.transaction.support.TransactionTemplate;

/** Application boundary: email ownership precedes credential creation. */
public final class IdentityAuthenticationService {
  private final JdbcVerificationRepository proofs;
  private final JdbcPasswordResetRepository resets;
  private final JdbcRateLimitRepository rateLimits;
  private final TransactionTemplate transactions;
  private final JdbcIdentityRepository identities;
  private final IdentityRateLimiter limits;
  private final VerificationMail mail;
  private final String origin;
  private final IdentityPasswordEncoder encoder;
  private final Semaphore hashPermits = new Semaphore(2);
  private final SecureRandom random = new SecureRandom();
  private final String dummyHash;

  public IdentityAuthenticationService(
      JdbcIdentityRepository identities,
      JdbcVerificationRepository proofs,
      JdbcPasswordResetRepository resets,
      JdbcRateLimitRepository rateLimits,
      IdentityPasswordEncoder encoder,
      TransactionTemplate transactions,
      IdentityRateLimiter limits,
      VerificationMail mail,
      String origin) {
    this.proofs = proofs;
    this.resets = resets;
    this.rateLimits = rateLimits;
    this.encoder = encoder;
    this.transactions = transactions;
    this.identities = identities;
    this.limits = limits;
    this.mail = mail;
    this.origin = origin;
    // One independent random dummy hash per process; never a shared usable account credential.
    dummyHash = encoder.encode(UUID.randomUUID() + " " + UUID.randomUUID());
  }

  public void requestRegistration(String rawEmail, String remoteAddress) {
    EmailAddress email = EmailAddress.parse(rawEmail);
    if (!limits.acquire(
        List.of(
            IdentityRateLimiter.quota("mail-id", email.comparisonKey(), 3, Duration.ofHours(1)),
            IdentityRateLimiter.quota("mail-ip", remoteAddress, 10, Duration.ofHours(1)),
            IdentityRateLimiter.quota("registration-global", "all", 1000, Duration.ofMinutes(15)))))
      return;
    transactions.executeWithoutResult(
        status -> {
          Instant now = Instant.now();
          UUID candidate = UUID.randomUUID();
          identities.createPendingIfAbsent(candidate, email, now);
          var locked = identities.lockByEmail(email);
          if (locked.isEmpty()) return;
          var user = locked.orElseThrow();
          if (user.status() != IdentityStatus.PENDING_EMAIL) return;
          UUID id = user.id();
          if (!user.createdAt().plus(Duration.ofDays(7)).isAfter(now)) {
            proofs.deleteForUser(id);
            identities.deletePending(id);
            id = candidate;
            identities.createPending(id, email, now);
          }
          var issued = proofs.latestIssue(id);
          if (issued.isPresent() && issued.orElseThrow().plusSeconds(60).isAfter(now)) return;
          byte[] bytes = new byte[32];
          random.nextBytes(bytes);
          String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
          // Mail failure rolls back replacement, preserving the previous proof.
          try {
            mail.send(email.original(), origin + "/confirmar-email#token=" + token);
          } catch (IdentityException deliveryFailure) {
            if (deliveryFailure.reason() != IdentityException.Reason.UNAVAILABLE)
              throw deliveryFailure;
            // Delivery failures must not distinguish a new/pending address from an active one.
            // Preserve all previous pending/proof state; admission quotas remain committed.
            status.setRollbackOnly();
            return;
          }
          proofs.deleteForUser(id);
          proofs.insert(
              IdentityRateLimiter.digest(token), id, now, now.plus(Duration.ofMinutes(30)));
        });
  }

  public void activate(String token, String password, String remoteAddress) {
    if (!limits.acquire(
        List.of(
            IdentityRateLimiter.quota("activation-ip", remoteAddress, 30, Duration.ofMinutes(15)),
            IdentityRateLimiter.quota("activation-global", "all", 1000, Duration.ofMinutes(15)))))
      throw throttled();
    if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalidProof();
    String digest = IdentityRateLimiter.digest(token);
    Instant now = Instant.now();
    UUID userId =
        proofs
            .findEligibleUser(digest, now, now.minus(Duration.ofDays(7)))
            .orElseThrow(IdentityAuthenticationService::invalidProof);
    String hash = hash(password);
    Boolean activated =
        transactions.execute(
            status -> {
              Instant current = Instant.now();
              UUID id = userId;
              // Same lock order as reissue: user first, then proof.
              if (!identities.lockFreshPending(id, current.minus(Duration.ofDays(7)))) return false;
              if (!proofs.consume(digest, id, current)) return false;
              identities.markActive(id, current);
              if (!identities.insertCredential(id, new PasswordHash(hash), current))
                throw new IllegalStateException("Activation persistence failed");
              return true;
            });
    if (!Boolean.TRUE.equals(activated)) throw invalidProof();
  }

  /**
   * Password recovery request (MVP block 1a). Always silent: the answer never tells whether the
   * address has an account. Only an active, verified account with a credential gets a link; a new
   * request replaces the previous link, at most one per minute, and a delivery failure keeps the
   * previous one.
   */
  public void requestPasswordReset(String rawEmail, String remoteAddress) {
    EmailAddress email;
    try {
      email = EmailAddress.parse(rawEmail);
    } catch (IllegalArgumentException invalid) {
      return; // Same silent answer as an unknown address.
    }
    if (!limits.acquire(
        List.of(
            IdentityRateLimiter.quota("reset-id", email.comparisonKey(), 3, Duration.ofHours(1)),
            IdentityRateLimiter.quota("reset-ip", remoteAddress, 10, Duration.ofHours(1)),
            IdentityRateLimiter.quota("reset-global", "all", 1000, Duration.ofMinutes(15)))))
      return;
    transactions.executeWithoutResult(
        status -> {
          Instant now = Instant.now();
          var locked = identities.lockByEmail(email);
          if (locked.isEmpty()) return;
          var user = locked.orElseThrow();
          if (user.status() != IdentityStatus.ACTIVE
              || user.verifiedAt() == null
              || identities.findCredential(user.id()).isEmpty()) return;
          var issued = resets.latestIssue(user.id());
          if (issued.isPresent() && issued.orElseThrow().plusSeconds(60).isAfter(now)) return;
          byte[] bytes = new byte[32];
          random.nextBytes(bytes);
          String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
          try {
            mail.sendPasswordReset(email.original(), origin + "/redefinir-senha#token=" + token);
          } catch (IdentityException deliveryFailure) {
            if (deliveryFailure.reason() != IdentityException.Reason.UNAVAILABLE)
              throw deliveryFailure;
            // Silent like an unknown address; the previous link, if any, stays valid.
            status.setRollbackOnly();
            return;
          }
          resets.deleteForUser(user.id());
          resets.insert(
              IdentityRateLimiter.digest(token), user.id(), now, now.plus(Duration.ofMinutes(30)));
        });
  }

  /**
   * Sets a new password from a recovery link (MVP block 1a). The proof is consumed once, under the
   * user lock; every other link of the user is dropped and the session version changes, so all
   * existing sessions end.
   */
  public void resetPassword(String token, String password, String remoteAddress) {
    if (!limits.acquire(
        List.of(
            IdentityRateLimiter.quota(
                "reset-confirm-ip", remoteAddress, 30, Duration.ofMinutes(15)),
            IdentityRateLimiter.quota(
                "reset-confirm-global", "all", 1000, Duration.ofMinutes(15))))) throw throttled();
    if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalidResetProof();
    String digest = IdentityRateLimiter.digest(token);
    UUID userId =
        resets
            .findEligibleUser(digest, Instant.now())
            .orElseThrow(IdentityAuthenticationService::invalidResetProof);
    String hash = hash(password);
    Boolean replaced =
        transactions.execute(
            status -> {
              Instant current = Instant.now();
              // Same lock order as the request: user first, then proof.
              var user = identities.findUser(userId);
              if (user.isEmpty()) return false;
              if (identities.lockByEmail(user.orElseThrow().email()).isEmpty()) return false;
              if (!resets.consume(digest, userId, current)) return false;
              if (!identities.replacePassword(userId, new PasswordHash(hash), current)) {
                status.setRollbackOnly();
                return false;
              }
              resets.deleteForUser(userId);
              return true;
            });
    if (!Boolean.TRUE.equals(replaced)) throw invalidResetProof();
  }

  public IdentityUser authenticate(String rawEmail, String password, String remoteAddress) {
    EmailAddress email = null;
    try {
      email = EmailAddress.parse(rawEmail);
    } catch (IllegalArgumentException ignored) {
      /* Generic negative path. */
    }
    String subject = email == null ? "invalid-identifier" : email.comparisonKey();
    List<IdentityRateLimiter.Quota> quotas =
        List.of(
            IdentityRateLimiter.quota("login-id", subject, 5, Duration.ofMinutes(15)),
            IdentityRateLimiter.quota("login-ip", remoteAddress, 50, Duration.ofMinutes(15)),
            IdentityRateLimiter.quota("login-global", "all", 1000, Duration.ofMinutes(15)));
    if (!limits.acquire(quotas)) throw throttled();
    var user =
        email == null ? java.util.Optional.<IdentityUser>empty() : identities.findByEmail(email);
    var credential =
        user.filter(value -> value.status() == IdentityStatus.ACTIVE && value.verifiedAt() != null)
            .flatMap(value -> identities.findCredential(value.id()));
    boolean matched =
        matches(password, credential.map(value -> value.hash().encoded()).orElse(dummyHash));
    if (!matched || credential.isEmpty())
      throw new IdentityException(
          IdentityException.Reason.INVALID_CREDENTIALS, "E-mail ou senha inválidos.");
    IdentityUser authenticated =
        identities
            .findUser(user.orElseThrow().id())
            .orElseThrow(
                () ->
                    new IdentityException(
                        IdentityException.Reason.INVALID_CREDENTIALS,
                        "E-mail ou senha inválidos."));
    if (authenticated.status() != IdentityStatus.ACTIVE
        || authenticated.authVersion() != user.orElseThrow().authVersion())
      throw new IdentityException(
          IdentityException.Reason.INVALID_CREDENTIALS, "E-mail ou senha inválidos.");
    limits.refund(quotas);
    return authenticated;
  }

  public java.util.Optional<IdentityUser> findUser(UUID id) {
    return identities.findUser(id);
  }

  private String hash(String password) {
    if (!hashPermits.tryAcquire()) throw unavailable();
    try {
      return encoder.encode(password);
    } finally {
      hashPermits.release();
    }
  }

  private boolean matches(String password, String encoded) {
    if (!hashPermits.tryAcquire()) throw unavailable();
    try {
      // Invalid candidate size must not bypass the comparable negative KDF path.
      String candidate;
      try {
        candidate = new PasswordPolicy().normalizeForVerification(password);
      } catch (IllegalArgumentException exception) {
        candidate = "invalid synthetic candidate 000";
        encoded = dummyHash;
      }
      return encoder.matches(candidate, encoded);
    } finally {
      hashPermits.release();
    }
  }

  public void cleanup() {
    transactions.executeWithoutResult(
        status -> {
          Instant now = Instant.now();
          proofs.deleteExpired(now.minus(Duration.ofHours(23)));
          resets.deleteExpired(now.minus(Duration.ofHours(23)));
          identities.deleteExpiredPending(now.minus(Duration.ofDays(7)));
          rateLimits.deleteExpired(now);
        });
    mail.cleanup();
  }

  private static IdentityException invalidProof() {
    return new IdentityException(
        IdentityException.Reason.INVALID_PROOF,
        "Link inválido ou expirado. Solicite uma nova confirmação.");
  }

  private static IdentityException invalidResetProof() {
    return new IdentityException(
        IdentityException.Reason.INVALID_PROOF,
        "Link inválido ou expirado. Peça um novo link de redefinição de senha.");
  }

  private static IdentityException throttled() {
    return new IdentityException(
        IdentityException.Reason.THROTTLED,
        "Muitas tentativas. Aguarde antes de tentar novamente.");
  }

  private static IdentityException unavailable() {
    return new IdentityException(
        IdentityException.Reason.UNAVAILABLE, "Serviço temporariamente indisponível.");
  }
}
