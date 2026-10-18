package com.smartcommerce.catalog.domain;

public record ImageContent(byte[] bytes, String mediaType, int width, int height) {}
