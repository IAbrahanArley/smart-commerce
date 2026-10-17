package com.smartcommerce.identity.services;

import com.smartcommerce.identity.repositories.JdbcRateLimitRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.springframework.transaction.support.TransactionTemplate;

/** Fixed windows, bounded cardinality, atomic multi-bucket admission across instances. */
public final class IdentityRateLimiter {
  public record Quota(String key, int limit, Duration window) {}

  private final JdbcRateLimitRepository repository;
  private final TransactionTemplate transactions;

  public IdentityRateLimiter(JdbcRateLimitRepository repository, TransactionTemplate transactions) {
    this.repository = repository;
    this.transactions = transactions;
  }

  public static String digest(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("Required digest unavailable");
    }
  }

  public static Quota quota(String category, String subject, int limit, Duration window) {
    return new Quota(category + ":" + digest(subject), limit, window);
  }

  public boolean acquire(List<Quota> quotas) {
    return Boolean.TRUE.equals(
        transactions.execute(
            status -> {
              // One short DB transaction serializes admission and cardinality checking; no KDF
              // under lock.
              repository.lockAdmission();
              Instant now = Instant.now();
              repository.deleteExpired(now);
              int newKeys = 0;
              for (Quota quota : quotas) {
                var attempts = repository.attempts(quota.key());
                if (attempts.isPresent() && attempts.orElseThrow() >= quota.limit()) return false;
                if (attempts.isEmpty()) newKeys++;
              }
              if (repository.countBuckets() > 10000 - newKeys) return false;
              for (Quota quota : quotas) {
                repository.increment(quota.key(), now, now.plus(quota.window()));
              }
              return true;
            }));
  }

  /** Refund this admitted successful login only; retain failures from other attempts. */
  public void refund(List<Quota> quotas) {
    transactions.executeWithoutResult(
        status -> {
          repository.lockAdmission();
          for (Quota quota : quotas) repository.refund(quota.key());
        });
  }
}
