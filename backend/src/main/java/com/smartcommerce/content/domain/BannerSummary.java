package com.smartcommerce.content.domain;

import java.time.Instant;
import java.util.UUID;

/** A banner as the owner lists it (SC-043): its description and which images it already has. */
public record BannerSummary(
    UUID id,
    int position,
    String alt,
    String title,
    String linkKind,
    String linkTarget,
    Instant startsAt,
    Instant endsAt,
    boolean desktopImage,
    boolean mobileImage) {}
