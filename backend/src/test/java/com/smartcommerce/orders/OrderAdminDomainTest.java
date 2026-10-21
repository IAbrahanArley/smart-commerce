package com.smartcommerce.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartcommerce.orders.domain.AdminOrderQuery;
import com.smartcommerce.orders.domain.CancelReason;
import org.junit.jupiter.api.Test;

class OrderAdminDomainTest {
  @Test
  void listQueryAcceptsOnlyKnownStatusesAndPositiveNumberCursors() {
    assertThat(AdminOrderQuery.of(null, null)).isEqualTo(new AdminOrderQuery(null, null));
    assertThat(AdminOrderQuery.of(" ", "")).isEqualTo(new AdminOrderQuery(null, null));
    assertThat(AdminOrderQuery.of("EXPIRED", "21")).isEqualTo(new AdminOrderQuery("EXPIRED", 21L));
    assertThat(AdminOrderQuery.of("PAID", null)).isEqualTo(new AdminOrderQuery("PAID", null));
    assertThat(AdminOrderQuery.of("REFUNDED", null))
        .isEqualTo(new AdminOrderQuery("REFUNDED", null));
    for (String status : new String[] {"CHARGEBACK", "paid", "expired", "PENDING_PAYMENT;"})
      assertThatThrownBy(() -> AdminOrderQuery.of(status, null))
          .isInstanceOf(IllegalArgumentException.class);
    for (String cursor : new String[] {"0", "-1", "01", "1.5", "9999999999999999999", "abc"})
      assertThatThrownBy(() -> AdminOrderQuery.of(null, cursor))
          .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void cancelReasonIsOneTrimmedLineOfThreeToThreeHundredCharacters() {
    assertThat(CancelReason.of("  Cliente desistiu  ").text()).isEqualTo("Cliente desistiu");
    assertThat(CancelReason.of("abc").text()).isEqualTo("abc");
    assertThat(CancelReason.of("é".repeat(300)).text()).hasSize(300);
    for (String invalid :
        new String[] {null, "", "   ", "ab", "x".repeat(301), "linha\nquebrada", "tab\tno"})
      assertThatThrownBy(() -> CancelReason.of(invalid))
          .isInstanceOf(IllegalArgumentException.class);
  }
}
