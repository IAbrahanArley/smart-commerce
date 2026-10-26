package com.smartcommerce.tenant.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What the store shows about itself (MVP block 1b, PRD item 10): its name and optional public
 * contacts. Inputs are normalized here, so the database CHECKs only ever see clean values:
 *
 * <ul>
 *   <li>name: trimmed, 1 to 120 characters, one line;
 *   <li>e-mail: trimmed, one address;
 *   <li>WhatsApp: any formatting of a Brazilian number ("(11) 98888-7777", "+55 11 …"), stored as
 *       E.164 digits;
 *   <li>Instagram: a handle, with or without "@" or the profile URL.
 * </ul>
 *
 * Blank contacts mean "not shown". Anything else invalid raises {@link IllegalArgumentException}.
 */
public record StoreProfileSettings(
    String name, String contactEmail, String whatsapp, String instagram) {
  private static final Pattern EMAIL = Pattern.compile("[^@\\s]{1,64}@[^@\\s]{1,189}");
  private static final Pattern DOMAIN = Pattern.compile("[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+");
  private static final Pattern HANDLE = Pattern.compile("[A-Za-z0-9._]{1,30}");
  private static final Pattern PROFILE_URL =
      Pattern.compile("(?i)(https?://)?(www\\.)?instagram\\.com/([A-Za-z0-9._]{1,30})/?");

  public static StoreProfileSettings of(
      String name, String contactEmail, String whatsapp, String instagram) {
    return new StoreProfileSettings(
        name(name), email(contactEmail), whatsapp(whatsapp), instagram(instagram));
  }

  private static String name(String raw) {
    String value = raw == null ? "" : raw.strip();
    if (value.isEmpty() || value.length() > 120 || value.chars().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException("Invalid store name");
    return value;
  }

  private static String email(String raw) {
    String value = blank(raw);
    if (value == null) return null;
    if (value.length() > 254 || !EMAIL.matcher(value).matches() || !DOMAIN.matcher(value).matches())
      throw new IllegalArgumentException("Invalid contact e-mail");
    return value;
  }

  private static String whatsapp(String raw) {
    String value = blank(raw);
    if (value == null) return null;
    if (!value.matches("[0-9+()\\s.-]{8,25}"))
      throw new IllegalArgumentException("Invalid WhatsApp");
    String digits = value.replaceAll("\\D", "");
    // An explicit country code must be Brazil's; without one, a 10/11-digit number gets it.
    if (value.startsWith("+") && !digits.startsWith("55"))
      throw new IllegalArgumentException("Invalid WhatsApp");
    if (!value.startsWith("+") && (digits.length() == 10 || digits.length() == 11))
      digits = "55" + digits;
    if (!digits.matches("55[1-9][0-9]{9,10}"))
      throw new IllegalArgumentException("Invalid WhatsApp");
    return digits;
  }

  private static String instagram(String raw) {
    String value = blank(raw);
    if (value == null) return null;
    var url = PROFILE_URL.matcher(value);
    String handle =
        url.matches() ? url.group(3) : value.startsWith("@") ? value.substring(1) : value;
    if (!HANDLE.matcher(handle).matches()) throw new IllegalArgumentException("Invalid Instagram");
    return handle.toLowerCase(Locale.ROOT);
  }

  private static String blank(String raw) {
    if (raw == null) return null;
    String value = raw.strip();
    return value.isEmpty() ? null : value;
  }
}
