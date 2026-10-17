package com.smartcommerce.identity.mail;

/** Development delivery port; adapters must never log recipient or link. */
@FunctionalInterface
public interface VerificationMail {
  void send(String recipient, String activationLink);

  default void cleanup() {}
}
