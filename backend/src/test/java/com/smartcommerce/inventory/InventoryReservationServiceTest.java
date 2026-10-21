package com.smartcommerce.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartcommerce.inventory.domain.InventoryException;
import com.smartcommerce.inventory.domain.ReservationLine;
import com.smartcommerce.inventory.domain.StockLevel;
import com.smartcommerce.inventory.repositories.InventoryRepository;
import com.smartcommerce.inventory.repositories.InventoryReservationRepository;
import com.smartcommerce.inventory.services.InventoryReservationService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryReservationServiceTest {
  private static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");
  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID ORDER = UUID.randomUUID();
  private static final UUID LOW = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID HIGH = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private final InventoryRepository inventory = mock(InventoryRepository.class);
  private final InventoryReservationRepository reservations =
      mock(InventoryReservationRepository.class);
  private final InventoryReservationService service =
      new InventoryReservationService(
          inventory, reservations, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(30));

  @Test
  void reservesAllLinesSortedByVariantWithBackendDeadline() {
    when(inventory.lockLevels(eq(TENANT), anyCollection(), eq(NOW)))
        .thenReturn(List.of(new StockLevel(LOW, 5, 4, 1), new StockLevel(HIGH, 2, 0, 2)));
    var result =
        service.reserve(
            TENANT, ORDER, List.of(new ReservationLine(HIGH, 2), new ReservationLine(LOW, 1)));
    var sorted = List.of(new ReservationLine(LOW, 1), new ReservationLine(HIGH, 2));
    assertThat(result.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
    assertThat(result.lines()).isEqualTo(sorted);
    verify(inventory).lockLevels(TENANT, List.of(LOW, HIGH), NOW);
    verify(reservations).insert(TENANT, ORDER, sorted, NOW, NOW.plus(Duration.ofMinutes(30)));
  }

  @Test
  void rejectsWholeOrderWhenAnyLineExceedsAvailableOrVariantBelongsElsewhere() {
    when(inventory.lockLevels(eq(TENANT), anyCollection(), eq(NOW)))
        .thenReturn(List.of(new StockLevel(LOW, 5, 5, 0), new StockLevel(HIGH, 2, 0, 2)));
    assertThatThrownBy(
            () ->
                service.reserve(
                    TENANT,
                    ORDER,
                    List.of(new ReservationLine(HIGH, 1), new ReservationLine(LOW, 1))))
        .hasFieldOrPropertyWithValue("reason", InventoryException.Reason.INSUFFICIENT_STOCK);
    when(inventory.lockLevels(eq(TENANT), anyCollection(), eq(NOW)))
        .thenReturn(List.of(new StockLevel(HIGH, 2, 0, 2)));
    assertThatThrownBy(
            () ->
                service.reserve(
                    TENANT,
                    ORDER,
                    List.of(new ReservationLine(HIGH, 1), new ReservationLine(LOW, 1))))
        .hasFieldOrPropertyWithValue("reason", InventoryException.Reason.MISSING);
    verify(reservations, never()).insert(any(), any(), any(), any(), any());
  }

  @Test
  void rejectsInvalidRequestsAndKeepsConsumptionForSprintFour() {
    assertThatThrownBy(() -> service.reserve(TENANT, ORDER, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                service.reserve(
                    TENANT,
                    ORDER,
                    List.of(new ReservationLine(LOW, 1), new ReservationLine(LOW, 2))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.reserve(null, ORDER, List.of(new ReservationLine(LOW, 1))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.expireDue(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.sell(TENANT, ORDER, List.of(), "Venda"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                service.sell(
                    TENANT,
                    ORDER,
                    List.of(new ReservationLine(LOW, 1), new ReservationLine(LOW, 2)),
                    "Venda"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(service.release(TENANT, ORDER)).isZero();
  }
}
