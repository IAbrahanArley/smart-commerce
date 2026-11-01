package com.smartcommerce.storefront.dto.response;

/**
 * Public contacts and logo of a store (MVP block 1b): {@code whatsapp} in E.164 digits, {@code
 * instagram} without "@"; null fields are not shown. {@code logo} true means {@code
 * /api/public/stores/{slug}/logo} answers, and {@code logoWidth}/{@code logoHeight} give its size
 * so the page reserves its space.
 */
public record PublicStoreProfileView(
    String contactEmail,
    String whatsapp,
    String instagram,
    boolean logo,
    Integer logoWidth,
    Integer logoHeight) {}
