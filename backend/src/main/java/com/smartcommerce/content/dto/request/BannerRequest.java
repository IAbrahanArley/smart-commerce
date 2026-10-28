package com.smartcommerce.content.dto.request;

import com.smartcommerce.content.domain.BannerInput;
import java.time.Instant;

/** A home banner as typed by the owner; validated by {@link BannerInput}. */
public record BannerRequest(
    String alt,
    String title,
    String linkKind,
    String linkTarget,
    Instant startsAt,
    Instant endsAt) {
  public BannerInput input() {
    return BannerInput.of(alt, title, linkKind, linkTarget, startsAt, endsAt);
  }
}
