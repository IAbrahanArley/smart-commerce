package com.smartcommerce.tenant.exceptions;

/** Outcome of an employee operation (SC-049); the controller advice owns HTTP translation. */
public final class StaffException extends RuntimeException {
  public enum Reason {
    /** The company is not the actor's own, or the employee or invitation does not exist. */
    MISSING,
    INVALID_EMAIL,
    /** The e-mail already belongs to the company's OWNER or one of its employees. */
    ALREADY_MEMBER,
    /** Unknown, expired, canceled or already used token. */
    INVALID_INVITATION,
    /** The invitation was sent to another e-mail than the signed-in account's. */
    WRONG_ACCOUNT,
    /** Too many invitation e-mails for this owner or recipient. */
    THROTTLED,
    /** The store's owner cannot take this employee now (plan limit or account not in standing). */
    OWNER_LIMIT,
    /** Two requests created the same open invitation at once. */
    CONFLICT
  }

  private final Reason reason;

  public StaffException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
