package com.smartcommerce.catalog.dto.request;

/** A category name, validated by the service (1 to 60 characters). */
public record CategoryRequest(String name) {}
