package com.smartcommerce.content.domain;

/** The store is not the actor's (or does not exist): one uniform 404 (SC-043). */
public final class ContentException extends RuntimeException {
  public ContentException() {
    super("Store content unavailable");
  }
}
