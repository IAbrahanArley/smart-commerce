package com.smartcommerce.advisor.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The assistant's read-only tools (SC-025, ADR-0016 A3). Each has a Portuguese description and a
 * strict JSON schema: only the listed arguments, each from a closed list, nothing else. No tool
 * takes a store, user or order: the store is always the session's.
 */
public enum AdvisorTool {
  RELATORIO_PERIODO(
      "relatorio_periodo",
      "Relatório financeiro da loja no período: receita bruta, reembolsos, taxas, CMV, despesas,"
          + " margem de contribuição, pedidos pagos, ticket médio, a receber e caixa. Se"
          + " complete=false, os números são parciais e 'missing' diz o que falta.",
      Map.of("periodo", Arg.PERIOD)),
  INDICADORES_DASHBOARD(
      "indicadores_dashboard",
      "Indicadores do painel da loja no período: pedidos pagos, receita, ticket médio, margem"
          + " estimada, vendas por dia e quantidade de itens com estoque baixo e sem giro.",
      Map.of("periodo", Arg.PERIOD)),
  ALERTAS(
      "alertas",
      "Alertas atuais da loja (regras fixas): pagamentos, margem, custos e taxas faltando,"
          + " pedidos perto de expirar, estoque baixo e itens sem venda.",
      Map.of()),
  ESTOQUE_PRODUTOS(
      "estoque_produtos",
      "Itens da loja com estoque baixo (5 unidades ou menos) ou sem venda nos últimos 30 dias,"
          + " com nome, variação, quantidade disponível e data da última venda.",
      Map.of("filtro", Arg.STOCK_FILTER, "limite", Arg.LIMIT)),
  RESUMO_PEDIDOS(
      "resumo_pedidos",
      "Quantidade de pedidos criados no período por situação (aguardando pagamento, pago,"
          + " expirado, cancelado, reembolsado). Sem dados de compradores.",
      Map.of("periodo", Arg.PERIOD));

  /** Argument kinds: each value comes from a closed list or a small range. */
  public enum Arg {
    PERIOD(List.of("TODAY", "LAST_7_DAYS", "LAST_30_DAYS", "THIS_MONTH", "LAST_MONTH"), true),
    STOCK_FILTER(List.of("ESTOQUE_BAIXO", "SEM_GIRO"), true),
    LIMIT(List.of(), false);

    final List<String> values;
    final boolean required;

    Arg(List<String> values, boolean required) {
      this.values = values;
      this.required = required;
    }

    public List<String> allowed() {
      return values;
    }

    public boolean required() {
      return required;
    }
  }

  public static final int MAX_LIMIT = 20;
  public static final int DEFAULT_LIMIT = 10;

  private final String toolName;
  private final String description;
  private final Map<String, Arg> args;

  AdvisorTool(String toolName, String description, Map<String, Arg> args) {
    this.toolName = toolName;
    this.description = description;
    this.args = args;
  }

  public String toolName() {
    return toolName;
  }

  public String description() {
    return description;
  }

  public Map<String, Arg> args() {
    return args;
  }

  public static AdvisorTool named(String name) {
    for (AdvisorTool tool : values()) if (tool.toolName.equals(name)) return tool;
    throw new AdvisorException(AdvisorException.Reason.INVALID_TOOL_CALL);
  }

  /** JSON schema for strict tool use: closed object, enums, no extra properties. */
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    List<String> required = new ArrayList<>();
    args.keySet().stream()
        .sorted()
        .forEach(
            name -> {
              Arg arg = args.get(name);
              if (arg == Arg.LIMIT)
                properties.put(name, Map.of("type", "integer", "minimum", 1, "maximum", MAX_LIMIT));
              else properties.put(name, Map.of("type", "string", "enum", arg.values));
              if (arg.required) required.add(name);
            });
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("properties", properties);
    schema.put("required", required);
    schema.put("additionalProperties", false);
    return schema;
  }
}
