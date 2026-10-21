package com.smartcommerce.payments.services;

import com.smartcommerce.payments.domain.PaymentException;
import com.smartcommerce.payments.domain.PaymentNotification;
import com.smartcommerce.payments.dto.request.WebhookNotification;
import com.smartcommerce.payments.provider.PaymentProviders;
import com.smartcommerce.payments.repositories.PaymentEventRepository;
import com.smartcommerce.payments.repositories.PaymentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Webhook intake (SC-014, ADR-0013 P5): authenticate, record once, answer fast. Nothing is
 * processed here; {@link PaymentEventService} queries the provider later. The tenant is resolved
 * from the payment we stored for that provider order, never from the notification.
 */
@Service
public class PaymentWebhookService {
  private final PaymentProviders providers;
  private final PaymentRepository payments;
  private final PaymentEventRepository events;
  private final Clock clock;
  private final TransactionTemplate writes;

  public PaymentWebhookService(
      PaymentProviders providers,
      PaymentRepository payments,
      PaymentEventRepository events,
      Clock clock,
      PlatformTransactionManager transactions) {
    this.providers = providers;
    this.payments = payments;
    this.events = events;
    this.clock = clock;
    this.writes = new TransactionTemplate(transactions);
    this.writes.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  public boolean enabled() {
    return providers.webhookEnabled();
  }

  public void receive(
      String signature, String requestId, String dataId, String queryType, byte[] body) {
    if (!enabled()) throw new PaymentException(PaymentException.Reason.UNAVAILABLE);
    Instant now = now();
    if (!providers.webhook().valid(signature, requestId, dataId, now))
      throw new PaymentException(PaymentException.Reason.INVALID_SIGNATURE);
    PaymentNotification notification =
        WebhookNotification.parse(body, queryType, dataId, requestId);
    if (notification == null) return;
    writes.executeWithoutResult(
        tx -> {
          var owner = payments.ownerOfProviderOrder(notification.providerOrderId());
          // Recorded under the stored spelling, so processing finds the payment exactly.
          PaymentNotification routed =
              owner
                  .map(
                      found ->
                          new PaymentNotification(
                              notification.eventKey(),
                              notification.topic(),
                              notification.action(),
                              found.providerOrderId(),
                              notification.providerUserId()))
                  .orElse(notification);
          events.insert(routed, owner.map(found -> found.tenant()).orElse(null), now);
        });
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
