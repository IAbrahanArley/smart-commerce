package com.smartcommerce.tenant.domain;

import com.smartcommerce.tenant.entities.Store;
import com.smartcommerce.tenant.entities.Tenant;

public record TenantDetails(Tenant tenant, Store store) {}
