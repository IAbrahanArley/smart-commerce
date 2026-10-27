package com.smartcommerce.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartcommerce.catalog.domain.CategoryTree;
import com.smartcommerce.catalog.domain.UrlSlug;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** SC-041: depth, cycles, placement and slugs of the category tree, without a database. */
class CategoryTreeTest {
  private final UUID feminino = UUID.randomUUID();
  private final UUID roupas = UUID.randomUUID();
  private final UUID camisetas = UUID.randomUUID();
  private final UUID outlet = UUID.randomUUID();
  private final CategoryTree tree =
      new CategoryTree(
          List.of(
              new CategoryTree.Node(outlet, null, 1, "outlet"),
              new CategoryTree.Node(feminino, null, 0, "feminino"),
              new CategoryTree.Node(roupas, feminino, 0, "roupas"),
              new CategoryTree.Node(camisetas, roupas, 0, "camisetas")));

  @Test
  void measuresDepthAndHeight() {
    assertThat(tree.depth(feminino)).isEqualTo(1);
    assertThat(tree.depth(camisetas)).isEqualTo(3);
    assertThat(tree.height(feminino)).isEqualTo(3);
    assertThat(tree.height(camisetas)).isEqualTo(1);
    assertThat(tree.parent(roupas)).isEqualTo(feminino);
    assertThat(tree.parent(feminino)).isNull();
  }

  @Test
  void detectsCycles() {
    assertThat(tree.isWithin(camisetas, feminino)).isTrue();
    assertThat(tree.isWithin(feminino, feminino)).isTrue();
    assertThat(tree.isWithin(outlet, feminino)).isFalse();
  }

  @Test
  void listsParentsBeforeChildrenBySiblingPosition() {
    assertThat(tree.depthFirst()).containsExactly(feminino, roupas, camisetas, outlet);
  }

  @Test
  void placesAmongSiblingsClampingThePosition() {
    assertThat(tree.placed(outlet, null, 0)).containsExactly(outlet, feminino);
    assertThat(tree.placed(feminino, null, 99)).containsExactly(outlet, feminino);
    assertThat(tree.placed(outlet, feminino, 0)).containsExactly(outlet, roupas);
  }

  @Test
  void derivesSlugsFromNamesAndAvoidsTakenOnes() {
    assertThat(UrlSlug.from("Óculos & Viseiras", "categoria", taken -> false).value())
        .isEqualTo("oculos-viseiras");
    assertThat(UrlSlug.from("  ", "categoria", taken -> false).value()).isEqualTo("categoria");
    Set<String> taken = Set.of("camisetas", "camisetas-2");
    assertThat(UrlSlug.from("Camisetas", "categoria", taken::contains).value())
        .isEqualTo("camisetas-3");
    assertThat(UrlSlug.from("x".repeat(100), "categoria", t -> false).value()).hasSize(60);
  }

  @Test
  void acceptsOnlyWellFormedSlugs() {
    assertThat(UrlSlug.parse("  Moda-Praia ").value()).isEqualTo("moda-praia");
    for (String invalid : new String[] {"", "moda praia", "-moda", "moda--praia", "ç", "a/b"})
      assertThatThrownBy(() -> UrlSlug.parse(invalid))
          .as(invalid)
          .isInstanceOf(IllegalArgumentException.class);
  }
}
