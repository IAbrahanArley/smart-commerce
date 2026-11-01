package com.smartcommerce.tenant.dto.response;

import java.util.UUID;

/** The company the signed-in account now works for (SC-049). */
public record AcceptedInvitation(UUID tenantId, String storeName) {}
