package com.smartcommerce.orders.domain;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a counter sales spreadsheet saved as CSV (SC-054, ADR-0019 C14). Pure: no catalog, stock or
 * database; the service checks SKUs, stock and duplicates afterwards.
 *
 * <ul>
 *   <li>At most 1 MB and 500 item lines. UTF-8 (with or without BOM) or, when the bytes are not
 *       UTF-8, Windows-1252 (Excel's "CSV" in Portuguese). Separator ";" or ",", from the header.
 *   <li>Columns by name, in any order: data, sku, quantidade, preco_unitario, forma_pagamento and,
 *       optionally, codigo_venda and desconto. Accents, case and spaces in names are ignored.
 *   <li>One line per item. Lines with the same date and sale code are one sale (same payment); a
 *       line without a code is a sale on its own. If any line of a sale cannot be read, the whole
 *       sale waits for the corrected file.
 *   <li>A row with more filled cells than the header is refused (a decimal comma that split a
 *       price); rows of separators only are skipped.
 *   <li>A cell starting with =, +, -, @, tab or carriage return is refused (spreadsheet formula
 *       injection), and nothing of the file is ever echoed beyond the line number and the column.
 *   <li>Each sale gets an import key from its content (date, code, payment and items) plus the
 *       occurrence of that same content in the file, so the same or a corrected file never imports
 *       a sale twice.
 * </ul>
 */
public final class CounterImportParser {
  public static final int MAX_BYTES = 1_048_576;
  public static final int MAX_LINES = 500;
  private static final List<String> REQUIRED =
      List.of("data", "sku", "quantidade", "preco_unitario", "forma_pagamento");

  private CounterImportParser() {}

  public record Item(
      int line, String sku, int quantity, BigDecimal unitPrice, BigDecimal discount) {}

  /** One sale of the file: its lines, date, optional code, payment method and items. */
  public record Sale(
      List<Integer> lines,
      LocalDate date,
      String code,
      String method,
      List<Item> items,
      String importKey) {}

  /** A problem of one line (0 for the whole file), with a message in Portuguese. */
  public record Problem(int line, String message) {}

  public record Result(List<Sale> sales, List<Problem> problems, int itemLines) {
    public boolean fileRefused() {
      return problems.stream().anyMatch(problem -> problem.line() == 0);
    }
  }

  public static Result parse(byte[] bytes, LocalDate today) {
    if (bytes == null || bytes.length == 0) return refused("O arquivo está vazio.");
    if (bytes.length > MAX_BYTES) return refused("O arquivo passa de 1 MB.");
    String text = decode(bytes);
    String[] rows = text.split("\r\n|\n|\r", -1);
    int headerIndex = 0;
    while (headerIndex < rows.length && rows[headerIndex].isBlank()) headerIndex++;
    if (headerIndex == rows.length) return refused("O arquivo está vazio.");
    String header = rows[headerIndex];
    char separator = header.indexOf(';') >= 0 ? ';' : ',';
    List<String> names = split(header, separator);
    if (names == null) return refused("Não foi possível ler o cabeçalho.");
    Map<String, Integer> columns = new HashMap<>();
    for (int index = 0; index < names.size(); index++)
      columns.putIfAbsent(column(names.get(index)), index);
    List<String> missing = REQUIRED.stream().filter(name -> !columns.containsKey(name)).toList();
    if (!missing.isEmpty())
      return refused("Faltam colunas no cabeçalho: " + String.join(", ", missing) + ".");

    List<Problem> problems = new ArrayList<>();
    Map<String, List<Row>> groups = new LinkedHashMap<>();
    // Sales (date and code) with a line that could not be read: the whole sale waits, so it is
    // never written without an item and then written again, whole, from the corrected file.
    Set<String> broken = new HashSet<>();
    int itemLines = 0;
    for (int index = headerIndex + 1; index < rows.length; index++) {
      if (rows[index].isBlank()) continue;
      List<String> cells = split(rows[index], separator);
      // Excel exports trailing rows of separators only (";;;;;;"): nothing to read there.
      if (cells != null && cells.stream().allMatch(String::isBlank)) continue;
      int line = index + 1;
      if (++itemLines > MAX_LINES)
        return refused("O arquivo passa de " + MAX_LINES + " linhas de itens.");
      if (cells == null) {
        problems.add(new Problem(line, "Linha mal formada (aspas sem fechar)."));
        continue;
      }
      if (cells.subList(Math.min(names.size(), cells.size()), cells.size()).stream()
          .anyMatch(cell -> !cell.isBlank())) {
        // A decimal comma in a comma-separated file splits the price: never read it as less.
        problems.add(
            new Problem(
                line,
                "A linha tem mais colunas que o cabeçalho. Com vírgula como separador, use ponto"
                    + " nos valores ou ponha-os entre aspas."));
        String code = cell(cells, columns, "codigo_venda");
        if (code != null) broken.add(group(cell(cells, columns, "data"), code));
        continue;
      }
      Row row = row(line, cells, columns, separator, today, problems);
      String code = cell(cells, columns, "codigo_venda");
      String group = code == null ? "linha:" + line : group(cell(cells, columns, "data"), code);
      if (row == null) {
        if (code != null) broken.add(group);
        continue;
      }
      groups.computeIfAbsent(group, key -> new ArrayList<>()).add(row);
    }
    if (itemLines == 0) problems.add(new Problem(0, "O arquivo não tem linhas de itens."));

    List<Sale> sales = new ArrayList<>();
    Map<String, Integer> occurrences = new HashMap<>();
    for (var entry : groups.entrySet()) {
      var rowsOfSale = entry.getValue();
      Row first = rowsOfSale.get(0);
      if (broken.contains(entry.getKey())) {
        for (Row row : rowsOfSale)
          problems.add(
              new Problem(
                  row.line(),
                  "A venda "
                      + first.code()
                      + " tem outra linha com erro: corrija-a para importar"
                      + " a venda inteira."));
        continue;
      }
      Set<String> skus = new HashSet<>();
      boolean consistent = true;
      for (Row row : rowsOfSale) {
        if (!row.method().equals(first.method())) {
          problems.add(
              new Problem(
                  row.line(), "A venda " + first.code() + " tem formas de pagamento diferentes."));
          consistent = false;
        }
        if (!skus.add(row.sku())) {
          problems.add(
              new Problem(row.line(), "SKU repetido na mesma venda: some as quantidades."));
          consistent = false;
        }
      }
      if (!consistent) continue;
      List<Item> items =
          rowsOfSale.stream()
              .map(
                  row ->
                      new Item(row.line(), row.sku(), row.quantity(), row.price(), row.discount()))
              .toList();
      String content = content(first, items);
      int occurrence = occurrences.merge(content, 1, Integer::sum);
      sales.add(
          new Sale(
              rowsOfSale.stream().map(Row::line).toList(),
              first.date(),
              first.code(),
              first.method(),
              items,
              digest(content + "#" + occurrence)));
    }
    return new Result(sales, problems, itemLines);
  }

  /** A sale code counts within its day: stores often restart their numbering every day. */
  private static String group(String date, String code) {
    return "codigo:" + (date == null ? "" : date) + "|" + code;
  }

  private record Row(
      int line,
      LocalDate date,
      String code,
      String sku,
      int quantity,
      BigDecimal price,
      BigDecimal discount,
      String method) {}

  private static Row row(
      int line,
      List<String> cells,
      Map<String, Integer> columns,
      char separator,
      LocalDate today,
      List<Problem> problems) {
    int before = problems.size();
    for (String cell : cells)
      if (!cell.isEmpty() && "=+-@\t\r".indexOf(cell.charAt(0)) >= 0) {
        problems.add(new Problem(line, "Célula começando com =, +, -, @ não é aceita."));
        return null;
      }
    String dateText = cell(cells, columns, "data");
    LocalDate date = date(dateText);
    if (date == null || date.isBefore(LocalDate.of(2000, 1, 1)) || date.isAfter(today))
      problems.add(new Problem(line, "Data inválida: use dd/mm/aaaa, desde 2000 até hoje."));
    String code = cell(cells, columns, "codigo_venda");
    if (code != null && (code.length() > 40 || !code.matches("[\\p{L}\\p{N}._/ -]+")))
      problems.add(new Problem(line, "Código da venda inválido: até 40 letras ou números."));
    String sku = cell(cells, columns, "sku");
    if (sku == null || !sku.matches("[A-Za-z0-9._/-]{1,64}"))
      problems.add(new Problem(line, "SKU inválido."));
    String quantityText = cell(cells, columns, "quantidade");
    int quantity = 0;
    if (quantityText == null
        || !quantityText.matches("[0-9]{1,4}")
        || (quantity = Integer.parseInt(quantityText)) < 1)
      problems.add(new Problem(line, "Quantidade inválida: um número inteiro de 1 a 9999."));
    BigDecimal price = money(cell(cells, columns, "preco_unitario"), separator);
    if (price == null || price.signum() <= 0)
      problems.add(new Problem(line, "Preço unitário inválido: maior que zero, até 2 casas."));
    String discountText = cell(cells, columns, "desconto");
    BigDecimal discount =
        discountText == null ? BigDecimal.ZERO.setScale(2) : money(discountText, separator);
    if (discount == null)
      problems.add(new Problem(line, "Desconto inválido: até 2 casas decimais, ou vazio."));
    String method = method(cell(cells, columns, "forma_pagamento"));
    if (method == null)
      problems.add(
          new Problem(line, "Forma de pagamento inválida: dinheiro, pix, débito ou crédito."));
    else if (method.isEmpty())
      problems.add(new Problem(line, "Crediário não é aceito na importação."));
    if (problems.size() > before) return null;
    BigDecimal total = price.multiply(BigDecimal.valueOf(quantity));
    if (total.compareTo(new BigDecimal("1000000000000000")) >= 0
        || discount.compareTo(total) >= 0) {
      problems.add(new Problem(line, "O desconto precisa ser menor que o total do item."));
      return null;
    }
    return new Row(line, date, code, sku, quantity, price, discount, method);
  }

  private static String cell(List<String> cells, Map<String, Integer> columns, String name) {
    Integer index = columns.get(name);
    if (index == null || index >= cells.size()) return null;
    String value = cells.get(index).strip();
    return value.isEmpty() ? null : value;
  }

  /** dd/mm/aaaa (what Excel shows in Portuguese) or aaaa-mm-dd. */
  static LocalDate date(String value) {
    if (value == null) return null;
    try {
      if (value.matches("[0-9]{1,2}/[0-9]{1,2}/[0-9]{4}")) {
        String[] parts = value.split("/");
        return LocalDate.of(
            Integer.parseInt(parts[2]), Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
      }
      if (value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return LocalDate.parse(value);
    } catch (java.time.DateTimeException invalid) {
      return null;
    }
    return null;
  }

  /**
   * Money as typed: "R$ 1.234,50", "1234,50", "1234.50" or "1,234.50". With "," as the column
   * separator, a decimal comma only survives inside a quoted cell.
   */
  static BigDecimal money(String value, char separator) {
    if (value == null) return null;
    String text = value.replace("R$", "").replace(" ", "").replace(" ", "");
    String normalized;
    if (text.matches("[0-9]{1,3}(\\.[0-9]{3})+(,[0-9]{1,2})?"))
      normalized = text.replace(".", "").replace(",", ".");
    else if (text.matches("[0-9]+,[0-9]{1,2}")) normalized = text.replace(",", ".");
    // "1,234.50" only in comma-separated files; with ";" a comma is always the decimal one.
    else if (separator == ',' && text.matches("[0-9]{1,3}(,[0-9]{3})+(\\.[0-9]{1,2})?"))
      normalized = text.replace(",", "");
    else normalized = text;
    if (!normalized.matches("[0-9]{1,15}(\\.[0-9]{1,2})?")) return null;
    return new BigDecimal(normalized).setScale(2);
  }

  /** CASH, PIX, DEBIT or CREDIT; "" for store credit (refused here); null when unknown. */
  static String method(String value) {
    if (value == null) return null;
    String text = normalize(value).replace("cartao_de_", "").replace("cartao_", "");
    return switch (text) {
      case "dinheiro", "especie" -> "CASH";
      case "pix" -> "PIX";
      case "debito" -> "DEBIT";
      case "credito" -> "CREDIT";
      case "crediario", "fiado" -> "";
      default -> null;
    };
  }

  /**
   * The column a header names, accepting the usual ways of writing it: "Forma de pagamento",
   * "Código da venda", "Preço", "Qtd".
   */
  static String column(String header) {
    String name = normalize(header).replaceAll("_(de|da|do)_", "_");
    return switch (name) {
      case "forma", "pagamento" -> "forma_pagamento";
      case "codigo", "venda", "codigo_venda" -> "codigo_venda";
      case "preco", "valor", "valor_unitario", "preco_unitario" -> "preco_unitario";
      case "qtd", "qtde", "quant", "quantidade" -> "quantidade";
      case "data_venda", "data" -> "data";
      default -> name;
    };
  }

  /** Lowercase, no accents, words joined by "_": "Preço unitário" is "preco_unitario". */
  static String normalize(String value) {
    return Normalizer.normalize(value.strip(), Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(java.util.Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "_")
        .replaceAll("^_|_$", "");
  }

  /** Splits one CSV line; quotes may wrap a cell and "" is a quote. Null for an open quote. */
  static List<String> split(String line, char separator) {
    List<String> cells = new ArrayList<>();
    StringBuilder cell = new StringBuilder();
    boolean quoted = false;
    for (int index = 0; index < line.length(); index++) {
      char current = line.charAt(index);
      if (quoted) {
        if (current == '"' && index + 1 < line.length() && line.charAt(index + 1) == '"') {
          cell.append('"');
          index++;
        } else if (current == '"') quoted = false;
        else cell.append(current);
      } else if (current == '"' && cell.toString().isBlank()) {
        cell.setLength(0);
        quoted = true;
      } else if (current == separator) {
        cells.add(cell.toString());
        cell.setLength(0);
      } else cell.append(current);
    }
    if (quoted) return null;
    cells.add(cell.toString());
    return cells;
  }

  private static String decode(byte[] bytes) {
    int start =
        bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF
            ? 3
            : 0;
    ByteBuffer buffer = ByteBuffer.wrap(bytes, start, bytes.length - start);
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(buffer)
          .toString();
    } catch (CharacterCodingException notUtf8) {
      return new String(bytes, start, bytes.length - start, Charset.forName("windows-1252"));
    }
  }

  private static String content(Row first, List<Item> items) {
    StringBuilder content =
        new StringBuilder()
            .append(first.date())
            .append('|')
            .append(first.code() == null ? "" : first.code())
            .append('|')
            .append(first.method());
    items.stream()
        .map(
            item ->
                item.sku()
                    + ":"
                    + item.quantity()
                    + ":"
                    + item.unitPrice().toPlainString()
                    + ":"
                    + item.discount().toPlainString())
        .sorted()
        .forEach(item -> content.append('|').append(item));
    return content.toString();
  }

  private static String digest(String content) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(content.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static Result refused(String message) {
    return new Result(List.of(), List.of(new Problem(0, message)), 0);
  }
}
