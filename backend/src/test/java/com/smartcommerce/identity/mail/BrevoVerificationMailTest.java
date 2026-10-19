package com.smartcommerce.identity.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartcommerce.identity.exceptions.IdentityException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class BrevoVerificationMailTest {
  private static final String KEY = "synthetic-brevo-key-for-tests-only";
  private static final String LINK = "https://admin.example.test/confirmar-email#token=a&b<c>\"d";
  private final AtomicReference<String> apiKey = new AtomicReference<>();
  private final AtomicReference<String> body = new AtomicReference<>();
  private final AtomicInteger status = new AtomicInteger(201);
  private HttpServer server;

  private String start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v3/smtp/email",
        exchange -> {
          apiKey.set(exchange.getRequestHeaders().getFirst("api-key"));
          body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          exchange.sendResponseHeaders(status.get(), -1);
          exchange.close();
        });
    server.start();
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/v3/smtp/email";
  }

  @AfterEach
  void stop() {
    if (server != null) server.stop(0);
  }

  @Test
  void sendsTheLinkThroughTheTransactionalApiWithTheKeyHeader() throws Exception {
    var mail = new BrevoVerificationMail(start(), KEY, "remetente@example.test", "Loja Teste");
    mail.send("comprador@example.test", LINK);
    assertThat(apiKey.get()).isEqualTo(KEY);
    JsonNode sent = JsonMapper.builder().build().readTree(body.get());
    assertThat(sent.get("sender").get("email").asString()).isEqualTo("remetente@example.test");
    assertThat(sent.get("to").get(0).get("email").asString()).isEqualTo("comprador@example.test");
    assertThat(sent.get("textContent").asString()).contains(LINK);
    // The link is escaped inside the HTML attribute, never injected as markup.
    assertThat(sent.get("htmlContent").asString())
        .contains("token=a&amp;b&lt;c&gt;&quot;d")
        .doesNotContain("<c>");
  }

  @Test
  void anyProviderFailureIsTheGenericUnavailableErrorWithoutSecretsOrRecipient() throws Exception {
    var mail = new BrevoVerificationMail(start(), KEY, "remetente@example.test", null);
    for (int code : new int[] {400, 401, 429, 500}) {
      status.set(code);
      assertThatThrownBy(() -> mail.send("comprador@example.test", LINK))
          .isInstanceOf(IdentityException.class)
          .hasFieldOrPropertyWithValue("reason", IdentityException.Reason.UNAVAILABLE)
          .satisfies(
              error ->
                  assertThat(error.getMessage())
                      .doesNotContain(KEY, "comprador@example.test", "token"));
    }
    server.stop(0);
    server = null;
    assertThatThrownBy(() -> mail.send("comprador@example.test", LINK))
        .isInstanceOf(IdentityException.class);
  }

  @Test
  void configurationIsValidatedAtStartup() {
    assertThatThrownBy(
            () -> new BrevoVerificationMail("http://api.example.test/v3", KEY, "a@b.test", null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("HTTPS");
    assertThatThrownBy(() -> new BrevoVerificationMail("", " ", "a@b.test", null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageNotContaining(KEY);
    assertThatThrownBy(() -> new BrevoVerificationMail("", KEY, "sem-arroba", null))
        .isInstanceOf(IllegalStateException.class);
  }
}
