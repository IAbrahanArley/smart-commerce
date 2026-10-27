package com.smartcommerce.identity.controllers;

import com.smartcommerce.identity.domain.IdentityUser;
import com.smartcommerce.identity.dto.request.ActivationRequest;
import com.smartcommerce.identity.dto.request.LoginRequest;
import com.smartcommerce.identity.dto.request.PasswordResetConfirmation;
import com.smartcommerce.identity.dto.request.PasswordResetRequest;
import com.smartcommerce.identity.dto.request.RegistrationRequest;
import com.smartcommerce.identity.exceptions.IdentityException;
import com.smartcommerce.identity.security.IdentitySessionFilter;
import com.smartcommerce.identity.services.IdentityAuthenticationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Identity")
@RestController
@RequestMapping("/api/auth")
public final class IdentityController {

  private final IdentityAuthenticationService identities;
  private final CookieCsrfTokenRepository csrf;
  private final HttpSessionSecurityContextRepository contexts =
      new HttpSessionSecurityContextRepository();

  public IdentityController(
      IdentityAuthenticationService identities, CookieCsrfTokenRepository csrf) {
    this.identities = identities;
    this.csrf = csrf;
  }

  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping("/registration")
  public ResponseEntity<Map<String, String>> registration(
      @RequestBody RegistrationRequest body, HttpServletRequest request) {
    identities.requestRegistration(body.email(), request.getRemoteAddr());
    return ResponseEntity.accepted()
        .body(
            Map.of(
                "message",
                "Se o endereço puder ser cadastrado, enviaremos as instruções de confirmação."));
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping("/activation")
  public ResponseEntity<Void> activation(
      @RequestBody ActivationRequest body, HttpServletRequest request) {
    identities.activate(body.token(), body.password(), request.getRemoteAddr());
    return ResponseEntity.noContent().build();
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping("/password-reset/request")
  public ResponseEntity<Map<String, String>> passwordResetRequest(
      @RequestBody PasswordResetRequest body, HttpServletRequest request) {
    identities.requestPasswordReset(body.email(), request.getRemoteAddr());
    return ResponseEntity.accepted()
        .body(
            Map.of(
                "message",
                "Se houver uma conta ativa com este e-mail, enviaremos um link para redefinir a"
                    + " senha."));
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping("/password-reset/confirmation")
  public ResponseEntity<Void> passwordResetConfirmation(
      @RequestBody PasswordResetConfirmation body, HttpServletRequest request) {
    identities.resetPassword(body.token(), body.password(), request.getRemoteAddr());
    return ResponseEntity.noContent().build();
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping("/login")
  public ResponseEntity<Void> login(
      @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
    IdentityUser user =
        identities.authenticate(body.email(), body.password(), request.getRemoteAddr());
    var authentication =
        UsernamePasswordAuthenticationToken.authenticated(user.id().toString(), null, List.of());
    new ChangeSessionIdAuthenticationStrategy().onAuthentication(authentication, request, response);
    new CsrfAuthenticationStrategy(csrf).onAuthentication(authentication, request, response);
    var session = request.getSession(true);
    session.setAttribute(IdentitySessionFilter.AUTH_VERSION, user.authVersion());
    session.setAttribute(IdentitySessionFilter.AUTHENTICATED_AT, System.currentTimeMillis());
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    contexts.saveContext(context, request, response);
    return ResponseEntity.noContent().build();
  }

  @SecurityRequirement(name = "session")
  @GetMapping("/me")
  public Map<String, String> me(Authentication authentication) {
    IdentityUser user =
        identities
            .findUser(UUID.fromString(authentication.getName()))
            .orElseThrow(
                () ->
                    new IdentityException(
                        IdentityException.Reason.INVALID_CREDENTIALS, "Sessão inválida."));
    return Map.of("id", user.id().toString(), "email", user.email().original());
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
    new SecurityContextLogoutHandler().logout(request, response, authentication);
    csrf.saveToken(null, request, response);
    return ResponseEntity.noContent().build();
  }
}
