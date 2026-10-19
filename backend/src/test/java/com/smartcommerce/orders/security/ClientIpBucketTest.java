package com.smartcommerce.orders.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClientIpBucketTest {
  @Test
  void ipv6AddressesShareTheirSlash64AndMappedIpv4CountsAsIpv4() {
    assertThat(ClientIpBucket.of("203.0.113.7")).isEqualTo("v4:203.0.113.7");
    assertThat(ClientIpBucket.of("::ffff:203.0.113.7")).isEqualTo("v4:203.0.113.7");
    assertThat(ClientIpBucket.of("2001:db8:1:2::1"))
        .isEqualTo(ClientIpBucket.of("2001:0DB8:0001:0002:ffff:ffff:ffff:fffe"))
        .isEqualTo("v6:20010db800010002/64");
    assertThat(ClientIpBucket.of("2001:db8:1:3::1"))
        .isNotEqualTo(ClientIpBucket.of("2001:db8:1:2::1"));
    assertThat(ClientIpBucket.of(null)).isEmpty();
    assertThat(ClientIpBucket.of("")).isEmpty();
  }

  @Test
  void malformedValuesAreKeptAsTheirOwnBucketWithoutResolution() {
    // Invalid literals are never resolved as host names (bracketed parsing only).
    assertThat(ClientIpBucket.of("1::2::3")).isEqualTo("raw:1::2::3");
    assertThat(ClientIpBucket.of("256.1.1.1")).isEqualTo("raw:256.1.1.1");
    assertThat(ClientIpBucket.of("Example")).isEqualTo("raw:example");
  }
}
