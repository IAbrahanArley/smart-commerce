package com.smartcommerce.payments.domain;

/** The seller account and its decrypted access token. {@link #toString()} never prints it. */
public record SellerCredentials(String providerUserId, String accessToken) {
  @Override
  public String toString() {
    return "SellerCredentials[" + providerUserId + "]";
  }
}
