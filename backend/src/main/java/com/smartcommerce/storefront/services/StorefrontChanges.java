package com.smartcommerce.storefront.services;

import com.smartcommerce.storefront.domain.StorefrontChanged;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Called by the services that change what a storefront shows (SC-047). The notice reaches the
 * storefront only after the transaction commits; a rolled-back change sends nothing.
 */
@Service
public class StorefrontChanges {
  private final ApplicationEventPublisher events;

  public StorefrontChanges(ApplicationEventPublisher events) {
    this.events = events;
  }

  public void changed(UUID tenant) {
    events.publishEvent(new StorefrontChanged(tenant));
  }
}
