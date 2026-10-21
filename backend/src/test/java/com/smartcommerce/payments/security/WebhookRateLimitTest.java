package com.smartcommerce.payments.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** SC-014 review: the failure ceiling, with a controlled clock instead of wall time. */
class WebhookRateLimitTest {
  @Test
  void allowsTheLimitPerWindowThenRefusesUntilTheNextWindow() {
    AtomicLong clock = new AtomicLong(1_000L);
    WebhookRateLimit limit = new WebhookRateLimit(clock::get);
    for (int i = 0; i < WebhookRateLimit.LIMIT; i++) assertThat(limit.tryAcquire()).isTrue();
    assertThat(limit.tryAcquire()).isFalse();
    clock.addAndGet(WebhookRateLimit.WINDOW_NANOS - 1);
    assertThat(limit.tryAcquire()).isFalse();
    clock.addAndGet(1);
    assertThat(limit.tryAcquire()).isTrue();
  }
}
