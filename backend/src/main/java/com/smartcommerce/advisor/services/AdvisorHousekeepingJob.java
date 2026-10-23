package com.smartcommerce.advisor.services;

import com.smartcommerce.advisor.repositories.AdvisorUsageRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Assistant housekeeping (SC-025, ADR-0016 A9): questions left PENDING by a crashed process stop
 * counting against the quota after {@link #STALE_AFTER}, and usage rows are kept for {@link
 * #RETENTION}. Bounded batches; several instances are safe.
 */
@Component
@ConditionalOnProperty(name = "advisor.housekeeping.enabled", matchIfMissing = true)
public class AdvisorHousekeepingJob {
  static final Duration STALE_AFTER = Duration.ofMinutes(10);
  static final Duration RETENTION = Duration.ofDays(90);
  private static final Logger LOG = LoggerFactory.getLogger(AdvisorHousekeepingJob.class);
  private final AdvisorUsageRepository usage;
  private final Clock clock;

  public AdvisorHousekeepingJob(AdvisorUsageRepository usage, Clock clock) {
    this.usage = usage;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT20M")
  public void run() {
    try {
      clean();
    } catch (RuntimeException failure) {
      LOG.warn("Advisor housekeeping failed: {}", failure.getClass().getSimpleName());
    }
  }

  public void clean() {
    Instant now = clock.instant();
    usage.cancelStale(now.minus(STALE_AFTER), now);
    usage.deleteOlderThan(now.minus(RETENTION));
  }
}
