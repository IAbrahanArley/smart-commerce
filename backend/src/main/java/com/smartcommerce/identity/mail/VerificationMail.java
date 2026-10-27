package com.smartcommerce.identity.mail;

/** Development delivery port; adapters must never log recipient or link. */
@FunctionalInterface
public interface VerificationMail {
  void send(String recipient, String activationLink);

  /** Password recovery link (MVP block 1a); adapters with their own wording override it. */
  default void sendPasswordReset(String recipient, String resetLink) {
    send(recipient, resetLink);
  }

  default void cleanup() {}
}
