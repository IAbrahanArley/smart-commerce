package com.smartcommerce.catalog.dto.request;

/** A brand name, validated by the service with the category name rule (1 to 60 characters). */
public record BrandRequest(String name) {}
