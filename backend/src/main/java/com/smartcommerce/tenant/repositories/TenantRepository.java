package com.smartcommerce.tenant.repositories;

import com.smartcommerce.tenant.domain.TenantDetails;
import com.smartcommerce.tenant.entities.*;
import com.smartcommerce.tenant.exceptions.TenantPersistenceException;
import com.smartcommerce.tenant.exceptions.TenantPersistenceException.Reason;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class TenantRepository {
  private final EntityManager entities;
  private final TenantJpaRepository tenants;
  private final TenantAccessRepository access;

  public TenantRepository(
      EntityManager entities, TenantJpaRepository tenants, TenantAccessRepository access) {
    this.entities = entities;
    this.tenants = tenants;
    this.access = access;
  }

  public TenantDetails create(
      UUID actor,
      UUID tenantId,
      UUID storeId,
      String company,
      String name,
      String slug,
      Instant now) {
    if (!access.lockActiveIdentity(actor))
      throw new TenantPersistenceException(Reason.INACTIVE_IDENTITY);
    Tenant tenant = new Tenant(tenantId, company, now);
    Store store = new Store(storeId, tenantId, name, slug, "DRAFT", now);
    entities.persist(tenant);
    entities.flush();
    entities.persist(new TenantMembership(tenantId, actor, now));
    entities.persist(store);
    entities.flush();
    return new TenantDetails(tenant, store);
  }

  public List<TenantDetails> listOwned(UUID actor) {
    return tenants.listOwned(actor).stream().map(this::details).toList();
  }

  public Optional<TenantDetails> findOwned(UUID actor, UUID tenant) {
    return tenants.findOwned(actor, tenant).map(this::details);
  }

  private TenantDetails details(Tenant tenant) {
    Store store =
        entities
            .createQuery("select s from Store s where s.tenantId=:tenant", Store.class)
            .setParameter("tenant", tenant.id())
            .getSingleResult();
    return new TenantDetails(tenant, store);
  }
}
