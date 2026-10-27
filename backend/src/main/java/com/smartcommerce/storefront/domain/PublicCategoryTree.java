package com.smartcommerce.storefront.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The published category tree of a store as the buyer sees it (Sprint 8, SC-042). Pure. A category
 * is reachable only when every ancestor is published too: hiding a category also hides its
 * subcategories from the menu and from navigation by slug.
 */
public final class PublicCategoryTree {
  /** A published category, in menu order. */
  public record Node(UUID id, UUID parent, String name, String slug) {}

  private final List<Node> nodes;
  private final Map<UUID, Node> byId = new HashMap<>();

  /** {@code published} lists only published categories, already in menu order. */
  public PublicCategoryTree(List<Node> published) {
    for (Node node : published) byId.put(node.id(), node);
    List<Node> reachable = new ArrayList<>();
    for (Node node : published) if (reachable(node)) reachable.add(node);
    this.nodes = List.copyOf(reachable);
  }

  private boolean reachable(Node node) {
    int guard = 0;
    for (Node at = node; at != null && guard++ <= byId.size(); ) {
      if (at.parent() == null) return true;
      at = byId.get(at.parent());
    }
    return false;
  }

  /** True for a published category whose ancestors are all published. */
  public boolean reachable(UUID id) {
    return nodes.stream().anyMatch(node -> node.id().equals(id));
  }

  public Optional<Node> bySlug(String slug) {
    return nodes.stream().filter(node -> node.slug().equals(slug)).findFirst();
  }

  public List<Node> children(UUID parent) {
    return nodes.stream().filter(node -> Objects.equals(node.parent(), parent)).toList();
  }

  /** The category and every reachable category below it. */
  public Set<UUID> subtree(UUID id) {
    Set<UUID> ids = new LinkedHashSet<>();
    ids.add(id);
    for (Node child : children(id)) ids.addAll(subtree(child.id()));
    return ids;
  }

  /** Root first, the category itself last. */
  public List<Node> path(UUID id) {
    List<Node> path = new ArrayList<>();
    for (Node at = byId.get(id);
        at != null;
        at = at.parent() == null ? null : byId.get(at.parent())) path.addFirst(at);
    return path;
  }

  /** Products of the category and its subcategories, from counts per category. */
  public long total(UUID id, Map<UUID, Long> counts) {
    return subtree(id).stream().mapToLong(each -> counts.getOrDefault(each, 0L)).sum();
  }
}
