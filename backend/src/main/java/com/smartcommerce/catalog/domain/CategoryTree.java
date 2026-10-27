package com.smartcommerce.catalog.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The shape of a store's category tree, read while every category row is locked (Sprint 8, SC-041).
 * Pure: it answers depth, cycle and placement questions and lists categories depth-first.
 */
public final class CategoryTree {
  public static final int MAX_DEPTH = 3;

  /** One category as stored: parent ({@code null} for a root) and position among siblings. */
  public record Node(UUID id, UUID parent, int position, String nameKey) {}

  private final Map<UUID, Node> nodes = new HashMap<>();

  public CategoryTree(List<Node> nodes) {
    for (Node node : nodes) this.nodes.put(node.id(), node);
  }

  public boolean contains(UUID id) {
    return nodes.containsKey(id);
  }

  public int size() {
    return nodes.size();
  }

  /** The parent of a category in the tree; {@code null} for a root. */
  public UUID parent(UUID id) {
    return nodes.get(id).parent();
  }

  /** 1 for a root. */
  public int depth(UUID id) {
    int depth = 0;
    for (UUID at = id; at != null && depth <= nodes.size(); at = nodes.get(at).parent()) depth++;
    return depth;
  }

  /** Levels of the subtree rooted at {@code id}, itself included (1 for a leaf). */
  public int height(UUID id) {
    int height = 1;
    for (Node child : children(id)) height = Math.max(height, 1 + height(child.id()));
    return height;
  }

  /** True when {@code candidate} is {@code id} itself or lies below it. */
  public boolean isWithin(UUID candidate, UUID id) {
    for (UUID at = candidate; at != null; at = nodes.get(at).parent())
      if (at.equals(id)) return true;
    return false;
  }

  public boolean hasChildren(UUID id) {
    return !children(id).isEmpty();
  }

  /** Children of {@code parent} ({@code null}: the roots) in display order. */
  public List<Node> children(UUID parent) {
    return nodes.values().stream()
        .filter(node -> Objects.equals(node.parent(), parent))
        .sorted(
            Comparator.comparingInt(Node::position)
                .thenComparing(Node::nameKey)
                .thenComparing(Node::id))
        .toList();
  }

  /**
   * Siblings under {@code parent} once {@code id} is placed at {@code position} (clamped) among
   * them; the list index is each sibling's new position.
   */
  public List<UUID> placed(UUID id, UUID parent, int position) {
    List<UUID> order = new ArrayList<>();
    for (Node sibling : children(parent)) if (!sibling.id().equals(id)) order.add(sibling.id());
    order.add(Math.max(0, Math.min(position, order.size())), id);
    return order;
  }

  /** Every category id, parents before their children, siblings in display order. */
  public List<UUID> depthFirst() {
    List<UUID> order = new ArrayList<>();
    visit(null, order);
    return order;
  }

  private void visit(UUID parent, List<UUID> order) {
    for (Node child : children(parent)) {
      order.add(child.id());
      visit(child.id(), order);
    }
  }
}
