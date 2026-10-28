package com.smartcommerce.content.dto.request;

/** The page text in the product description format; empty removes the page (SC-043). */
public record PageRequest(String bodyHtml) {}
