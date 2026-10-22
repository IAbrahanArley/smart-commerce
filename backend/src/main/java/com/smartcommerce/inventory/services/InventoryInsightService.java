package com.smartcommerce.inventory.services;

import com.smartcommerce.inventory.domain.ReservationPolicy;
import com.smartcommerce.inventory.domain.StockInsight;
import com.smartcommerce.inventory.repositories.InventoryInsightRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stock lists of the dashboard (SC-022, ADR-0015 F8): low stock (available ≤ {@link #LOW_STOCK})
 * and items without turnover (stock and no sale in {@link #STALE_AFTER}). Internal API: the caller
 * has already checked the OWNER of {@code tenant}.
 */
@Service
public class InventoryInsightService {
  public static final int LOW_STOCK = 5;
  public static final Duration STALE_AFTER = Duration.ofDays(30);
  private final InventoryInsightRepository insights;
  private final Clock clock;

  public InventoryInsightService(InventoryInsightRepository insights, Clock clock) {
    this.insights = insights;
    this.clock = clock;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Lists lists(UUID tenant, int limit) {
    Instant now = ReservationPolicy.now(clock);
    Instant since = now.minus(STALE_AFTER);
    return new Lists(
        insights.lowStock(tenant, now, LOW_STOCK, limit),
        insights.lowStockCount(tenant, now, LOW_STOCK),
        insights.withoutSales(tenant, now, since, limit),
        insights.withoutSalesCount(tenant, now, since));
  }

  public record Lists(
      List<StockInsight> lowStock,
      long lowStockCount,
      List<StockInsight> withoutSales,
      long withoutSalesCount) {}
}
