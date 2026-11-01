package com.smartcommerce.tenant.domain;

/**
 * The stored profile of the store; {@code version} 0 means nothing was saved yet. The logo size is
 * null without a logo.
 */
public record StoreProfile(
    StoreProfileSettings settings,
    boolean hasLogo,
    Integer logoWidth,
    Integer logoHeight,
    long version) {}
