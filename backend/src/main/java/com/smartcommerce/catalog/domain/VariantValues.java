package com.smartcommerce.catalog.domain;

import java.util.*;
import tools.jackson.databind.json.JsonMapper;

public record VariantValues(ProductValues values, List<VariantOption> options) {
  private static final JsonMapper JSON = JsonMapper.builder().build();

  public VariantValues {
    if (values == null
        || options == null
        || options.size() > 3
        || options.stream().anyMatch(Objects::isNull))
      throw new IllegalArgumentException("Invalid options");
    options = options.stream().sorted(Comparator.comparing(VariantOption::name)).toList();
    if (options.stream().map(VariantOption::name).distinct().count() != options.size())
      throw new IllegalArgumentException("Duplicate option");
  }

  public String key() {
    return options.stream()
        .map(o -> o.name().length() + ":" + o.name() + o.value().length() + ":" + o.value())
        .reduce("", String::concat);
  }

  public String json() {
    return JSON.writeValueAsString(options);
  }

  public static List<VariantOption> decode(String text) {
    var node = JSON.readTree(text);
    List<VariantOption> result = new ArrayList<>();
    for (var entry : node)
      result.add(new VariantOption(entry.get("name").asString(), entry.get("value").asString()));
    return List.copyOf(result);
  }

  public List<String> names() {
    return options.stream().map(VariantOption::name).toList();
  }
}
