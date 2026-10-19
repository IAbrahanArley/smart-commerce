package com.smartcommerce.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DatabaseUrlEnvironmentPostProcessorTest {
  @Test
  void jdbcUrlsPassThroughWithOnlyWhitespaceAndQuotesRemoved() {
    var connection =
        DatabaseUrlEnvironmentPostProcessor.normalize(
            "  \"jdbc:postgresql://db.internal:5432/app\" ", "app_user", "s3cret");
    assertThat(connection.url()).isEqualTo("jdbc:postgresql://db.internal:5432/app");
    assertThat(connection.username()).isEqualTo("app_user");
    assertThat(connection.password()).isEqualTo("s3cret");
  }

  @Test
  void renderConnectionStringsBecomeJdbcWithTheirCredentials() {
    var connection =
        DatabaseUrlEnvironmentPostProcessor.normalize(
            "postgresql://app_user:p%40ss%3Aword@dpg-example-a/app_db", null, " ");
    assertThat(connection.url()).isEqualTo("jdbc:postgresql://dpg-example-a:5432/app_db");
    assertThat(connection.username()).isEqualTo("app_user");
    assertThat(connection.password()).isEqualTo("p@ss:word");
    var external =
        DatabaseUrlEnvironmentPostProcessor.normalize(
            "postgres://u:p@host.example:6543/db?sslmode=require", null, null);
    assertThat(external.url()).isEqualTo("jdbc:postgresql://host.example:6543/db?sslmode=require");
  }

  @Test
  void explicitCredentialsWinAndErrorsNeverEchoTheValue() {
    var connection =
        DatabaseUrlEnvironmentPostProcessor.normalize(
            "postgresql://url_user:url_pass@host/db", "env_user", "env_pass");
    assertThat(connection.username()).isEqualTo("env_user");
    assertThat(connection.password()).isEqualTo("env_pass");
    assertThat(connection.toString()).doesNotContain("env_pass", "url_pass");
    assertThatThrownBy(
            () ->
                DatabaseUrlEnvironmentPostProcessor.normalize(
                    "postgresql://u:topsecret@host", null, null))
        .isInstanceOf(IllegalStateException.class)
        .satisfies(error -> assertThat(error.getMessage()).doesNotContain("topsecret"));
  }

  @Test
  void theNormalizedValuesOverrideTheDatasourceProperties() {
    var environment =
        new MockEnvironment()
            .withProperty("DB_URL", "postgresql://app_user:pw@dpg-example-a:5432/app_db")
            .withProperty("spring.datasource.url", "unused");
    new DatabaseUrlEnvironmentPostProcessor().postProcessEnvironment(environment, null);
    assertThat(environment.getProperty("spring.datasource.url"))
        .isEqualTo("jdbc:postgresql://dpg-example-a:5432/app_db");
    assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("app_user");
    assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("pw");
  }
}
