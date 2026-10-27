package com.smartcommerce.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartcommerce.tenant.domain.StoreProfileSettings;
import org.junit.jupiter.api.Test;

/** MVP block 1b: contacts are normalized before they reach the database. */
class StoreProfileSettingsTest {
  @Test
  void normalizesCommonWaysOfWritingContacts() {
    var settings =
        StoreProfileSettings.of(
            "  Loja da Ana  ", " contato@loja.example ", "(11) 98888-7777", "@Loja.Ana");
    assertThat(settings.name()).isEqualTo("Loja da Ana");
    assertThat(settings.contactEmail()).isEqualTo("contato@loja.example");
    assertThat(settings.whatsapp()).isEqualTo("5511988887777");
    assertThat(settings.instagram()).isEqualTo("loja.ana");

    assertThat(StoreProfileSettings.of("Loja", "", "+55 21 3333-4444", null).whatsapp())
        .isEqualTo("552133334444");
    assertThat(
            StoreProfileSettings.of("Loja", null, null, "https://www.instagram.com/loja_ana/")
                .instagram())
        .isEqualTo("loja_ana");
    var empty = StoreProfileSettings.of("Loja", " ", "", "   ");
    assertThat(empty.contactEmail()).isNull();
    assertThat(empty.whatsapp()).isNull();
    assertThat(empty.instagram()).isNull();
  }

  @Test
  void rejectsInvalidValues() {
    for (String name : new String[] {"", "   ", "x".repeat(121), "Loja\nNova"})
      assertThatThrownBy(() -> StoreProfileSettings.of(name, null, null, null))
          .isInstanceOf(IllegalArgumentException.class);
    for (String email : new String[] {"sem-arroba", "a@b", "a b@loja.example", "@loja.example"})
      assertThatThrownBy(() -> StoreProfileSettings.of("Loja", email, null, null))
          .as(email)
          .isInstanceOf(IllegalArgumentException.class);
    for (String phone : new String[] {"123", "(11) 9888", "+1 555 010 0000", "abc11988887777"})
      assertThatThrownBy(() -> StoreProfileSettings.of("Loja", null, phone, null))
          .as(phone)
          .isInstanceOf(IllegalArgumentException.class);
    for (String handle : new String[] {"@", "loja ana", "x".repeat(31), "https://evil.example/a"})
      assertThatThrownBy(() -> StoreProfileSettings.of("Loja", null, null, handle))
          .as(handle)
          .isInstanceOf(IllegalArgumentException.class);
  }
}
