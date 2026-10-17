package com.smartcommerce.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.smartcommerce.identity.domain.PasswordHash;
import com.smartcommerce.identity.security.IdentityPasswordEncoder;
import org.junit.jupiter.api.Test;

class PasswordHashTest {
  @Test
  void rejectsNoncanonicalIterationIdsAndCostsOutsideSecurityBounds() {
    for (String iterations : new String[] {"0600000", "599999", "2000001", "000000", "6000000"}) {
      String representation =
          "{pbkdf2-sha256-nfc-i" + iterations + "-s16-l256-v1}" + "ab".repeat(48);
      assertThatIllegalArgumentException().isThrownBy(() -> new PasswordHash(representation));
    }
    for (int iterations : new int[] {600_000, 2_000_000}) {
      String representation =
          "{" + IdentityPasswordEncoder.idForIterations(iterations) + "}" + "ab".repeat(48);
      assertThat(new PasswordHash(representation).iterations()).isEqualTo(iterations);
    }
  }
}
