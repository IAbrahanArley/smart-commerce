package com.smartcommerce.finance.dto.response;

import com.smartcommerce.finance.domain.CashSession;
import com.smartcommerce.finance.services.CashSessionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A cash session as its viewer sees it (SC-052). Money is text with two decimals. {@code
 * expectedAmount} and {@code difference} are null for employees, always (blind close), and for
 * everyone while the session is open. {@code movements} is null in the history list.
 */
public record CashSessionView(
    UUID id,
    UUID accountId,
    String accountName,
    String status,
    String openingAmount,
    String openedBy,
    Instant openedAt,
    UUID reopenedFrom,
    String reopenReason,
    String countedAmount,
    String expectedAmount,
    String difference,
    String closedBy,
    Instant closedAt,
    List<Movement> movements) {
  public record Movement(
      UUID id,
      String kind,
      String amount,
      String description,
      String recordedBy,
      Instant createdAt) {}

  public static CashSessionView of(CashSessionService.SessionDetail detail) {
    return of(
        detail.session(),
        detail.owner(),
        detail.movements().stream()
            .map(
                item ->
                    new Movement(
                        item.id(),
                        item.kind(),
                        money(item.amount()),
                        item.description(),
                        item.recordedBy(),
                        item.createdAt()))
            .toList());
  }

  public static CashSessionView of(CashSession session, boolean owner, List<Movement> movements) {
    return new CashSessionView(
        session.id(),
        session.accountId(),
        session.accountName(),
        session.status(),
        money(session.openingAmount()),
        session.openedBy(),
        session.openedAt(),
        session.reopenedFrom(),
        session.reopenReason(),
        money(session.countedAmount()),
        owner ? money(session.expectedAmount()) : null,
        owner ? money(session.difference()) : null,
        session.closedBy(),
        session.closedAt(),
        movements);
  }

  static String money(BigDecimal value) {
    return value == null ? null : value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
  }
}
