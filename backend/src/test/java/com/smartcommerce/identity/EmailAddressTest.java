package com.smartcommerce.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.smartcommerce.identity.domain.EmailAddress;
import org.junit.jupiter.api.Test;

class EmailAddressTest {
  @Test
  void normalizesOnlyDomainAndPreservesLocalCaseDotsAndTags() {
    var address = EmailAddress.parse("User.Name+tag@EXAMPLE.COM");
    assertThat(address.original()).isEqualTo("User.Name+tag@EXAMPLE.COM");
    assertThat(address.comparisonKey()).isEqualTo("User.Name+tag@example.com");
    assertThat(address.comparisonKey())
        .isNotEqualTo(EmailAddress.parse("user.Name+tag@example.com").comparisonKey())
        .isNotEqualTo(EmailAddress.parse("UserName+tag@example.com").comparisonKey())
        .isNotEqualTo(EmailAddress.parse("User.Name@example.com").comparisonKey());
  }

  @Test
  void idnaDomainAndItsAsciiRepresentationHaveSameComparisonKey() {
    assertThat(EmailAddress.parse("Owner@bücher.EXAMPLE").comparisonKey())
        .isEqualTo("Owner@xn--bcher-kva.example")
        .isEqualTo(EmailAddress.parse("Owner@xn--bcher-kva.example").comparisonKey());
    // JDK IDNA2003 maps sharp-s to ss; this documented alias is not IDNA2008 behavior.
    assertThat(EmailAddress.parse("Owner@faß.example").comparisonKey())
        .isEqualTo(EmailAddress.parse("Owner@fass.example").comparisonKey());
  }

  @Test
  void rejectsAmbiguousMalformedAndInjectionInputs() {
    for (String candidate :
        new String[] {
          "",
          "owner",
          "@example.com",
          "owner@",
          "a@@example.com",
          " owner@example.com",
          "owner@example.com ",
          "own er@example.com",
          "owner\r\nBcc:other@example.com",
          "ówner@example.com",
          "a..b@example.com",
          ".owner@example.com",
          "owner.@example.com",
          "owner@exam_ple.com",
          "owner@example..com",
          "owner@example.com.",
          "owner@-example.com",
          "owner@xn--.com",
          "owner@xn--a.example",
          "owner@\uD800.example"
        }) {
      assertThatIllegalArgumentException()
          .as("Invalid email contract")
          .isThrownBy(() -> EmailAddress.parse(candidate));
    }
    assertThatIllegalArgumentException().isThrownBy(() -> EmailAddress.parse(null));
  }

  @Test
  void checksLocalAndDnsLabelLengths() {
    assertThat(EmailAddress.parse("a".repeat(64) + "@example.com").comparisonKey())
        .startsWith("a".repeat(64) + "@");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> EmailAddress.parse("a".repeat(65) + "@example.com"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> EmailAddress.parse("owner@" + "a".repeat(64) + ".example"));
  }

  @Test
  void diagnosticRepresentationDoesNotExposeAddress() {
    assertThat(EmailAddress.parse("Synthetic.Owner@example.test").toString())
        .doesNotContain("Synthetic", "Owner", "example.test", "@");
  }
}
