package com.smartcommerce.content.dto.response;

/**
 * One institutional page as the owner edits it; {@code bodyHtml} is empty when the store has not
 * written it (and the page is not shown).
 */
public record PageView(String kind, String slug, String title, String bodyHtml) {}
