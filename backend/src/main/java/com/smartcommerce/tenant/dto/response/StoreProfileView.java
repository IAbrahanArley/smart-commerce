package com.smartcommerce.tenant.dto.response;

/**
 * The store's profile as its OWNER edits it. {@code whatsapp} is E.164 digits ("5511988887777");
 * {@code instagram} is the handle without "@"; {@code version} 0 means never saved.
 */
public record StoreProfileView(
    String name,
    String contactEmail,
    String whatsapp,
    String instagram,
    boolean hasLogo,
    Integer logoWidth,
    Integer logoHeight,
    long version) {}
