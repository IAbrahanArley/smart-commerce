package com.smartcommerce.identity.dto.request;

public record RegistrationRequest(String email) {
  @Override
  public String toString() {
    return "Registration[REDACTED]";
  }
}
