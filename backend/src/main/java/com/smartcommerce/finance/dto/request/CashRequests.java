package com.smartcommerce.finance.dto.request;

/** Cash register bodies (SC-052), validated by {@code CashInput}; money as decimal text. */
public final class CashRequests {
  private CashRequests() {}

  public record Open(String requestId, String accountId, String amount) {}

  /**
   * {@code type}: DROP, TOP_UP or EXPENSE; {@code accountId} optional, {@code category} only for
   * EXPENSE.
   */
  public record Movement(
      String requestId,
      String type,
      String amount,
      String reason,
      String accountId,
      String category) {}

  public record Close(String requestId, String counted) {}

  public record Reopen(String reason) {}
}
