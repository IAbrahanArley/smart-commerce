package com.smartcommerce.tenant.domain;

/** Stored delivery configuration; version 0 means the owner has never saved it. */
public record StoreFulfillment(FulfillmentSettings settings, long version) {}
