package com.smartcommerce.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartcommerce.storefront.domain.CatalogQuery;
import com.smartcommerce.storefront.domain.CatalogSort;
import com.smartcommerce.storefront.domain.PublicCategoryTree;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** SC-042: what the catalog accepts from the query string, and the public category tree. */
class CatalogQueryTest {
  private static CatalogQuery query(String search, String sort) {
    return CatalogQuery.parse(null, null, null, null, null, search, sort, null);
  }

  @Test
  void searchBecomesPrefixTermsWithoutAccentsOrSyntax() {
    assertThat(query("Óculos  de SOL", null).search()).isEqualTo("oculos:* & de:* & sol:*");
    assertThat(query("oculos') | ('x", null).search()).isEqualTo("oculos:* & x:*");
    assertThat(query("!!!", null).search()).isNull();
    assertThat(query("a b c d e f g h", null).search().split("&")).hasSize(6);
    assertThatThrownBy(() -> query("a".repeat(101), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void relevanceOnlyWithASearch() {
    assertThat(query("camiseta", null).sort()).isEqualTo(CatalogSort.RELEVANCE);
    assertThat(query(null, null).sort()).isEqualTo(CatalogSort.RECENT);
    assertThat(query(null, "relevancia").sort()).isEqualTo(CatalogSort.RECENT);
    assertThat(query(null, "menor-preco").sort()).isEqualTo(CatalogSort.PRICE_ASC);
    assertThatThrownBy(() -> query(null, "nome")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void filtersAreBoundedAndNormalized() {
    CatalogQuery query =
        CatalogQuery.parse(
            "feminino",
            List.of("agil,solar", "agil"),
            "49,9",
            "150",
            List.of("Tamanho:M", "Tamanho:G", "Cor:Azul", "Tamanho:M"),
            null,
            null,
            "2");
    assertThat(query.brands()).containsExactly("agil", "solar");
    assertThat(query.priceMin()).isEqualByComparingTo(new BigDecimal("49.90"));
    assertThat(query.options())
        .containsExactly(
            Map.entry("Tamanho", List.of("M", "G")), Map.entry("Cor", List.of("Azul")));
    assertThat(query.offset()).isEqualTo(24);
    for (String[] invalid :
        new String[][] {
          {"Feminino", null, null, null, null}, {null, "Ágil", null, null, null},
          {null, null, "10", "5", null}, {null, null, "1.234", null, null},
          {null, null, null, null, "Tamanho"}, {null, null, null, null, ":M"}
        })
      assertThatThrownBy(
              () ->
                  CatalogQuery.parse(
                      invalid[0],
                      invalid[1] == null ? null : List.of(invalid[1]),
                      invalid[2],
                      invalid[3],
                      invalid[4] == null ? null : List.of(invalid[4]),
                      null,
                      null,
                      null))
          .isInstanceOf(IllegalArgumentException.class);
    for (String page : new String[] {"0", "501", "-1", "x"})
      assertThatThrownBy(() -> CatalogQuery.parse(null, null, null, null, null, null, null, page))
          .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void hiddenParentsHideTheirSubtree() {
    UUID feminino = UUID.randomUUID(), camisetas = UUID.randomUUID(), orphan = UUID.randomUUID();
    // "orphan" is published but its parent is not in the published list.
    PublicCategoryTree tree =
        new PublicCategoryTree(
            List.of(
                new PublicCategoryTree.Node(feminino, null, "Feminino", "feminino"),
                new PublicCategoryTree.Node(camisetas, feminino, "Camisetas", "camisetas"),
                new PublicCategoryTree.Node(orphan, UUID.randomUUID(), "Órfã", "orfa")));
    assertThat(tree.bySlug("orfa")).isEmpty();
    assertThat(tree.reachable(camisetas)).isTrue();
    assertThat(tree.subtree(feminino)).containsExactly(feminino, camisetas);
    assertThat(tree.path(camisetas).stream().map(PublicCategoryTree.Node::slug))
        .containsExactly("feminino", "camisetas");
    assertThat(tree.total(feminino, Map.of(feminino, 2L, camisetas, 3L, orphan, 9L))).isEqualTo(5);
  }
}
