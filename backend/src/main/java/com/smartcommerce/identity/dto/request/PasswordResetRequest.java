package com.smartcommerce.identity.dto.request;

/** Password recovery request (MVP block 1a); the answer never reveals whether it exists. */
public record PasswordResetRequest(String email) {
  @Override
  public String toString() {
    return "PasswordResetRequest[REDACTED]";
  }
}
