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
                        "/api/public/stores/*/products/*",
                        "/api/public/stores/*/products/*/images",
                        "/api/public/stores/*/products/*/images/*/content",
                        "/api/public/stores/*/orders/*")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/public/stores/*/cart/quote",
                        "/api/public/stores/*/orders",
                        "/api/public/stores/*/orders/*/payment",
                        "/api/public/webhooks/mercado-pago")
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
                        "/api/auth/logout")
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
                    .requestMatchers(HttpMethod.POST, "/api/tenants")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/tenants/*/fulfillment")
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
                    .requestMatchers(
                        HttpMethod.POST, "/api/tenants/*/payments/mercado-pago/authorization")
                    .authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/api/tenants/*/payments/mercado-pago")
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
