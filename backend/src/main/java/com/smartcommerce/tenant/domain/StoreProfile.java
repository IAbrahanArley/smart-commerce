package com.smartcommerce.tenant.domain;

/** The stored profile of the store; {@code version} 0 means nothing was saved yet. */
public record StoreProfile(StoreProfileSettings settings, boolean hasLogo, long version) {}
