package com.smartcommerce.finance.dto.response;

import java.util.List;

/**
 * One page of the OWNER's cash history; {@code nextCursor} goes back as {@code before}, or null.
 */
public record CashHistoryView(List<CashSessionView> sessions, String nextCursor) {}
