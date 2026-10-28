package com.smartcommerce.tenant.services;

import com.smartcommerce.storefront.services.StorefrontChanges;
import com.smartcommerce.tenant.domain.FulfillmentSettings;
import com.smartcommerce.tenant.domain.StoreFulfillment;
import com.smartcommerce.tenant.dto.response.FulfillmentView;
import com.smartcommerce.tenant.exceptions.FulfillmentException;
import com.smartcommerce.tenant.exceptions.TenantPersistenceException;
import com.smartcommerce.tenant.repositories.FulfillmentRepository;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FulfillmentService {
  private final FulfillmentRepository fulfillment;

  private final StorefrontChanges changes;

  public FulfillmentService(FulfillmentRepository fulfillment, StorefrontChanges changes) {
    this.changes = changes;
    this.fulfillment = fulfillment;
  }

  @Transactional(readOnly = true)
  public FulfillmentView find(UUID actor, UUID tenant) {
    return view(fulfillment.findOwned(actor, tenant).orElseThrow(FulfillmentService::missing));
  }

  @Transactional
  public FulfillmentView update(
      UUID actor, UUID tenant, FulfillmentSettings settings, long version) {
    changes.changed(tenant);
    if (version < 0 || version == Long.MAX_VALUE)
      throw new IllegalArgumentException("Invalid version");
    boolean saved;
    try {
      saved = fulfillment.save(actor, tenant, settings, version, Instant.now());
    } catch (TenantPersistenceException exception) {
      throw missing();
    }
    if (!saved)
      throw new FulfillmentException(
          FulfillmentException.Reason.VERSION_CONFLICT,
          "A entrega foi alterada em outra sessão. Recarregue para ver a versão atual.");
    return view(fulfillment.findOwned(actor, tenant).orElseThrow(FulfillmentService::missing));
  }

  private static FulfillmentException missing() {
    return new FulfillmentException(FulfillmentException.Reason.MISSING, "Loja não encontrada.");
  }

  private static FulfillmentView view(StoreFulfillment stored) {
    var settings = stored.settings();
    var amount = settings.fixedShippingAmount();
    return new FulfillmentView(
        settings.pickupEnabled(),
        settings.pickupInstructions(),
        settings.fixedShippingEnabled(),
        amount == null ? null : amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
        "BRL",
        settings.fixedShippingArea(),
        stored.version());
  }
}
