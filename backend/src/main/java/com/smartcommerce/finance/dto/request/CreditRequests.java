package com.smartcommerce.finance.dto.request;

/** Store credit bodies (SC-055); money as decimal text. */
public final class CreditRequests {
  private CreditRequests() {}

  /** A receipt into a drawer's open session ({@code cashSessionId}) or a bank account. */
  public record Receipt(String requestId, String amount, String cashSessionId, String accountId) {}
}
