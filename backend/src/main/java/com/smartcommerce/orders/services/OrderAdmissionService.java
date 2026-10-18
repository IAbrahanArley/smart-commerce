package com.smartcommerce.orders.services;

import com.smartcommerce.inventory.domain.ReservationPolicy;
import com.smartcommerce.orders.domain.OrderException;
import com.smartcommerce.orders.repositories.OrderRateLimitRepository;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-IP checkout admission (ADR-0012 D4): at most {@value #MAX_ATTEMPTS} attempts per hour. Runs
 * in its own transaction, so rejected checkouts (stock, price) still count. Keys are HMACs with a
 * key generated at startup and kept only in memory: no IP is recoverable from the table, and a
 * restart starts new windows.
 */
@Service
public class OrderAdmissionService {
  static final int MAX_ATTEMPTS = 20;
  private static final Duration WINDOW = Duration.ofHours(1);
  private final OrderRateLimitRepository limits;
  private final Clock clock;
  private final byte[] key = new byte[32];

  public OrderAdmissionService(OrderRateLimitRepository limits, Clock clock) {
    this.limits = limits;
    this.clock = clock;
    new SecureRandom().nextBytes(key);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void admit(String clientIp) {
    Instant now = ReservationPolicy.now(clock);
    limits.deleteExpired(now);
    if (limits.increment(bucket(clientIp == null ? "" : clientIp), now, now.plus(WINDOW))
        > MAX_ATTEMPTS) throw new OrderException(OrderException.Reason.RATE_LIMITED);
  }

  private String bucket(String clientIp) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return HexFormat.of()
          .formatHex(mac.doFinal(("checkout-ip:" + clientIp).getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
