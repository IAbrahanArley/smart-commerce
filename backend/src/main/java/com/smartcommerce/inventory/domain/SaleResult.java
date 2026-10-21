package com.smartcommerce.inventory.domain;

/**
 * Outcome of deducting a paid order's stock (SC-014, ADR-0013 P4): {@code deducted} is false when
 * some line could not be covered, in which case nothing was deducted.
 */
public record SaleResult(boolean deducted) {}
