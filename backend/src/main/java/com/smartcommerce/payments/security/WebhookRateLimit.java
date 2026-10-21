package com.smartcommerce.payments.security;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.springframework.stereotype.Component;

/**
 * Per-process ceiling on failed webhook signatures (SC-014): a fixed window of {@link #LIMIT}
 * failures per {@link #WINDOW_NANOS}, past which failures get 429 instead of 401. Valid signed
 * deliveries never consult it; requests without the secret never reach the database.
 */
@Component
public class WebhookRateLimit {
  static final int LIMIT = 300;
  static final long WINDOW_NANOS = 10_000_000_000L;
  private final LongSupplier nanos;
  private final AtomicLong window = new AtomicLong();
  private final AtomicLong count = new AtomicLong();

  public WebhookRateLimit() {
    this(System::nanoTime);
  }

  WebhookRateLimit(LongSupplier nanos) {
    this.nanos = nanos;
    this.window.set(nanos.getAsLong());
  }

  public boolean tryAcquire() {
    long now = nanos.getAsLong();
    long start = window.get();
    if (now - start >= WINDOW_NANOS && window.compareAndSet(start, now)) count.set(0);
    return count.incrementAndGet() <= LIMIT;
  }
}
