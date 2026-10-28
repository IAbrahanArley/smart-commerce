package com.smartcommerce.storefront.services;

import com.smartcommerce.catalog.domain.ImageContent;
import com.smartcommerce.catalog.domain.ProductDescription;
import com.smartcommerce.catalog.domain.VariantValues;
import com.smartcommerce.content.domain.StorePageKind;
import com.smartcommerce.inventory.domain.ReservationPolicy;
import com.smartcommerce.storefront.domain.CatalogQuery;
import com.smartcommerce.storefront.domain.PublicCategoryTree;
import com.smartcommerce.storefront.domain.PublicLocator;
import com.smartcommerce.storefront.domain.StorefrontException;
import com.smartcommerce.storefront.dto.response.PublicCatalogView;
import com.smartcommerce.storefront.dto.response.PublicPageView;
import com.smartcommerce.storefront.dto.response.PublicProductBySlugView;
import com.smartcommerce.storefront.dto.response.PublicProductDetailsView;
import com.smartcommerce.storefront.dto.response.PublicProductView;
import com.smartcommerce.storefront.dto.response.PublicStoreProfileView;
import com.smartcommerce.storefront.dto.response.PublicStoreView;
import com.smartcommerce.storefront.dto.response.PublicVariantView;
import com.smartcommerce.storefront.repositories.PublicCatalogRepository;
import com.smartcommerce.storefront.repositories.StorefrontRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The storefront catalog with search, filters, facets, sort and numbered pages (Sprint 8, SC-042,
 * ADR-0018 V6/V7). Read-only, in one repeatable-read snapshot so counts, facets and rows agree.
 */
@Service
public class PublicCatalogService {
  private static final CatalogQuery EVERYTHING =
      CatalogQuery.parse(null, null, null, null, null, null, null, null);

  private final StorefrontRepository stores;
  private final PublicCatalogRepository catalog;
  private final Clock clock;

