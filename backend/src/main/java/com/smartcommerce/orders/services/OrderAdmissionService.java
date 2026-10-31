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

  /**
   * "Acompanhar pedido" (SC-046, SC-048 review): attempts per hour per client address, per store
   * order number with the e-mail tried, and per store e-mail. Counting the number together with the
   * e-mail keeps a stranger who knows only the number from locking the buyer out; the e-mail limit
   * stops walking the sequential numbers of one buyer from many addresses.
   */
  static final int MAX_LOOKUPS_PER_IP = 20;

  static final int MAX_LOOKUPS_PER_ORDER = 10;
  static final int MAX_LOOKUPS_PER_EMAIL = 20;
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
   * Counts one order lookup for the client address, the store order number with this e-mail and the
   * store e-mail, whether the order exists or not, so the limits never tell an existing order from
   * a missing one. Keys are HMACs: no address, number or e-mail is stored in clear.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void admitLookup(String clientIp, String slug, long number, String emailKey) {
    Instant now = ReservationPolicy.now(clock);
    limits.deleteExpired(now);
    Instant until = now.plus(WINDOW);
    int byIp = limits.increment(hash("lookup-ip:" + ClientIpBucket.of(clientIp)), now, until);
    int byOrder =
        limits.increment(hash("lookup-order:" + slug + ":" + number + ":" + emailKey), now, until);
    int byEmail = limits.increment(hash("lookup-email:" + slug + ":" + emailKey), now, until);
    if (byIp > MAX_LOOKUPS_PER_IP
        || byOrder > MAX_LOOKUPS_PER_ORDER
        || byEmail > MAX_LOOKUPS_PER_EMAIL)
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
