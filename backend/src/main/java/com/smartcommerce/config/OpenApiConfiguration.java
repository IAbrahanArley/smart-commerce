package com.smartcommerce.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("dev")
public class OpenApiConfiguration {
  @Bean
  OpenApiCustomizer sessionAndCsrfContract() {
    return api ->
        api.getPaths()
            .forEach(
                (path, item) ->
                    item.readOperationsMap()
                        .forEach(
                            (method, operation) -> {
                              SecurityRequirement required = new SecurityRequirement();
                              if (!path.startsWith("/api/auth/") || path.equals("/api/auth/me")) {
                                required.addList("session");
                              }
                              if (method != PathItem.HttpMethod.GET
                                  && method != PathItem.HttpMethod.HEAD) {
                                required.addList("csrf");
                              }
                              // One requirement object means AND: a tenant mutation needs both
                              // session and CSRF.
                              operation.setSecurity(
                                  required.isEmpty() ? List.of() : List.of(required));
                            }));
  }

  @Bean
  OpenAPI smartCommerceApi(@Value("${identity.secure-cookies:false}") boolean secure) {
    return new OpenAPI()
        .info(
            new Info()
                .title("Smart Commerce API")
                .version("v1")
                .description(
                    "API administrativa. Mutações exigem CSRF; recursos de loja exigem vínculo autorizado. Documentação somente DEV, autenticada e sem execução de operações pela UI."))
        .components(
            new Components()
                .addSecuritySchemes(
                    "session",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name(secure ? "__Host-SCSESSION" : "SCSESSION"))
                .addSecuritySchemes(
                    "csrf",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-CSRF-TOKEN")));
  }
}
