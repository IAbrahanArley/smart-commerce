package com.smartcommerce.storefront.domain;

/**
 * A published store as buyers see it. Contacts are null when the store chose not to show them (MVP
 * block 1b); {@code logo} says whether a logo can be fetched.
 */
public record PublicStore(
    String name,
    String slug,
    String contactEmail,
    String whatsapp,
    String instagram,
    boolean logo,
    // SC-042: card installments offered, only while Mercado Pago is connected; else null.
    Integer maxInstallments) {}
