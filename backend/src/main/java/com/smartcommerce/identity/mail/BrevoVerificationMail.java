package com.smartcommerce.identity.mail;

import com.smartcommerce.identity.exceptions.IdentityException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verification mail through Brevo's transactional HTTP API (ADR-0014 H1): {@code POST
 * /v3/smtp/email} with the {@code api-key} header. Used where SMTP is blocked (Render free). The
 * API key, recipient and link never reach logs or exception messages; any failure is the same
 * generic UNAVAILABLE error the DEV mailbox uses.
 */
public final class BrevoVerificationMail implements VerificationMail {
  static final String DEFAULT_ENDPOINT = "https://api.brevo.com/v3/smtp/email";
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private final URI endpoint;
  private final String apiKey;
  private final String senderEmail;
  private final String senderName;
  private final HttpClient client;

  public BrevoVerificationMail(
      String endpoint, String apiKey, String senderEmail, String senderName) {
    this.endpoint = validatedEndpoint(endpoint);
    if (apiKey == null || apiKey.isBlank())
      throw new IllegalStateException("identity.mail.brevo.api-key is required");
    if (senderEmail == null || !senderEmail.matches("[^@\\s]{1,64}@[^@\\s]{1,255}"))
      throw new IllegalStateException("identity.mail.sender-email must be a valid address");
    this.apiKey = apiKey;
    this.senderEmail = senderEmail;
    this.senderName = senderName == null || senderName.isBlank() ? "Smart Commerce" : senderName;
    this.client =
        HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  @Override
  public void send(String recipient, String activationLink) {
    deliver(body(recipient, activationLink));
  }

  @Override
  public void sendPasswordReset(String recipient, String resetLink) {
    deliver(resetBody(recipient, resetLink));
  }

  @Override
  public void sendStaffInvitation(String recipient, String storeName, String invitationLink) {
    deliver(invitationBody(recipient, storeName, invitationLink));
  }

  private void deliver(String json) {
    HttpResponse<Void> response;
    try {
      response =
          client.send(
              HttpRequest.newBuilder(endpoint)
                  .timeout(TIMEOUT)
                  .header("api-key", apiKey)
                  .header("Content-Type", "application/json")
                  .header("Accept", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofString(json))
                  .build(),
              HttpResponse.BodyHandlers.discarding());
    } catch (IOException failure) {
      throw unavailable();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw unavailable();
    }
    if (response.statusCode() < 200 || response.statusCode() > 299) throw unavailable();
  }

  String body(String recipient, String activationLink) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("sender", Map.of("email", senderEmail, "name", senderName));
    body.put("to", List.of(Map.of("email", recipient)));
    body.put("subject", "Confirme seu e-mail — Smart Commerce");
    body.put(
        "textContent",
        "Abra o link e defina sua senha explicitamente:\n"
            + activationLink
            + "\n\nO link expira em 30 minutos. Se não solicitou, ignore esta mensagem.\n");
    body.put(
        "htmlContent",
        "<p>Abra o link e defina sua senha explicitamente:</p><p><a href=\""
            + html(activationLink)
            + "\">Confirmar e-mail e definir senha</a></p>"
            + "<p>O link expira em 30 minutos. Se não solicitou, ignore esta mensagem.</p>");
    return JSON.writeValueAsString(body);
  }

  String resetBody(String recipient, String resetLink) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("sender", Map.of("email", senderEmail, "name", senderName));
    body.put("to", List.of(Map.of("email", recipient)));
    body.put("subject", "Redefina sua senha — Smart Commerce");
    body.put(
        "textContent",
        "Recebemos um pedido para redefinir a senha da sua conta. Abra o link e escolha uma nova"
            + " senha:\n"
            + resetLink
            + "\n\nO link expira em 30 minutos e só pode ser usado uma vez. Se não pediu, ignore"
            + " esta mensagem: sua senha atual continua valendo.\n");
    body.put(
        "htmlContent",
        "<p>Recebemos um pedido para redefinir a senha da sua conta.</p><p><a href=\""
            + html(resetLink)
            + "\">Escolher uma nova senha</a></p>"
            + "<p>O link expira em 30 minutos e só pode ser usado uma vez. Se não pediu, ignore"
            + " esta mensagem: sua senha atual continua valendo.</p>");
    return JSON.writeValueAsString(body);
  }

  String invitationBody(String recipient, String storeName, String invitationLink) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("sender", Map.of("email", senderEmail, "name", senderName));
    body.put("to", List.of(Map.of("email", recipient)));
    // The store name is chosen by its owner: kept out of the subject, escaped in the body.
    body.put("subject", "Convite para a equipe de uma loja — Smart Commerce");
    body.put(
        "textContent",
        "Você foi convidado para a equipe da loja "
            + storeName
            + " no Smart Commerce, para operar o caixa e registrar vendas. Entre com este e-mail e"
            + " aceite o convite:\n"
            + invitationLink
            + "\n\nO convite expira em 7 dias e só pode ser usado uma vez. Se não esperava este"
            + " convite, ignore esta mensagem.\n");
    body.put(
        "htmlContent",
        "<p>Você foi convidado para a equipe da loja <strong>"
            + html(storeName)
            + "</strong> no Smart Commerce, para operar o caixa e registrar vendas.</p><p><a href=\""
            + html(invitationLink)
            + "\">Aceitar o convite</a></p>"
            + "<p>Entre com este e-mail para aceitar. O convite expira em 7 dias e só pode ser usado"
            + " uma vez. Se não esperava este convite, ignore esta mensagem.</p>");
    return JSON.writeValueAsString(body);
  }

  private static URI validatedEndpoint(String configured) {
    URI uri;
    try {
      uri = URI.create(configured == null || configured.isBlank() ? DEFAULT_ENDPOINT : configured);
    } catch (IllegalArgumentException invalid) {
      throw new IllegalStateException("Invalid identity.mail.brevo.endpoint");
    }
    boolean loopback = "127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost());
    if (uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || uri.getFragment() != null
        || !("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && loopback)))
      throw new IllegalStateException(
          "identity.mail.brevo.endpoint must be HTTPS (HTTP only on loopback for tests)");
    return uri;
  }

  private static String html(String value) {
    return value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;");
  }

  private static IdentityException unavailable() {
    return new IdentityException(
        IdentityException.Reason.UNAVAILABLE, "Serviço temporariamente indisponível.");
  }
}
