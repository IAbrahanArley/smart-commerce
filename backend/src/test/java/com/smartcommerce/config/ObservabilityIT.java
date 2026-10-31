package com.smartcommerce.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * MVP block 2 (minimal observability): every response carries a correlation id that also marks the
 * log lines of its request, a received id is reused only when well formed, and PostgreSQL errors
 * never carry the rows they refused.
 */
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ObservabilityIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;

  @Test
  void everyResponseCarriesAWellFormedCorrelationId() throws Exception {
    // Generated when absent, also for requests the security chain refuses.
    for (String path : new String[] {"/api/public/stores/nao-existe", "/api/tenants/x/content"}) {
      String generated = get(path, null).headers().firstValue("X-Request-Id").orElseThrow();
      assertThat(UUID.fromString(generated)).isNotNull();
    }
    assertThat(
            get("/api/public/stores/nao-existe", "storefront-1234abcd")
                .headers()
                .firstValue("X-Request-Id"))
        .contains("storefront-1234abcd");
    // Anything that is not letters, digits and hyphens (or is too short or too long) is replaced.
    for (String invalid :
        new String[] {"curto", "a b c d e f g h", "x".repeat(65), "id%0d%0aforged"}) {
      String id =
          get("/api/public/stores/nao-existe", invalid)
              .headers()
              .firstValue("X-Request-Id")
              .orElseThrow();
      assertThat(id).isNotEqualTo(invalid).matches("[0-9a-f-]{36}");
    }
  }

  @Test
  void requestLogLinesCarryTheIdAndDatabaseErrorsCarryNoRowData(CapturedOutput output)
      throws Exception {
    // The configured pattern prints the id the filter puts in the MDC on every line.
    org.slf4j.MDC.put(RequestIdFilter.MDC_KEY, "observability-check-01");
    try {
      org.slf4j.LoggerFactory.getLogger(ObservabilityIT.class).warn("Synthetic line");
    } finally {
      org.slf4j.MDC.remove(RequestIdFilter.MDC_KEY);
    }
    assertThat(output.getAll()).contains("WARN [observability-check-01]");

    Throwable failure =
        catchThrowable(
            () ->
                jdbc.update(
                    "INSERT INTO tenant (id, company_name, created_at) VALUES (?, ?, now())",
                    null,
                    "Dado Pessoal Sintetico 98888-7777"));
    assertThat(failure).isNotNull();
    assertThat(failure.getMessage()).doesNotContain("Dado Pessoal Sintetico", "98888-7777");
  }

  private HttpResponse<String> get(String path, String requestId) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(20));
    if (requestId != null) request.header("X-Request-Id", requestId);
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }
}
