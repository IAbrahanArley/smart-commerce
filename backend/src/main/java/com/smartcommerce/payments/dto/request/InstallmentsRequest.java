package com.smartcommerce.payments.dto.request;

import tools.jackson.databind.JsonNode;

/**
 * The most card installments the store offers (SC-042): a JSON integer from 1 to 12, or {@code
 * null} to leave Mercado Pago's default.
 */
public record InstallmentsRequest(JsonNode maxInstallments) {
  public Integer value() {
    if (maxInstallments == null || maxInstallments.isNull()) return null;
    if (!maxInstallments.isIntegralNumber()
        || !maxInstallments.canConvertToInt()
        || maxInstallments.asInt() < 1
        || maxInstallments.asInt() > 12) throw new IllegalArgumentException("Installments");
    return maxInstallments.asInt();
  }
}
