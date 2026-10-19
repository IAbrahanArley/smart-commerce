package com.smartcommerce.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Accepts the database URL in the forms hosting panels show (ADR-0014): {@code jdbc:postgresql://}
 * as before, or Render's {@code postgresql://user:password@host:port/database} connection string,
 * which is converted to JDBC with the credentials taken from it unless {@code DB_USERNAME} and
 * {@code DB_PASSWORD} are set. Surrounding whitespace and quotes are ignored. Values are never
 * logged or put in error messages: the URL may carry the password.
 */
public final class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {
  private static final String SOURCE = "smartCommerceDatabaseUrl";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    String url = environment.getProperty("DB_URL");
    if (url == null || url.isBlank()) return;
    Connection connection =
        normalize(
            url, environment.getProperty("DB_USERNAME"), environment.getProperty("DB_PASSWORD"));
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("spring.datasource.url", connection.url());
    if (connection.username() != null)
      properties.put("spring.datasource.username", connection.username());
    if (connection.password() != null)
      properties.put("spring.datasource.password", connection.password());
    environment.getPropertySources().addFirst(new MapPropertySource(SOURCE, properties));
  }

  record Connection(String url, String username, String password) {
    @Override
    public String toString() {
      return "Connection[redacted]";
    }
  }

  static Connection normalize(String rawUrl, String username, String password) {
    String url = unquote(rawUrl.strip());
    String explicitUser = blank(username) ? null : unquote(username.strip());
    String explicitPassword = blank(password) ? null : unquote(password.strip());
    if (!url.startsWith("postgres://") && !url.startsWith("postgresql://"))
      return new Connection(url, explicitUser, explicitPassword);
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException invalid) {
      throw new IllegalStateException("DB_URL is not a valid PostgreSQL connection string");
    }
    String path = uri.getRawPath();
    if (uri.getHost() == null || path == null || path.length() < 2)
      throw new IllegalStateException("DB_URL must include the host and the database name");
    String jdbc =
        "jdbc:postgresql://"
            + uri.getHost()
            + ":"
            + (uri.getPort() > 0 ? uri.getPort() : 5432)
            + path
            + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
    String user = explicitUser;
    String secret = explicitPassword;
    String info = uri.getRawUserInfo();
    if (info != null) {
      int colon = info.indexOf(':');
      String urlUser = decode(colon < 0 ? info : info.substring(0, colon));
      String urlPassword = colon < 0 ? null : decode(info.substring(colon + 1));
      if (user == null) user = urlUser;
      if (secret == null) secret = urlPassword;
    }
    return new Connection(jdbc, user, secret);
  }

  private static String unquote(String value) {
    if (value.length() >= 2
        && (value.startsWith("\"") && value.endsWith("\"")
            || value.startsWith("'") && value.endsWith("'")))
      return value.substring(1, value.length() - 1).strip();
    return value;
  }

  private static boolean blank(String value) {
    return value == null || unquote(value.strip()).isEmpty();
  }

  private static String decode(String value) {
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }
}
