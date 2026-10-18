package com.smartcommerce.inventory.services;

import com.smartcommerce.inventory.domain.InventoryException;
import com.smartcommerce.inventory.domain.ReservationLine;
import com.smartcommerce.inventory.domain.ReservationPolicy;
import com.smartcommerce.inventory.domain.ReservationResult;
import com.smartcommerce.inventory.domain.StockLevel;
import com.smartcommerce.inventory.repositories.InventoryRepository;
import com.smartcommerce.inventory.repositories.InventoryReservationRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal reservation API for the orders module (ADR-0012 §1). No public endpoint: {@code tenant}
 * must come from trusted context (published store resolved by the server, or OWNER membership).
 * Reservations never touch {@code inventory_balance} or the ledger.
 */
@Service
public class InventoryReservationService {
  static final int MAX_LINES = 100;
  private final InventoryRepository inventory;
  private final InventoryReservationRepository reservations;
  private final Clock clock;
  private final ReservationPolicy policy;

  public InventoryReservationService(
      InventoryRepository inventory,
      InventoryReservationRepository reservations,
      Clock clock,
      @Value("${inventory.reservation.ttl:PT30M}") Duration ttl) {
    this.inventory = inventory;
    this.reservations = reservations;
    this.clock = clock;
    this.policy = new ReservationPolicy(ttl);
  }

  /**
   * Reserves every line or nothing. Joins the caller's transaction (the order checkout), which must
   * run under READ COMMITTED. Throws MISSING when any variant is not in {@code tenant},
   * INSUFFICIENT_STOCK when any line exceeds availability and REQUEST_CONFLICT when the order
   * already has reservations.
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public ReservationResult reserve(UUID tenant, UUID order, List<ReservationLine> lines) {
    if (tenant == null || order == null || lines == null || lines.isEmpty())
      throw new IllegalArgumentException("Invalid reservation");
    if (lines.size() > MAX_LINES || lines.stream().anyMatch(line -> line == null))
      throw new IllegalArgumentException("Invalid reservation lines");
    var ordered = lines.stream().sorted(Comparator.comparing(ReservationLine::variantId)).toList();
    var ids = ordered.stream().map(ReservationLine::variantId).toList();
    if (new HashSet<>(ids).size() != ids.size())
      throw new IllegalArgumentException("Duplicated variant");
    var now = ReservationPolicy.now(clock);
    var levels =
        inventory.lockLevels(tenant, ids, now).stream()
            .collect(Collectors.toMap(StockLevel::variantId, Function.identity()));
    if (levels.size() != ids.size())
      throw new InventoryException(InventoryException.Reason.MISSING);
    if (reservations.exists(tenant, order))
      throw new InventoryException(InventoryException.Reason.REQUEST_CONFLICT);
    for (var line : ordered)
      if (line.quantity() > levels.get(line.variantId()).available())
        throw new InventoryException(InventoryException.Reason.INSUFFICIENT_STOCK);
    var expiresAt = policy.expiresAt(now);
    reservations.insert(tenant, order, ordered, now, expiresAt);
    return new ReservationResult(order, expiresAt, ordered);
  }

  /** Releases the order's ACTIVE reservations once; repeated calls return 0. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public int release(UUID tenant, UUID order) {
    if (tenant == null || order == null) throw new IllegalArgumentException("Invalid release");
    var now = ReservationPolicy.now(clock);
    var variants = reservations.activeVariants(tenant, order);
    if (variants.isEmpty()) return 0;
    inventory.lockLevels(tenant, variants, now);
    return reservations.release(tenant, order, now);
  }

  /**
   * Records the expired reservations of one expired order (SC-010). They no longer count toward
   * availability, so no balance lock is taken: the expiration batch can close many orders without
   * locking stock rows out of order. Repeated calls return 0.
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public int expireOrder(UUID tenant, UUID order) {
    if (tenant == null || order == null) throw new IllegalArgumentException("Invalid expiry");
    return reservations.expireOrder(tenant, order, ReservationPolicy.now(clock));
  }

  /**
   * Marks up to {@code limit} logically expired reservations as EXPIRED. Correctness never depends
   * on this call: expired rows stop counting as soon as {@code expires_at <= now}. Orders expire
   * through {@link #expireOrder}; this sweep is kept for rows without that path.
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public int expireDue(int limit) {
    if (limit < 1 || limit > 1000) throw new IllegalArgumentException("Invalid batch size");
    return reservations.expireDue(ReservationPolicy.now(clock), limit);
  }

  /**
   * Sprint 4 stub: consuming a reservation on confirmed payment must also append an outbound ledger
   * movement, which needs a new movement type (e.g. SALE) and a system actor decision (ADR-0012
   * §9). Not implemented until then.
   */
  public void consume(UUID tenant, UUID order) {
    throw new UnsupportedOperationException("Reservation consumption is planned for Sprint 4");
  }
}
