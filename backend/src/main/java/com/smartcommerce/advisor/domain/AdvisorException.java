package com.smartcommerce.advisor.domain;

/** Assistant failures. Messages are codes only: never a question, an answer or a tool result. */
public final class AdvisorException extends RuntimeException {
  public enum Reason {
    /** Store absent or not owned by the actor: one 404 for both. */
    MISSING,
    /** The assistant is switched off in this environment (ADR-0016 A11). */
    DISABLED,
    /** The store used its monthly questions (A8). */
    QUOTA_EXCEEDED,
    /** The platform reached its monthly spending cap (A8). */
    GLOBAL_CAP_REACHED,
    /** A tool call with an unknown tool, an unknown argument or a value outside its list. */
    INVALID_TOOL_CALL,
    /** Empty question, or longer than 500 characters (SC-026). */
    INVALID_QUESTION,
    /** The model provider failed or rate-limited the question (SC-026). */
    PROVIDER_UNAVAILABLE
  }

  private final Reason reason;

  public AdvisorException(Reason reason) {
    super(reason.name(), null, false, false);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
