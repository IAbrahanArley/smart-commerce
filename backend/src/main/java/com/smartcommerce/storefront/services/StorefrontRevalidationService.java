package com.smartcommerce.storefront.services;

import com.smartcommerce.storefront.client.StorefrontRevalidationClient;
import com.smartcommerce.storefront.domain.StorefrontChanged;
import com.smartcommerce.storefront.repositories.StorefrontRepository;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * After a change commits, asks storefront-web to drop the cached pages of the tenant's store
 * (SC-047). Runs on a virtual thread, so the owner's request never waits for the storefront.
 */
@Service
public class StorefrontRevalidationService {
  private final StorefrontRepository stores;
  private final StorefrontRevalidationClient client;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  public StorefrontRevalidationService(
      StorefrontRepository stores, StorefrontRevalidationClient client) {
    this.stores = stores;
    this.client = client;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void changed(StorefrontChanged event) {
    if (!client.enabled()) return;
    stores
        .slugOf(event.tenant())
        .ifPresent(slug -> executor.execute(() -> client.revalidate(slug)));
  }
}
