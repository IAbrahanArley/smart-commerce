package com.smartcommerce.tenant.entities;

import java.io.Serializable;
import java.util.UUID;

public record MembershipId(UUID tenantId, UUID userId) implements Serializable {}
