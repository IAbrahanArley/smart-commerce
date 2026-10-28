package com.smartcommerce.content.domain;

import java.util.regex.Pattern;

/**
 * What the store shows about the company (SC-043, ADR-0018 V8/V13): the announcement bar, legal
 * name, CNPJ, phone, address and other social networks (Instagram and WhatsApp stay in the store
 * profile). Every field is optional; blank means "not shown". Inputs are normalized here:
 *
 * <ul>
 *   <li>CNPJ: any formatting, stored as 14 digits, check digits verified;
 *   <li>phone: a Brazilian number in any formatting, stored as E.164 digits;
 *   <li>Facebook, TikTok, YouTube: the handle, with or without "@" or the profile URL.
 * </ul>
 *
 * Anything else invalid raises {@link IllegalArgumentException}.
 */
public record CompanyInfo(
    String announcement,
    String legalName,
    String cnpj,
    String phone,
    String address,
    String facebook,
    String tiktok,
    String youtube) {
  private static final Pattern FACEBOOK =
      Pattern.compile("(?i)(?:https?://)?(?:www\\.|m\\.)?facebook\\.com/([A-Za-z0-9.]{1,100})/?");
  private static final Pattern TIKTOK =
      Pattern.compile("(?i)(?:https?://)?(?:www\\.)?tiktok\\.com/@([A-Za-z0-9._]{1,30})/?");
  private static final Pattern YOUTUBE =
      Pattern.compile("(?i)(?:https?://)?(?:www\\.)?youtube\\.com/@([A-Za-z0-9._-]{1,100})/?");

  public static final CompanyInfo EMPTY =
      new CompanyInfo(null, null, null, null, null, null, null, null);

  public static CompanyInfo of(
      String announcement,
      String legalName,
      String cnpj,
      String phone,
      String address,
      String facebook,
      String tiktok,
      String youtube) {
    return new CompanyInfo(
        line(announcement, 120),
        line(legalName, 120),
        cnpj(cnpj),
        phone(phone),
        line(address, 240),
        handle(facebook, FACEBOOK, "[A-Za-z0-9.]{1,100}"),
        handle(tiktok, TIKTOK, "[A-Za-z0-9._]{1,30}"),
        handle(youtube, YOUTUBE, "[A-Za-z0-9._-]{1,100}"));
  }

  private static String line(String raw, int max) {
    String value = blank(raw);
    if (value == null) return null;
    value = value.replaceAll(" {2,}", " ");
    if (value.length() > max || value.chars().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException("Invalid text");
    return value;
  }

  /** 14 digits whose two check digits match (Receita Federal module 11), not all equal. */
  static String cnpj(String raw) {
    String value = blank(raw);
    if (value == null) return null;
    if (!value.matches("[0-9./\\s-]{14,20}")) throw new IllegalArgumentException("Invalid CNPJ");
    String digits = value.replaceAll("\\D", "");
    if (digits.length() != 14 || digits.chars().distinct().count() == 1)
      throw new IllegalArgumentException("Invalid CNPJ");
    if (checkDigit(digits, 12) != digits.charAt(12) - '0'
        || checkDigit(digits, 13) != digits.charAt(13) - '0')
      throw new IllegalArgumentException("Invalid CNPJ");
    return digits;
  }

  private static int checkDigit(String digits, int length) {
    int sum = 0;
    int weight = length - 7;
    for (int i = 0; i < length; i++) {
      sum += (digits.charAt(i) - '0') * weight;
      weight = weight == 2 ? 9 : weight - 1;
    }
    int rest = sum % 11;
    return rest < 2 ? 0 : 11 - rest;
  }

  private static String phone(String raw) {
    String value = blank(raw);
    if (value == null) return null;
    if (!value.matches("[0-9+()\\s.-]{8,25}")) throw new IllegalArgumentException("Invalid phone");
    String digits = value.replaceAll("\\D", "");
    if (value.startsWith("+") && !digits.startsWith("55"))
      throw new IllegalArgumentException("Invalid phone");
    if (!value.startsWith("+") && (digits.length() == 10 || digits.length() == 11))
      digits = "55" + digits;
    if (!digits.matches("55[1-9][0-9]{9,10}")) throw new IllegalArgumentException("Invalid phone");
    return digits;
  }

  private static String handle(String raw, Pattern url, String format) {
    String value = blank(raw);
    if (value == null) return null;
    var matcher = url.matcher(value);
    String handle =
        matcher.matches() ? matcher.group(1) : value.startsWith("@") ? value.substring(1) : value;
    if (!handle.matches(format)) throw new IllegalArgumentException("Invalid handle");
    return handle;
  }

  private static String blank(String raw) {
    if (raw == null) return null;
    String value = raw.strip();
    return value.isEmpty() ? null : value;
  }
}
