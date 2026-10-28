package com.smartcommerce.orders.services;

import com.smartcommerce.inventory.domain.ReservationPolicy;
import com.smartcommerce.orders.domain.OrderException;
import com.smartcommerce.orders.repositories.OrderRateLimitRepository;
import com.smartcommerce.orders.security.ClientIpBucket;
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

  /** "Acompanhar pedido" (SC-046): attempts per client address and per order number, per hour. */
  static final int MAX_LOOKUPS_PER_IP = 20;

  static final int MAX_LOOKUPS_PER_ORDER = 10;
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
    if (limits.increment(bucket(ClientIpBucket.of(clientIp)), now, now.plus(WINDOW)) > MAX_ATTEMPTS)
      throw new OrderException(OrderException.Reason.RATE_LIMITED);
  }

  /**
   * Counts one order lookup for the client address and for the store order number, whether the
   * order exists or not, so the limit itself never tells an existing number from a missing one.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void admitLookup(String clientIp, String slug, long number) {
    Instant now = ReservationPolicy.now(clock);
    limits.deleteExpired(now);
    int byIp =
        limits.increment(hash("lookup-ip:" + ClientIpBucket.of(clientIp)), now, now.plus(WINDOW));
    int byOrder =
        limits.increment(hash("lookup-order:" + slug + ":" + number), now, now.plus(WINDOW));
    if (byIp > MAX_LOOKUPS_PER_IP || byOrder > MAX_LOOKUPS_PER_ORDER)
      throw new OrderException(OrderException.Reason.RATE_LIMITED);
  }

  /** {@code group} is the normalized address group (IPv6 /64), never stored in clear. */
  private String bucket(String group) {
    return hash("checkout-ip:" + group);
  }

  private String hash(String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
