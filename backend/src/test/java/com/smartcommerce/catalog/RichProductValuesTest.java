package com.smartcommerce.catalog;

import static org.assertj.core.api.Assertions.*;

import com.smartcommerce.catalog.domain.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class RichProductValuesTest {
  @Test
  void nullArrayMembersAreRejectedAsInvalidInput() {
    var request =
        new com.smartcommerce.catalog.dto.request.ProductRequest(
            "Produto",
            null,
            null,
            null,
            null,
            null,
            "VARIABLE",
            java.util.Arrays.asList((com.smartcommerce.catalog.dto.request.VariantRequest) null));
    assertThatThrownBy(request::values).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(request::details).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new VariantValues(
                    ProductValues.parse("Produto", "SKU", "1", null),
                    java.util.Arrays.asList((VariantOption) null)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void htmlUsesOnlySevenFormattingTagsAndIsIdempotent() {
    String clean =
        ProductDescription.sanitize(
            "<p class='x' onclick='x()'>Texto <strong>real</strong><a href='javascript:x()'>link</a><img src=x onerror='x()'><script>alert(1)</script><iframe src='x'>bad</iframe><em>fim</em></p>");
    assertThat(clean).isEqualTo("<p>Texto <strong>real</strong>link<em>fim</em></p>");
    assertThat(ProductDescription.sanitize(clean)).isEqualTo(clean);
    assertThatThrownBy(() -> ProductDescription.sanitize("x".repeat(20001)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ProductDescription.sanitize("&".repeat(20000)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ProductDescription.sanitize("\uD800"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void optionsAreCanonicalAndRoundTripWithoutDelimiterCollisions() {
    var values = ProductValues.parse("Produto", "VAR-1", "1.01", null);
    var a =
        new VariantValues(
            values,
            List.of(new VariantOption(" Tamanho ", " P "), new VariantOption("Cor", "Azul")));
    var b =
        new VariantValues(
            values, List.of(new VariantOption("Cor", "Azul"), new VariantOption("Tamanho", "P")));
    assertThat(a.key()).isEqualTo(b.key());
    assertThat(VariantValues.decode(a.json())).isEqualTo(a.options());
    assertThat(new VariantOption("Cor", "Cafe\u0301").value()).isEqualTo("Café");
    assertThatThrownBy(
            () ->
                new VariantValues(
                    values, List.of(new VariantOption("Cor", "A"), new VariantOption("Cor", "B"))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new VariantOption("Cor", "x".repeat(41)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new VariantOption("Cor", "A\nB"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
