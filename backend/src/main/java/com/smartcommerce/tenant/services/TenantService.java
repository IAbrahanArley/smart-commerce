package com.smartcommerce.tenant.services;

import com.smartcommerce.billing.services.BillingService;
import com.smartcommerce.storefront.services.StorefrontChanges;
import com.smartcommerce.tenant.domain.TenantDetails;
import com.smartcommerce.tenant.dto.response.TenantView;
import com.smartcommerce.tenant.exceptions.TenantException;
import com.smartcommerce.tenant.exceptions.TenantPersistenceException;
import com.smartcommerce.tenant.repositories.TenantRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {
  private final TenantRepository tenants;
  private final BillingService billing;

  private final StorefrontChanges changes;

  public TenantService(
      TenantRepository tenants, BillingService billing, StorefrontChanges changes) {
    this.changes = changes;
    this.tenants = tenants;
    this.billing = billing;
  }

  @Transactional
  public TenantView create(UUID actor, String companyName, String storeName, String slug) {
    if (slug != null) slug = slug.toLowerCase(Locale.ROOT);
    String company = name(companyName);
    String store = name(storeName);
    if (slug == null
        || slug.length() < 3
        || slug.length() > 63
        || !slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
      throw new IllegalArgumentException("Invalid slug");
    }
    billing.requireCompanySlot(actor); // SC-031: companies per owner's plan, good standing
    try {
      return view(
          tenants.create(
              actor, UUID.randomUUID(), UUID.randomUUID(), company, store, slug, Instant.now()),
          "OWNER");
    } catch (TenantPersistenceException exception) {
      throw new TenantException(TenantException.Reason.INVALID_IDENTITY, "Sessão inválida.");
    } catch (DataIntegrityViolationException exception) {
      throw new TenantException(
          TenantException.Reason.SLUG_CONFLICT,
          "Não foi possível usar esse endereço. Escolha outro.");
    }
  }

  @Transactional(readOnly = true)
  public List<TenantView> list(UUID actor) {
    // SC-049: companies where the actor is an employee come after his own, marked STAFF.
    return java.util.stream.Stream.concat(
            tenants.listOwned(actor).stream().map(details -> view(details, "OWNER")),
            tenants.listStaffed(actor).stream().map(details -> view(details, "STAFF")))
        .toList();
  }

  @Transactional(readOnly = true)
  public TenantView find(UUID actor, UUID tenant) {
    return tenants
        .findOwned(actor, tenant)
        .map(details -> view(details, "OWNER"))
        .or(() -> tenants.findStaffed(actor, tenant).map(details -> view(details, "STAFF")))
        .orElseThrow(
            () -> new TenantException(TenantException.Reason.MISSING, "Loja não encontrada."));
  }

  @Transactional
  public TenantView publish(UUID actor, UUID tenant, boolean published) {
    changes.changed(tenant);
    try {
      return view(tenants.publish(actor, tenant, published), "OWNER");
    } catch (TenantPersistenceException exception) {
      throw new TenantException(TenantException.Reason.MISSING, "Loja não encontrada.");
    }
  }

  private static TenantView view(TenantDetails details, String role) {
    var tenant = details.tenant();
    var store = details.store();
    return new TenantView(
        tenant.id(),
        tenant.companyName(),
        new TenantView.StoreView(store.id(), store.name(), store.slug(), store.status()),
        role);
  }

  static String name(String value) {
    if (value == null
        || value.length() > 512
        || value
            .codePoints()
            .anyMatch(
                code ->
                    Character.isISOControl(code)
                        || Character.getType(code) == Character.FORMAT
                        || Character.getType(code) == Character.SURROGATE
                        || Character.getType(code) == Character.LINE_SEPARATOR
                        || Character.getType(code) == Character.PARAGRAPH_SEPARATOR)) {
      throw new IllegalArgumentException("Invalid name");
    }
    String result = value.strip();
    if (result.isBlank() || result.codePointCount(0, result.length()) > 120) {
      throw new IllegalArgumentException("Invalid name");
    }
    return result;
  }
}
