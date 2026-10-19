package com.smartcommerce.orders.security;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Admission bucket of a client address (ADR-0012 D4, SC-011 review). One IPv6 host usually owns a
 * whole /64, so IPv6 addresses are grouped by their /64 prefix; IPv4-mapped IPv6 addresses count as
 * their IPv4 address. Parsing never touches DNS: IPv4 is matched textually and IPv6 is parsed only
 * as a bracketed literal, which {@link InetAddress} never resolves.
 */
public final class ClientIpBucket {
  private static final Pattern IPV4 =
      Pattern.compile(
          "(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}");
  private static final Pattern IPV6_CHARS = Pattern.compile("[0-9A-Fa-f:.]{2,45}");

  private ClientIpBucket() {}

  public static String of(String address) {
    if (address == null || address.isEmpty()) return "";
    if (IPV4.matcher(address).matches()) return "v4:" + address;
    if (address.indexOf(':') < 0 || !IPV6_CHARS.matcher(address).matches())
      return "raw:" + address.toLowerCase(Locale.ROOT);
    InetAddress parsed;
    try {
      parsed = InetAddress.getByName("[" + address + "]");
    } catch (UnknownHostException invalid) {
      return "raw:" + address.toLowerCase(Locale.ROOT);
    }
    if (parsed instanceof Inet4Address) return "v4:" + parsed.getHostAddress();
    return "v6:" + HexFormat.of().formatHex(Arrays.copyOf(parsed.getAddress(), 8)) + "/64";
  }
}
