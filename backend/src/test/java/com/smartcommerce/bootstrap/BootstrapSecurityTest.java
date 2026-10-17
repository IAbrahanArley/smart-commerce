package com.smartcommerce.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartcommerce.identity.services.IdentityAuthenticationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(
    controllers = BootstrapSecurityTest.ProbeController.class,
    excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({BootstrapSecurityConfiguration.class, BootstrapSecurityTest.ProbeController.class})
class BootstrapSecurityTest {
  @MockitoBean IdentityAuthenticationService authentication;
  @Autowired MockMvc mvc;
  @Autowired ApplicationContext context;
  @Autowired SecurityFilterChain chain;

  @Test
  void csrfRemainsEnabledAndThereIsNoGeneratedUser() {
    assertThat(chain.getFilters()).anyMatch(CsrfFilter.class::isInstance);
    assertThat(context.getBeansOfType(UserDetailsService.class)).isEmpty();
  }

  @Test
  void onlyExactHealthGetRoutesArePublic() throws Exception {
    for (String path :
        new String[] {
          "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"
        }) {
      mvc.perform(get(path)).andExpect(status().isOk());
    }
  }

  @Test
  void allOtherRoutesAndHealthSubpathsAreDenied() throws Exception {
    for (String path : new String[] {"/login", "/actuator/env", "/actuator/health/db"}) {
      mvc.perform(get(path)).andExpect(status().isForbidden());
    }
    mvc.perform(get("/api/tenants")).andExpect(status().isUnauthorized());
    mvc.perform(post("/actuator/health")).andExpect(status().isForbidden());
    mvc.perform(post("/actuator/health").with(csrf())).andExpect(status().isForbidden());
  }

  @RestController
  static class ProbeController {
    @GetMapping({"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"})
    String probe() {
      return "probe";
    }
  }
}
