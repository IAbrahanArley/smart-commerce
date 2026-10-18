package com.smartcommerce.orders.services;

import com.smartcommerce.inventory.domain.ReservationPolicy;
import com.smartcommerce.inventory.services.InventoryReservationService;
import com.smartcommerce.orders.repositories.OrderMaintenanceRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order lifecycle batches (SC-010). Correctness never depends on how often they run: an expired
 * reservation already stops counting toward availability, and the buyer and admin views already
 * read a pending order past its deadline as EXPIRED. The batches persist that state, release the
 * reservations and apply the D2 retention. Each call is one transaction holding one connection.
 */
@Service
public class OrderLifecycleService {
  /** D2: unpaid orders keep the buyer's personal data for 90 days after creation. */
  static final Duration RETENTION = Duration.ofDays(90);

  private final OrderMaintenanceRepository orders;
  private final InventoryReservationService reservations;
  private final Clock clock;

  public OrderLifecycleService(
      OrderMaintenanceRepository orders, InventoryReservationService reservations, Clock clock) {
    this.orders = orders;
    this.reservations = reservations;
    this.clock = clock;
  }

  /** Expires up to {@code limit} due orders and releases their reservations; returns the count. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public int expireDue(int limit) {
    checkLimit(limit);
    Instant now = ReservationPolicy.now(clock);
    int expired = 0;
    for (var due : orders.lockDue(now, limit)) {
      if (!orders.expire(due.tenantId(), due.id(), now)) continue;
      reservations.expireOrder(due.tenantId(), due.id());
      expired++;
    }
    return expired;
  }

  /** Anonymizes up to {@code limit} closed orders past the D2 retention; returns the count. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public int anonymizeDue(int limit) {
    checkLimit(limit);
    Instant now = ReservationPolicy.now(clock);
    return orders.anonymize(now.minus(RETENTION), now, limit);
  }

  private static void checkLimit(int limit) {
    if (limit < 1 || limit > 1000) throw new IllegalArgumentException("Invalid batch size");
  }
}
