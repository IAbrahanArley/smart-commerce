package com.smartcommerce.identity.config;

import com.smartcommerce.identity.exceptions.IdentityException;
import com.smartcommerce.identity.mail.LocalVerificationMail;
import com.smartcommerce.identity.mail.VerificationMail;
import com.smartcommerce.identity.repositories.JdbcIdentityRepository;
import com.smartcommerce.identity.repositories.JdbcRateLimitRepository;
import com.smartcommerce.identity.repositories.JdbcVerificationRepository;
import com.smartcommerce.identity.security.IdentityPasswordEncoder;
import com.smartcommerce.identity.services.IdentityAuthenticationService;
import com.smartcommerce.identity.services.IdentityRateLimiter;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class IdentityConfiguration {
  @Bean
  TransactionTemplate identityTransactions(PlatformTransactionManager manager) {
    return new TransactionTemplate(manager);
  }

  @Bean
  IdentityRateLimiter identityRateLimiter(
      JdbcRateLimitRepository repository, TransactionTemplate identityTransactions) {
    return new IdentityRateLimiter(repository, identityTransactions);
  }

  @Bean
  @Profile("dev")
  @ConditionalOnMissingBean(VerificationMail.class)
  VerificationMail localVerificationMail(
      @Value("${identity.mail.directory:}") String directory,
      @Value("${identity.public-origin:http://127.0.0.1:3000}") String origin) {
    if (!isLoopback(URI.create(validatedOrigin(origin))))
      throw new IllegalStateException("DEV mail requires a loopback public origin");
    return new LocalVerificationMail(directory);
  }

  @Bean
  @Profile("!dev")
  @ConditionalOnMissingBean(VerificationMail.class)
  VerificationMail unavailableVerificationMail() {
    return (recipient, link) -> {
      throw new IdentityException(
          IdentityException.Reason.UNAVAILABLE,
          "Transporte de e-mail indisponível neste ambiente.");
    };
  }

  @Bean
  IdentityPasswordEncoder identityPasswordEncoder() {
    return new IdentityPasswordEncoder();
  }

  @Bean
  IdentityAuthenticationService identityAuthenticationService(
      JdbcIdentityRepository identities,
      JdbcVerificationRepository proofs,
      JdbcRateLimitRepository rateLimits,
      IdentityPasswordEncoder encoder,
      TransactionTemplate identityTransactions,
      IdentityRateLimiter limits,
      VerificationMail mail,
      @Value("${identity.public-origin:http://127.0.0.1:3000}") String origin,
      @Value("${identity.secure-cookies:false}") boolean secureCookies) {
    if ("https".equals(URI.create(validatedOrigin(origin)).getScheme()) && !secureCookies)
      throw new IllegalStateException("HTTPS origin requires secure cookies");
    return new IdentityAuthenticationService(
        identities,
        proofs,
        rateLimits,
        encoder,
        identityTransactions,
        limits,
        mail,
        validatedOrigin(origin));
  }

  public static String validatedOrigin(String configured) {
    URI uri;
    try {
      uri = URI.create(configured);
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("Invalid public origin");
    }
    boolean loopback = isLoopback(uri);
    if (uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || uri.getFragment() != null
        || (uri.getPath() != null && !uri.getPath().isEmpty())
        || !("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && loopback)))
      throw new IllegalStateException(
          "Public origin must be an explicit HTTPS origin or loopback DEV HTTP");
    return configured;
  }

  private static boolean isLoopback(URI uri) {
    return "localhost".equals(uri.getHost())
        || "127.0.0.1".equals(uri.getHost())
        || "[::1]".equals(uri.getHost());
  }

  @Bean
  IdentityMaintenance identityMaintenance(IdentityAuthenticationService identities) {
    return new IdentityMaintenance(identities);
  }

  public static final class IdentityMaintenance {
    private final IdentityAuthenticationService identities;

    IdentityMaintenance(IdentityAuthenticationService identities) {
      this.identities = identities;
    }

    @Scheduled(fixedDelay = 3600000, initialDelay = 3600000)
    public void cleanup() {
      try {
        identities.cleanup();
      } catch (org.springframework.dao.DataAccessException exception) {
        throw new IllegalStateException("Identity maintenance unavailable");
      }
    }
  }
}
