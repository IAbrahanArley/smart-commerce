package com.smartcommerce.advisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartcommerce.advisor.domain.AdvisorException;
import com.smartcommerce.advisor.domain.AdvisorTool;
import com.smartcommerce.advisor.domain.AdvisorUsage;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdvisorToolTest {
  @Test
  void everyToolHasAStrictClosedSchemaAndNoStoreOrPersonArgument() {
    for (AdvisorTool tool : AdvisorTool.values()) {
      assertThat(tool.toolName()).matches("[a-z_]{3,40}");
      assertThat(tool.description()).isNotBlank().hasSizeLessThan(400);
      Map<String, Object> schema = tool.inputSchema();
      assertThat(schema)
          .containsEntry("type", "object")
          .containsEntry("additionalProperties", false);
      @SuppressWarnings("unchecked")
      Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
      assertThat(properties.keySet())
          .noneMatch(name -> name.matches("(?i).*(tenant|loja|store|user|usuario|pedido|order).*"));
      for (Object property : properties.values())
        assertThat(((Map<?, ?>) property).get("type")).isIn("string", "integer");
    }
    assertThat(AdvisorTool.named("estoque_produtos").inputSchema().get("required"))
        .isEqualTo(List.of("filtro"));
  }

  @Test
  void argumentsAreOnlyTheToolsOwnAndFromTheirClosedLists() throws Exception {
    Method validated =
        com.smartcommerce.advisor.services.AdvisorToolService.class.getDeclaredMethod(
            "validated", AdvisorTool.class, Map.class);
    validated.setAccessible(true);
    AdvisorTool report = AdvisorTool.RELATORIO_PERIODO;
    AdvisorTool stock = AdvisorTool.ESTOQUE_PRODUTOS;

    assertThat(validated.invoke(null, report, Map.of("periodo", "THIS_MONTH"))).isNotNull();
    assertThat(validated.invoke(null, stock, Map.of("filtro", "SEM_GIRO", "limite", 20)))
        .isNotNull();
    assertThat(validated.invoke(null, AdvisorTool.ALERTAS, Map.of())).isNotNull();

    List<Map.Entry<AdvisorTool, Map<String, Object>>> invalid =
        List.of(
            Map.entry(report, args()),
            Map.entry(report, args("periodo", "FOREVER")),
            Map.entry(report, args("periodo", 7)),
            Map.entry(report, args("periodo", "THIS_MONTH", "tenant", "00000000-0000")),
            Map.entry(stock, args("filtro", "TUDO")),
            Map.entry(stock, args("filtro", "SEM_GIRO", "limite", 21)),
            Map.entry(stock, args("filtro", "SEM_GIRO", "limite", 0)),
            Map.entry(stock, args("filtro", "SEM_GIRO", "limite", "10")),
            Map.entry(AdvisorTool.ALERTAS, args("periodo", "TODAY")));
    for (var call : invalid)
      assertThatThrownBy(() -> validated.invoke(null, call.getKey(), call.getValue()))
          .as(call.toString())
          .satisfies(
              thrown ->
                  assertThat(thrown.getCause())
                      .isInstanceOf(AdvisorException.class)
                      .hasFieldOrPropertyWithValue(
                          "reason", AdvisorException.Reason.INVALID_TOOL_CALL));

    assertThatThrownBy(() -> AdvisorTool.named("apagar_pedido"))
        .isInstanceOf(AdvisorException.class)
        .hasFieldOrPropertyWithValue("reason", AdvisorException.Reason.INVALID_TOOL_CALL);
  }

  private static Map<String, Object> args(Object... pairs) {
    Map<String, Object> map = new HashMap<>();
    for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
    return map;
  }

  @Test
  void usageRecordsRejectUnknownOutcomesAndNegativeFigures() {
    assertThat(
            new AdvisorUsage("ANSWERED", "claude-opus-5-5", 1, 0, 1, 2, "alertas", BigDecimal.ONE))
        .isNotNull();
    assertThatThrownBy(() -> new AdvisorUsage("PENDING", null, 0, 0, 0, 0, null, BigDecimal.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AdvisorUsage("ANSWERED", null, -1, 0, 0, 0, null, BigDecimal.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new AdvisorUsage("ANSWERED", null, 0, 0, 0, 0, null, new BigDecimal("-0.01")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
