package com.smartcommerce.identity.services;

import com.smartcommerce.identity.domain.EmailAddress;
import com.smartcommerce.identity.domain.IdentityStatus;
import com.smartcommerce.identity.domain.IdentityUser;
import com.smartcommerce.identity.domain.PasswordHash;
import com.smartcommerce.identity.domain.PasswordPolicy;
import com.smartcommerce.identity.exceptions.IdentityException;
import com.smartcommerce.identity.mail.VerificationMail;
import com.smartcommerce.identity.repositories.JdbcIdentityRepository;
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
      JdbcRateLimitRepository rateLimits,
      IdentityPasswordEncoder encoder,
      TransactionTemplate transactions,
      IdentityRateLimiter limits,
      VerificationMail mail,
      String origin) {
    this.proofs = proofs;
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
