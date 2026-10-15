package com.smartcommerce;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BootstrapIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
              DockerImageName.parse(
                      "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
                  .asCompatibleSubstituteFor("postgres"))
          .withUrlParam("socketTimeout", "2");

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired Flyway flyway;

  @Test
  void migrationIsAppliedOnceAndValidated() {
    assertThat(
            jdbc.queryForObject(
                "SELECT metadata_value FROM app_metadata WHERE metadata_key = 'bootstrap'",
                String.class))
        .isEqualTo("SC-001");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success",
                Integer.class))
        .isEqualTo(1);
    flyway.validate();
    assertThat(flyway.migrate().migrationsExecuted).isZero();
  }

  @Test
  void realHealthAndProbesAreHealthyAndDoNotDiscloseDetails() throws Exception {
    for (String path :
        new String[] {
          "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"
        }) {
      var response = request(path);
      assertThat(response.statusCode()).isEqualTo(200);
      var health = JsonMapper.builder().build().readTree(response.body());
      assertThat(health.get("status").asText()).isEqualTo("UP");
      assertThat(health.has("details")).isFalse();
      assertThat(health.has("components")).isFalse();
    }
    assertThat(request("/actuator/env").statusCode()).isEqualTo(403);
    assertThat(request("/login").statusCode()).isEqualTo(403);
  }

  @Test
  void readinessReflectsDatabaseFailureButLivenessStaysUp() throws Exception {
    try {
      POSTGRES.getDockerClient().pauseContainerCmd(POSTGRES.getContainerId()).exec();
      assertThat(request("/actuator/health/readiness").statusCode()).isEqualTo(503);
      assertThat(request("/actuator/health/liveness").statusCode()).isEqualTo(200);
    } finally {
      POSTGRES.getDockerClient().unpauseContainerCmd(POSTGRES.getContainerId()).exec();
      long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
      while (request("/actuator/health/readiness").statusCode() != 200) {
        if (System.nanoTime() > deadline) {
          throw new AssertionError("Database readiness did not recover after unpause");
        }
        Thread.sleep(200);
      }
    }
  }

  private HttpResponse<String> request(String path) throws Exception {
    return HttpClient.newHttpClient()
        .send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
  }
}
