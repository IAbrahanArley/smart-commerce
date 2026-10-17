package com.smartcommerce.identity.security;

import com.smartcommerce.identity.domain.IdentityStatus;
import com.smartcommerce.identity.services.IdentityAuthenticationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Server-side absolute timeout and revocation checks on every authenticated request. */
public final class IdentitySessionFilter extends OncePerRequestFilter {
  public static final String AUTH_VERSION = "identity.authVersion";
  public static final String AUTHENTICATED_AT = "identity.authenticatedAt";
  private final IdentityAuthenticationService identities;

  public IdentitySessionFilter(IdentityAuthenticationService identities) {
    this.identities = identities;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.isAuthenticated()
        && !(authentication instanceof AnonymousAuthenticationToken)) {
      boolean valid = false;
      try {
        var session = request.getSession(false);
        if (session != null
            && session.getAttribute(AUTH_VERSION) instanceof Long version
            && session.getAttribute(AUTHENTICATED_AT) instanceof Long timestamp) {
          Instant authenticated = Instant.ofEpochMilli(timestamp);
          var user = identities.findUser(UUID.fromString(authentication.getName()));
          valid =
              !authenticated.isAfter(Instant.now())
                  && authenticated.plus(Duration.ofHours(8)).isAfter(Instant.now())
                  && user.isPresent()
                  && user.get().status() == IdentityStatus.ACTIVE
                  && user.get().verifiedAt() != null
                  && user.get().authVersion() == version;
        }
      } catch (IllegalArgumentException ignored) {
        /* Invalid session fails closed. */
      } catch (DataAccessException exception) {
        response.setStatus(503);
        response.setHeader("Retry-After", "5");
        return;
      }
      if (!valid) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        response.setStatus(401);
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