  public PublicCatalogService(
      StorefrontRepository stores, PublicCatalogRepository catalog, Clock clock) {
    this.stores = stores;
    this.catalog = catalog;
    this.clock = clock;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PublicCatalogView catalog(String rawSlug, CatalogQuery query) {
    String slug = PublicLocator.slug(rawSlug);
    var store = stores.store(slug).orElseThrow(StorefrontException::new);
    PublicCategoryTree tree = tree(slug);
    // A hidden, unreachable or foreign category is the same 404 as one that does not exist.
    PublicCategoryTree.Node current =
        query.category() == null
            ? null
            : tree.bySlug(query.category()).orElseThrow(StorefrontException::new);
    Set<UUID> scope = current == null ? null : tree.subtree(current.id());

    long total = catalog.count(slug, query, scope);
    var cards =
        catalog.page(slug, query, scope, ReservationPolicy.now(clock)).stream()
            .map(PublicCatalogService::card)
            .toList();
    Map<UUID, Set<UUID>> filtered = catalog.categoryMembers(slug, query);
    Map<UUID, Set<UUID>> everything = catalog.categoryMembers(slug, EVERYTHING);

    return new PublicCatalogView(
        new PublicStoreView(store.name(), store.slug(), profile(store)),
        menu(tree, null, everything),
        current == null
            ? null
            : new PublicCatalogView.CategoryPath(
                current.name(),
                current.slug(),
                tree.path(current.id()).stream()
                    .map(node -> new PublicCatalogView.Link(node.name(), node.slug()))
                    .toList()),
        query.text() == null || query.search() == null ? null : query.text(),
        query.sort().parameter(),
        query.page(),
        CatalogQuery.PAGE_SIZE,
        total,
        cards,
        new PublicCatalogView.Facets(
            tree.children(current == null ? null : current.id()).stream()
                .map(
                    child ->
                        new PublicCatalogView.Count(
                            child.slug(), child.name(), tree.total(child.id(), filtered)))
                .filter(count -> count.count() > 0)
                .toList(),
            catalog.brandCounts(slug, query, scope).stream()
                .map(
                    brand -> new PublicCatalogView.Count(brand.key(), brand.label(), brand.count()))
                .toList(),
            prices(catalog.priceCounts(slug, query, scope)),
            options(catalog.optionCounts(slug, query, scope))),
        store.maxInstallments(),
        company(slug),
        pages(slug),
        catalog.banners(slug, clock.instant()).stream()
            .map(
                b ->
                    new PublicCatalogView.Banner(
                        b.id(), b.alt(), b.title(), b.linkKind(), b.linkTarget(), b.mobileImage()))
            .toList(),
        catalog.sections(slug).stream()
            .map(s -> new PublicCatalogView.Section(s.kind(), s.title(), s.categorySlug()))
            .toList());
  }

  /** The image of a banner the published store shows now (SC-043); any other is a 404. */
  @Transactional(readOnly = true)
  public ImageContent bannerImage(String rawSlug, UUID banner, boolean mobile) {
    return catalog
        .bannerImage(PublicLocator.slug(rawSlug), banner, mobile, clock.instant())
        .orElseThrow(StorefrontException::new);
  }

  /** An institutional page the published store wrote (SC-043); any other is a 404. */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PublicPageView page(String rawSlug, String pageSlug) {
    String slug = PublicLocator.slug(rawSlug);
    var store = stores.store(slug).orElseThrow(StorefrontException::new);
    StorePageKind kind;
    try {
      kind = StorePageKind.fromSlug(pageSlug);
    } catch (IllegalArgumentException unknown) {
      throw new StorefrontException();
    }
    String html = catalog.pages(slug).get(kind);
    if (html == null) throw new StorefrontException();
    return new PublicPageView(
        new PublicStoreView(store.name(), store.slug(), profile(store)),
        kind.title(),
        kind.slug(),
        ProductDescription.sanitize(html));
  }

  private PublicCatalogView.Company company(String slug) {
    return catalog
        .company(slug)
        .map(
            c ->
                new PublicCatalogView.Company(
                    c.announcement(),
                    c.legalName(),
                    c.cnpj(),
                    c.phone(),
                    c.address(),
                    c.facebook(),
                    c.tiktok(),
                    c.youtube()))
        .orElse(null);
  }

  /** Pages in footer order, only those the store wrote. */
  private List<PublicCatalogView.Link> pages(String slug) {
    var written = catalog.pages(slug);
    return java.util.Arrays.stream(StorePageKind.values())
        .filter(written::containsKey)
        .map(kind -> new PublicCatalogView.Link(kind.title(), kind.slug()))
        .toList();
  }

  /**
   * A published product with its storefront details (SC-042): address, brand, category path (only
   * through published categories), "compare at" prices above the price, and related products.
   */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PublicProductDetailsView product(String rawSlug, UUID product) {
    String slug = PublicLocator.slug(rawSlug);
    var store = stores.store(slug).orElseThrow(StorefrontException::new);
    var now = ReservationPolicy.now(clock);
    var found = stores.find(slug, product, now).orElseThrow(StorefrontException::new);
    var details = catalog.details(slug, product).orElseThrow(StorefrontException::new);
    Map<UUID, BigDecimal> compareAt = catalog.compareAt(slug, product);
    var variants =
        stores.variants(slug, product, now).stream()
            .map(
                v -> {
                  BigDecimal before = compareAt.get(v.id());
                  return new PublicVariantView(
                      v.id(),
                      money(v.price()),
                      v.currency(),
                      v.available(),
                      VariantValues.decode(v.optionsJson()),
                      before != null && before.compareTo(v.price()) > 0 ? money(before) : null);
                })
            .toList();
    // The product's "compare at" is the one of its cheapest variant, like the catalog card.
    PublicVariantView cheapest =
        variants.stream()
            .min(
                java.util.Comparator.comparing((PublicVariantView v) -> new BigDecimal(v.price()))
                    .thenComparing(v -> v.id()))
            .orElse(null);
    String before = cheapest == null ? null : cheapest.compareAtPrice();

    PublicCategoryTree tree = tree(slug);
    List<PublicCatalogView.Link> path =
        details.category() == null || !tree.reachable(details.category())
            ? List.of()
            : tree.path(details.category()).stream()
                .map(node -> new PublicCatalogView.Link(node.name(), node.slug()))
                .toList();
    return new PublicProductDetailsView(
        new PublicStoreView(store.name(), store.slug(), profile(store)),
        new PublicProductView(
            found.id(),
            found.name(),
            money(found.price()),
            found.currency(),
            found.available(),
            ProductDescription.sanitize(found.descriptionHtml()),
            found.kind(),
            variants,
            details.slug(),
            details.brand() == null
                ? null
                : new PublicCatalogView.Link(details.brand(), details.brandSlug()),
            path,
            before,
            before == null ? null : discountPercent(found.price(), new BigDecimal(before))),
        details.category() == null
            ? List.of()
            : catalog.related(slug, details.category(), product, now).stream()
                .map(PublicCatalogService::card)
                .toList(),
        store.maxInstallments());
  }

  /** The product with this address, or where an old address now points (SC-042, SC-041). */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PublicProductBySlugView productBySlug(String rawSlug, String productSlug) {
    String slug = PublicLocator.slug(rawSlug);
    if (productSlug == null
        || productSlug.length() > 80
        || !productSlug.matches("[a-z0-9]+(-[a-z0-9]+)*")) throw new StorefrontException();
    var id = catalog.productBySlug(slug, productSlug);
    if (id.isPresent()) return new PublicProductBySlugView(null, product(slug, id.get()));
    return catalog
        .movedTo(slug, productSlug, ReservationPolicy.now(clock))
        .map(moved -> new PublicProductBySlugView(moved, null))
        .orElseThrow(StorefrontException::new);
  }

  private PublicCategoryTree tree(String slug) {
    return new PublicCategoryTree(
        catalog.categories(slug).stream()
            .map(c -> new PublicCategoryTree.Node(c.id(), c.parent(), c.name(), c.slug()))
            .toList());
  }

  private static PublicStoreProfileView profile(
      com.smartcommerce.storefront.domain.PublicStore store) {
    return new PublicStoreProfileView(
        store.contactEmail(), store.whatsapp(), store.instagram(), store.logo());
  }

  /** Categories with at least one published product in their subtree, recursively. */
  private static List<PublicCatalogView.MenuItem> menu(
      PublicCategoryTree tree, UUID parent, Map<UUID, Set<UUID>> counts) {
    return tree.children(parent).stream()
        .filter(child -> tree.total(child.id(), counts) > 0)
        .map(
            child ->
                new PublicCatalogView.MenuItem(
                    child.name(), child.slug(), menu(tree, child.id(), counts)))
        .toList();
  }

  private static PublicCatalogView.Card card(PublicCatalogRepository.Card card) {
    boolean discounted = card.compareAt() != null && card.compareAt().compareTo(card.price()) > 0;
    return new PublicCatalogView.Card(
        card.id(),
        card.slug(),
        card.name(),
        card.brand(),
        money(card.price()),
        discounted ? money(card.compareAt()) : null,
        discounted ? discountPercent(card.price(), card.compareAt()) : null,
        card.currency(),
        card.available());
  }

  /** Rounded down: the storefront never promises more discount than there is (ADR-0018 V3). */
  static int discountPercent(BigDecimal price, BigDecimal compareAt) {
    return compareAt
        .subtract(price)
        .multiply(BigDecimal.valueOf(100))
        .divide(compareAt, 0, RoundingMode.DOWN)
        .intValueExact();
  }

  private static List<PublicCatalogView.PriceRange> prices(Map<Integer, Long> counts) {
    List<BigDecimal> edges = PublicCatalogRepository.PRICE_EDGES;
    List<PublicCatalogView.PriceRange> ranges = new ArrayList<>();
    counts.forEach(
        (bucket, count) -> {
          if (count == 0) return;
          BigDecimal min = bucket == 0 ? BigDecimal.ZERO : edges.get(bucket - 1);
          BigDecimal max = bucket < edges.size() ? edges.get(bucket) : null;
          ranges.add(
              new PublicCatalogView.PriceRange(money(min), max == null ? null : money(max), count));
        });
    return ranges;
  }

  private static List<PublicCatalogView.Option> options(
      List<PublicCatalogRepository.OptionCount> counts) {
    Map<String, List<PublicCatalogView.OptionValue>> byName = new LinkedHashMap<>();
    for (var count : counts)
      byName
          .computeIfAbsent(count.name(), name -> new ArrayList<>())
          .add(new PublicCatalogView.OptionValue(count.value(), count.count()));
    return byName.entrySet().stream()
        .map(entry -> new PublicCatalogView.Option(entry.getKey(), List.copyOf(entry.getValue())))
        .toList();
  }

  private static String money(BigDecimal value) {
    return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
  }
}
