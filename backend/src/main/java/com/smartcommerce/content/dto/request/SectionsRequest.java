package com.smartcommerce.content.dto.request;

import com.smartcommerce.content.domain.HomeSection;
import java.util.List;
import java.util.UUID;

/** The home sections, in order; replaces the current ones. */
public record SectionsRequest(List<Section> sections) {
  public record Section(String kind, String title, UUID categoryId) {}

  public List<HomeSection> list() {
    if (sections == null || sections.size() > HomeSection.MAX)
      throw new IllegalArgumentException("Invalid sections");
    return sections.stream()
        .map(
            section -> {
              if (section == null) throw new IllegalArgumentException("Invalid section");
              return HomeSection.of(section.kind(), section.title(), section.categoryId());
            })
        .toList();
  }
}
