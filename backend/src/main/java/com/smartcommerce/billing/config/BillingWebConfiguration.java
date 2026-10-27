package com.smartcommerce.billing.config;

import com.smartcommerce.billing.services.BillingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Account standing at the edge (ADR-0017 B7), so every endpoint follows one rule:
 *
 * <ul>
 *   <li>Changes to a store ({@code POST}, {@code PUT}, {@code PATCH}, {@code DELETE} under {@code
 *       /api/tenants/{tenantId}}) need an owner in good standing, except order operations (cancel,
 *       refund), which keep serving buyers who already paid.
 *   <li>The public storefront of a store whose owner is not in good standing answers 404, like an
 *       unpublished store, except the status and payment of orders already placed.
 * </ul>
 *
 * Nothing is checked while billing is off. Reads and the panel itself always work, and no data is
 * removed. The service comes through a provider so web slice tests, which load configurers without
 * services, start without it.
 */
@Configuration
public class BillingWebConfiguration implements WebMvcConfigurer {
  private static final Set<String> WRITES = Set.of("POST", "PUT", "PATCH", "DELETE");
  private final ObjectProvider<BillingService> billing;

  public BillingWebConfiguration(ObjectProvider<BillingService> billing) {
    this.billing = billing;
  }

  private BillingService active() {
    var service = billing.getIfAvailable();
    return service != null && service.enabled() ? service : null;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(new TenantWriteGuard())
        .addPathPatterns("/api/tenants/*/**")
        .excludePathPatterns("/api/tenants/*/orders/**");
    registry
        .addInterceptor(new StorefrontGuard())
        .addPathPatterns("/api/public/stores/*/**")
        .excludePathPatterns("/api/public/stores/*/orders/*", "/api/public/stores/*/orders/*/**");
  }

  private final class TenantWriteGuard implements HandlerInterceptor {
    @Override
    public boolean preHandle(
        HttpServletRequest request, HttpServletResponse response, Object handler) {
      var service = active();
      if (service == null || !WRITES.contains(request.getMethod())) return true;
      String tenant = variable(request, "tenantId");
      if (tenant == null) return true;
      UUID id;
      try {
        id = UUID.fromString(tenant);
      } catch (IllegalArgumentException invalid) {
        return true; // the controller answers the malformed id
      }
      service.requireWritable(id); // a BillingException becomes 402 in BillingErrorHandler
      return true;
    }
  }

  private final class StorefrontGuard implements HandlerInterceptor {
    @Override
    public boolean preHandle(
        HttpServletRequest request, HttpServletResponse response, Object handler)
        throws IOException {
      var service = active();
      if (service == null) return true;
      String slug = variable(request, "slug");
      if (slug == null || service.storeOpen(slug)) return true;
      response.setStatus(404);
      response.setHeader("Cache-Control", "no-store");
      response.setContentType("application/json;charset=UTF-8");
      response.getWriter().write("{\"message\":\"Loja não encontrada.\"}");
      return false;
    }
  }

  @SuppressWarnings("unchecked")
  private static String variable(HttpServletRequest request, String name) {
    var variables =
        (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    return variables == null ? null : variables.get(name);
  }
}
