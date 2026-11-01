package com.smartcommerce.identity.mail;

/** Development delivery port; adapters must never log recipient or link. */
@FunctionalInterface
public interface VerificationMail {
  void send(String recipient, String activationLink);

  /** Password recovery link (MVP block 1a); adapters with their own wording override it. */
  default void sendPasswordReset(String recipient, String resetLink) {
    send(recipient, resetLink);
  }

  /**
   * Invitation to work in a store (SC-049). {@code storeName} is the OWNER's own store name;
   * adapters with their own wording override it, the DEV mailbox just keeps the link.
   */
  default void sendStaffInvitation(String recipient, String storeName, String invitationLink) {
    send(recipient, invitationLink);
  }

  default void cleanup() {}
}
