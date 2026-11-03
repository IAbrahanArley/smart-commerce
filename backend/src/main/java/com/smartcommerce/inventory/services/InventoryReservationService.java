package com.smartcommerce.inventory.services;

import com.smartcommerce.inventory.domain.InventoryException;
import com.smartcommerce.inventory.domain.ReservationLine;
import com.smartcommerce.inventory.domain.ReservationPolicy;
import com.smartcommerce.inventory.domain.ReservationResult;
import com.smartcommerce.inventory.domain.SaleResult;
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
   * Deducts a paid order's stock, all lines or none (SC-014, ADR-0013 P4, P7). Joins the caller's
   * READ COMMITTED transaction, which holds the order lock. Balances are locked in variant order,
   * as in {@link #reserve}. A line is covered by the order's own reservation still in force plus
   * what is available to anyone; this covers the normal case (reservation in force) and the late
   * payment one (reservation expired or released: reserve again). Covered: each balance goes down,
   * a SALE movement by the system actor is appended and the order's reservations become CONSUMED.
   * Not covered: nothing is deducted and the order's reservations are released, so the store
   * decides (P4: never an automatic refund).
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public SaleResult sell(UUID tenant, UUID order, List<ReservationLine> lines, String reason) {
    if (tenant == null || order == null || lines == null || lines.isEmpty())
      throw new IllegalArgumentException("Invalid sale");
    var ordered = lines.stream().sorted(Comparator.comparing(ReservationLine::variantId)).toList();
    var ids = ordered.stream().map(ReservationLine::variantId).toList();
    if (new HashSet<>(ids).size() != ids.size())
      throw new IllegalArgumentException("Duplicated variant");
    var now = ReservationPolicy.now(clock);
    var levels =
        inventory.lockLevels(tenant, ids, now).stream()
            .collect(Collectors.toMap(StockLevel::variantId, Function.identity()));
    var own = reservations.activeQuantities(tenant, order, now);
    boolean covered =
        ordered.stream()
            .allMatch(
                line -> {
                  StockLevel level = levels.get(line.variantId());
                  return level != null
                      && line.quantity() <= level.onHand()
                      && line.quantity()
                          <= (long) level.available() + own.getOrDefault(line.variantId(), 0);
                });
    if (!covered) {
      reservations.release(tenant, order, now);
      return new SaleResult(false);
    }
    for (var line : ordered) {
      int after =
          inventory
              .deduct(tenant, line.variantId(), line.quantity())
              .orElseThrow(() -> new IllegalStateException("Locked balance changed"));
      inventory.appendSystem(
          "SALE",
          tenant,
          line.variantId(),
          order,
          saleRequest(order, line.variantId()),
          -line.quantity(),
          after,
          reason,
          now);
    }
    reservations.consume(tenant, order, now);
    return new SaleResult(true);
  }

  /**
   * Returns a refunded order's units to stock (SC-016, ADR-0013 P9), exactly what its SALE
   * movements took out, as SALE_REVERSAL movements by the system actor. Only called when the store
   * chose to restock. Balances are locked in variant order; the request key per order and variant
   * makes a second reversal impossible. Returns the number of lines restored (0 for an order paid
   * without stock).
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public int reverseSale(UUID tenant, UUID order, String reason) {
    if (tenant == null || order == null) throw new IllegalArgumentException("Invalid reversal");
    var sold = inventory.soldUnits(tenant, order);
    if (sold.isEmpty()) return 0;
    var now = ReservationPolicy.now(clock);
    inventory.lockLevels(tenant, sold.keySet(), now);
    for (var line : sold.entrySet()) {
      int after = inventory.restore(tenant, line.getKey(), line.getValue());
      inventory.appendSystem(
          "SALE_REVERSAL",
          tenant,
          line.getKey(),
          order,
          reversalRequest(order, line.getKey()),
          line.getValue(),
          after,
          reason,
          now);
    }
    return sold.size();
  }

  /**
   * Takes a counter sale's units out of the stock the storefront also sells (SC-053, ADR-0019 C6),
   * all or nothing. Joins the caller's READ COMMITTED transaction. Balances are locked in variant
   * order, as in {@link #reserve}, and a line must fit what is available to anyone: the
   * storefront's reservations in force are respected. Returns false, deducting nothing, when a line
   * does not fit.
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public boolean sellAtCounter(
      UUID tenant, UUID sale, UUID actor, List<ReservationLine> lines, String reason) {
    if (tenant == null || sale == null || actor == null || lines == null || lines.isEmpty())
      throw new IllegalArgumentException("Invalid counter sale");
    var ordered = lines.stream().sorted(Comparator.comparing(ReservationLine::variantId)).toList();
    var ids = ordered.stream().map(ReservationLine::variantId).toList();
    if (new HashSet<>(ids).size() != ids.size())
      throw new IllegalArgumentException("Duplicated variant");
    var now = ReservationPolicy.now(clock);
    var levels =
        inventory.lockLevels(tenant, ids, now).stream()
            .collect(Collectors.toMap(StockLevel::variantId, Function.identity()));
    boolean covered =
        ordered.stream()
            .allMatch(
                line -> {
                  StockLevel level = levels.get(line.variantId());
                  return level != null && line.quantity() <= level.available();
                });
    if (!covered) return false;
    for (var line : ordered) {
      int after =
          inventory
              .deduct(tenant, line.variantId(), line.quantity())
              .orElseThrow(() -> new IllegalStateException("Locked balance changed"));
      inventory.appendCounter(
          "COUNTER_SALE",
          tenant,
          line.variantId(),
          sale,
          counterRequest("counter-sale:", sale, line.variantId()),
          -line.quantity(),
          after,
          reason,
          actor,
          now);
    }
    return true;
  }

  /** Puts a cancelled counter sale's units back, exactly what it took out (SC-053). */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public int reverseCounterSale(UUID tenant, UUID sale, UUID actor, String reason) {
    if (tenant == null || sale == null || actor == null)
      throw new IllegalArgumentException("Invalid reversal");
    var sold = inventory.counterSoldUnits(tenant, sale);
    if (sold.isEmpty()) return 0;
    var now = ReservationPolicy.now(clock);
    inventory.lockLevels(tenant, sold.keySet(), now);
    for (var line : sold.entrySet()) {
      int after = inventory.restore(tenant, line.getKey(), line.getValue());
      inventory.appendCounter(
          "COUNTER_RETURN",
          tenant,
          line.getKey(),
          sale,
          counterRequest("counter-sale-reversal:", sale, line.getKey()),
          line.getValue(),
          after,
          reason,
          actor,
          now);
    }
    return sold.size();
  }

  static UUID counterRequest(String kind, UUID sale, UUID variant) {
    return UUID.nameUUIDFromBytes(
        (kind + sale + ":" + variant).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  static UUID reversalRequest(UUID order, UUID variant) {
    return UUID.nameUUIDFromBytes(
        ("sale-reversal:" + order + ":" + variant)
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  static UUID saleRequest(UUID order, UUID variant) {
    return UUID.nameUUIDFromBytes(
        ("sale:" + order + ":" + variant).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
}
