package com.smartcommerce.bootstrap;

import com.smartcommerce.identity.config.IdentityConfiguration;
import com.smartcommerce.identity.security.IdentityRequestFilter;
import com.smartcommerce.identity.security.IdentitySessionFilter;
import com.smartcommerce.identity.services.IdentityAuthenticationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/** Global identity does not authorize any tenant, role or business operation. */
@Configuration(proxyBeanMethods = false)
public class BootstrapSecurityConfiguration {
  /** Public reads never authenticate with, create, or reuse the administrative session. */
  @Bean
  @Order(0)
  SecurityFilterChain publicStorefrontSecurity(HttpSecurity http) throws Exception {
    return http.securityMatcher("/api/public/**")
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/public/stores/*",
                        "/api/public/stores/*/fulfillment",
                        "/api/public/stores/*/logo",
                        // SC-042: catalog with search, filters and facets.
                        "/api/public/stores/*/catalog",
                        "/api/public/stores/*/p/*",
                        "/api/public/stores/*/p/*/address",
                        "/api/public/stores/*/sitemap",
                        // SC-043: institutional pages.
                        "/api/public/stores/*/pages/*",
                        "/api/public/stores/*/banners/*/images/*",
                        "/api/public/stores/*/products/*",
                        "/api/public/stores/*/products/*/images",
                        "/api/public/stores/*/products/*/images/*/content",
                        "/api/public/stores/*/orders/*")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/public/stores/*/cart/quote",
                        "/api/public/stores/*/orders",
                        "/api/public/stores/*/orders/lookup",
                        "/api/public/stores/*/orders/*/payment",
                        "/api/public/webhooks/mercado-pago",
                        // SC-032: Stripe subscription events (HMAC signature, ADR-0017 B6).
                        "/api/public/webhooks/stripe")
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .sessionManagement(config -> config.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .securityContext(
            config -> config.securityContextRepository(new NullSecurityContextRepository()))
        // Reads, the side-effect-free cart quote, the checkout (SC-008) and the payment start
        // (SC-013, buyer token in a header) and the provider webhook (SC-014, HMAC signature):
        // no ambient authority
        // exists on this chain (no cookie or session; the buyer token travels in a header), so
        // CSRF does not apply; administrative mutations retain their CSRF protection.
        .csrf(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .build();
  }

  @Bean
  CookieCsrfTokenRepository identityCsrfRepository(
      @Value("${identity.secure-cookies:false}") boolean secure) {
    var csrf = new CookieCsrfTokenRepository();
    csrf.setCookieName(secure ? "__Host-SC-CSRF" : "SC-CSRF");
    csrf.setHeaderName("X-CSRF-TOKEN");
    csrf.setCookieCustomizer(
        cookie -> cookie.httpOnly(true).secure(secure).sameSite("Lax").path("/"));
    return csrf;
  }

  @Bean
  CookieSerializer identitySessionCookie(
      @Value("${identity.secure-cookies:false}") boolean secure) {
    var cookie = new DefaultCookieSerializer();
    cookie.setCookieName(secure ? "__Host-SCSESSION" : "SCSESSION");
    cookie.setCookiePath("/");
    cookie.setUseHttpOnlyCookie(true);
    cookie.setUseSecureCookie(secure);
    cookie.setSameSite("Lax");
    return cookie;
  }

  @Bean
  SecurityFilterChain bootstrapSecurity(
      HttpSecurity http,
      IdentityAuthenticationService identities,
      CookieCsrfTokenRepository csrf,
      @Value("${identity.public-origin:http://127.0.0.1:3000}") String origin)
      throws Exception {
    return http.authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers(
                        HttpMethod.GET,
                        "/actuator/health",
                        "/actuator/health/liveness",
                        "/actuator/health/readiness",
                        "/api/auth/csrf")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/auth/registration",
                        "/api/auth/activation",
                        "/api/auth/login",
                        "/api/auth/logout",
                        // MVP block 1a: password recovery, with CSRF like the other auth forms.
                        "/api/auth/password-reset/request",
                        "/api/auth/password-reset/confirmation")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/auth/me")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/tenants/*/products/*/images",
                        "/api/tenants/*/products/*/images/*/content",
                        "/api/tenants/*/products/*/variants/*/inventory")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/tenants/*/products/*/images",
                        "/api/tenants/*/products/*/variants",
                        "/api/tenants/*/products/*/variants/*/inventory")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.PUT,
                        "/api/tenants/*/products/*/images/order",
                        "/api/tenants/*/products/*/variants/*")
                    .authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/api/tenants/*/products/*/images/*")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.PUT,
                        "/api/tenants/*/publication",
                        "/api/tenants/*/products/*/publication")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/tenants", "/api/tenants/*")
                    .authenticated()
                    // SC-031: the signed-in owner's plan and limits (ADR-0017).
                    .requestMatchers(
                        HttpMethod.GET, "/api/billing/account", "/api/billing/subscription")
                    .authenticated()
                    // SC-032: hosted Checkout and customer portal of the owner's own account;
                    // ADR-0017 B13: plan changes, cancellation and resumption in the panel.
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/billing/checkout",
                        "/api/billing/portal",
                        "/api/billing/change/preview",
                        "/api/billing/change",
                        "/api/billing/cancel",
                        "/api/billing/resume")
                    .authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/api/billing/change")
                    .authenticated()
                    // SC-049: store employees (OWNER checked in the service) and acceptance of
                    // an invitation by the signed-in account it was sent to.
                    .requestMatchers(HttpMethod.GET, "/api/tenants/*/staff")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/tenants/*/staff/invitations",
                        "/api/staff-invitations/acceptance")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.DELETE,
                        "/api/tenants/*/staff/invitations/*",
                        "/api/tenants/*/staff/*")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/tenants")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/tenants/*/fulfillment")
                    .authenticated()
                    // MVP block 1c: product categories (OWNER checked in the service).
                    .requestMatchers(
                        HttpMethod.GET, "/api/tenants/*/categories", "/api/tenants/*/brands")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.POST, "/api/tenants/*/categories", "/api/tenants/*/brands")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.PUT,
                        "/api/tenants/*/categories/*",
                        // SC-041: storefront address, place in the tree and visibility.
                        "/api/tenants/*/categories/*/slug",
                        "/api/tenants/*/categories/*/placement",
                        "/api/tenants/*/categories/*/publication",
                        "/api/tenants/*/products/*/category",
                        // SC-043: additional categories of a product.
                        "/api/tenants/*/products/*/categories",
                        // SC-041: brand, address and "compare at" prices of a product.
                        "/api/tenants/*/products/*/storefront",
                        "/api/tenants/*/brands/*")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.DELETE, "/api/tenants/*/categories/*", "/api/tenants/*/brands/*")
                    .authenticated()
                    // MVP block 1b: store name, contacts and logo (OWNER checked in the query).
                    .requestMatchers(
                        HttpMethod.GET, "/api/tenants/*/profile", "/api/tenants/*/profile/logo")
                    .authenticated()
                    .requestMatchers(HttpMethod.PUT, "/api/tenants/*/profile")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/tenants/*/profile/logo")
                    .authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/api/tenants/*/profile/logo")
                    .authenticated()
                    .requestMatchers(HttpMethod.PUT, "/api/tenants/*/fulfillment")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.GET, "/api/tenants/*/orders", "/api/tenants/*/orders/*")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/tenants/*/orders/*/cancel",
                        "/api/tenants/*/orders/*/refund")
                    .authenticated()
                    // SC-012: Mercado Pago connection. The callback is a top-level GET from the
                    // provider redirect: the session cookie (SameSite=Lax) identifies the user and
                    // the single-use state stands in for CSRF, as in OAuth.
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/tenants/*/payments/mercado-pago",
                        "/api/payments/mercado-pago/callback")
                    .authenticated()
                    // SC-043: company details and institutional pages (OWNER checked in the
                    // service).
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/tenants/*/content/company",
                        "/api/tenants/*/content/pages",
                        "/api/tenants/*/content/banners",
                        "/api/tenants/*/content/banners/*/images/*",
                        "/api/tenants/*/content/sections")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.PUT,
                        "/api/tenants/*/content/company",
                        "/api/tenants/*/content/pages/*",
                        "/api/tenants/*/content/banners/order",
                        "/api/tenants/*/content/banners/*",
                        "/api/tenants/*/content/sections")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/tenants/*/content/banners",
                        "/api/tenants/*/content/banners/*/images/*")
                    .authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/api/tenants/*/content/banners/*")
                    .authenticated()
                    // SC-042: installment limit of the store (OWNER checked in the service).
                    .requestMatchers(HttpMethod.GET, "/api/tenants/*/payments/installments")
                    .authenticated()
                    .requestMatchers(HttpMethod.PUT, "/api/tenants/*/payments/installments")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.POST, "/api/tenants/*/payments/mercado-pago/authorization")
                    .authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/api/tenants/*/payments/mercado-pago")
                    .authenticated()
                    // SC-020 to SC-022: expenses, financial report, dashboard (OWNER checked in the
                    // query).
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/tenants/*/expenses",
                        "/api/tenants/*/finance",
                        "/api/tenants/*/dashboard",
                        "/api/tenants/*/alerts",
                        "/api/tenants/*/advisor")
                    .authenticated()
                    // SC-026: assistant question (OWNER, quota and switch checked in the service).
                    .requestMatchers(HttpMethod.POST, "/api/tenants/*/advisor/questions")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/tenants/*/expenses",
                        "/api/tenants/*/expenses/*/cancel",
                        // SC-051: paying a payable (OWNER checked in the service).
                        "/api/tenants/*/expenses/*/pay")
                    .authenticated()
                    // SC-050: accounts, statements, adjustments and transfers (OWNER checked in
                    // the service).
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/tenants/*/finance/accounts",
                        "/api/tenants/*/finance/accounts/*/statement")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/tenants/*/finance/accounts",
                        "/api/tenants/*/finance/accounts/*/adjustments",
                        "/api/tenants/*/finance/transfers")
                    .authenticated()
                    // SC-053: counter sales (OWNER or STAFF; cancel and settings OWNER only,
                    // checked in the service).
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/tenants/*/counter-sales",
                        "/api/tenants/*/counter-sales/products",
                        "/api/tenants/*/counter-sales/settings")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/tenants/*/counter-sales",
                        "/api/tenants/*/counter-sales/*/cancel",
                        // SC-054: spreadsheet import, OWNER only (checked in the service).
                        "/api/tenants/*/counter-sales/imports",
                        "/api/tenants/*/counter-sales/imports/preview")
                    .authenticated()
                    .requestMatchers(HttpMethod.PUT, "/api/tenants/*/counter-sales/settings")
                    .authenticated()
                    // SC-052: cash register (OWNER or STAFF, checked in the service).
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/tenants/*/cash",
                        "/api/tenants/*/cash/sessions",
                        "/api/tenants/*/cash/sessions/*")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/tenants/*/cash/sessions",
                        "/api/tenants/*/cash/sessions/*/movements",
                        "/api/tenants/*/cash/sessions/*/close",
                        "/api/tenants/*/cash/sessions/*/reopen")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.GET, "/api/tenants/*/products", "/api/tenants/*/products/*")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/tenants/*/products")
                    .authenticated()
                    .requestMatchers(HttpMethod.PUT, "/api/tenants/*/products/*")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/tenants/*/products/*/inventory")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/tenants/*/products/*/inventory")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/v3/api-docs.yaml",
                        "/swagger-ui.html",
                        "/swagger-ui/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .csrf(
            config ->
                config
                    .csrfTokenRepository(csrf)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .securityContext(config -> config.requireExplicitSave(true))
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .headers(
            headers ->
                headers.referrerPolicy(
                    policy -> policy.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
        .addFilterBefore(
            new IdentityRequestFilter(IdentityConfiguration.validatedOrigin(origin)),
            CsrfFilter.class)
        .addFilterBefore(new IdentitySessionFilter(identities), AuthorizationFilter.class)
        .exceptionHandling(
            errors ->
                errors.authenticationEntryPoint(
                    (request, response, exception) ->
                        response.setStatus(
                            request.getRequestURI().startsWith("/api/") ? 401 : 403)))
        .build();
  }
}
