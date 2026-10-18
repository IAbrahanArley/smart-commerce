package com.smartcommerce.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** UTC clock for deadlines computed by the backend (reservation expiry, availability). */
@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
