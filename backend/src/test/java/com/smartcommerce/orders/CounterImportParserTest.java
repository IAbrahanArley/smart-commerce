package com.smartcommerce.orders;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartcommerce.orders.domain.CounterImportParser;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** SC-054: the counter sales spreadsheet reader (ADR-0019 C14), without Spring or a database. */
class CounterImportParserTest {
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);

  private static CounterImportParser.Result parse(String text) {
    return CounterImportParser.parse(text.getBytes(StandardCharsets.UTF_8), TODAY);
  }

  @Test
  void readsExcelInPortugueseWithBomSemicolonsAndDecimalCommas() {
    byte[] body =
        ("Data;Código venda;SKU;Quantidade;Preço unitário;Desconto;Forma de pagamento\r\n"
                + "02/11/2026;V1;CAM-1;2;\"1.234,50\";10,00;Cartão de crédito\r\n"
                + "02/11/2026;V1;JEA-1;1;149,00;;Crédito\r\n"
                + "\r\n"
                + "03/11/2026;;CAM-1;1;59,90;;Dinheiro\r\n")
            .getBytes(Charset.forName("windows-1252"));
    var result = CounterImportParser.parse(body, TODAY);
    assertThat(result.problems()).isEmpty();
    assertThat(result.itemLines()).isEqualTo(3);
    assertThat(result.sales()).hasSize(2);
    var grouped = result.sales().get(0);
    assertThat(grouped.lines()).containsExactly(2, 3);
    assertThat(grouped.method()).isEqualTo("CREDIT");
    assertThat(grouped.date()).isEqualTo(LocalDate.of(2026, 11, 2));
    assertThat(grouped.items().get(0).unitPrice()).isEqualByComparingTo("1234.50");
    assertThat(grouped.items().get(0).discount()).isEqualByComparingTo("10.00");
    assertThat(result.sales().get(1).method()).isEqualTo("CASH");
    assertThat(result.sales().get(1).code()).isNull();

    // The same content in UTF-8 with BOM and commas reads the same, with the same import keys.
    String utf =
        "data,codigo_venda,sku,quantidade,preco_unitario,desconto,forma_pagamento\n"
            + "2026-11-02,V1,CAM-1,2,1234.50,10.00,credito\n"
            + "2026-11-02,V1,JEA-1,1,149.00,,credito\n"
            + "2026-11-03,,CAM-1,1,59.90,,dinheiro\n";
    byte[] utfBytes = utf.getBytes(StandardCharsets.UTF_8);
    byte[] withBom = new byte[utfBytes.length + 3];
    withBom[0] = (byte) 0xEF;
    withBom[1] = (byte) 0xBB;
    withBom[2] = (byte) 0xBF;
    System.arraycopy(utfBytes, 0, withBom, 3, utfBytes.length);
    var again = CounterImportParser.parse(withBom, TODAY);
    assertThat(again.problems()).isEmpty();
    assertThat(again.sales())
        .extracting(CounterImportParser.Sale::importKey)
        .isEqualTo(result.sales().stream().map(CounterImportParser.Sale::importKey).toList());
  }

  @Test
  void identicalSalesWithoutCodeAreDistinctButStableAcrossFiles() {
    String line = "03/11/2026;;CAM-1;1;59,90;;pix\n";
    String header = "data;codigo_venda;sku;quantidade;preco_unitario;desconto;forma_pagamento\n";
    var two = parse(header + line + line);
    assertThat(two.sales()).hasSize(2);
    assertThat(two.sales().get(0).importKey()).isNotEqualTo(two.sales().get(1).importKey());
    // A corrected file with a line added before keeps the keys of the identical sales.
    var shifted = parse(header + "03/11/2026;;JEA-1;1;149,00;;pix\n" + line + line);
    assertThat(shifted.sales().subList(1, 3))
        .extracting(CounterImportParser.Sale::importKey)
        .isEqualTo(two.sales().stream().map(CounterImportParser.Sale::importKey).toList());
  }

  @Test
  void reportsEachBadLineAndRefusesFormulasStoreCreditAndInconsistentSales() {
    var result =
        parse(
            "data;codigo_venda;sku;quantidade;preco_unitario;desconto;forma_pagamento\n"
                + "04/11/2026;;CAM-1;1;10,00;;pix\n" // future date
                + "31/02/2026;;CAM-1;1;10,00;;pix\n" // no such day
                + "03/11/2026;;=HYPERLINK(1);1;10,00;;pix\n" // formula
                + "03/11/2026;;CAM-1;0;10,00;;pix\n" // zero units
                + "03/11/2026;;CAM-1;1;10,001;;pix\n" // three decimals
                + "03/11/2026;;CAM-1;1;10,00;10,00;pix\n" // discount = total
                + "03/11/2026;;CAM-1;1;10,00;;fiado\n" // store credit
                + "03/11/2026;;CAM-1;1;10,00;;boleto\n" // unknown method
                + "03/11/2026;V9;CAM-1;1;10,00;;pix\n"
                + "03/11/2026;V9;JEA-1;1;10,00;;dinheiro\n" // same sale, other payment
                + "03/11/2026;V8;CAM-1;1;10,00;;pix\n"
                + "03/11/2026;V8;CAM-1;2;10,00;;pix\n" // same SKU twice in a sale
                + "03/11/2026;;CAM-1;1;10,00;;pix\n"); // the only good one
    assertThat(result.fileRefused()).isFalse();
    assertThat(result.problems())
        .extracting(CounterImportParser.Problem::line)
        .contains(2, 3, 4, 5, 6, 7, 8, 9, 11, 13);
    assertThat(
            result.problems().stream()
                .filter(p -> p.line() == 8)
                .findFirst()
                .orElseThrow()
                .message())
        .contains("Crediário");
    assertThat(result.sales()).hasSize(1);
    assertThat(result.sales().get(0).lines()).containsExactly(14);
  }

  @Test
  void aSaleWithABadLineWaitsWholeAndCodesCountWithinTheirDay() {
    String header = "data;codigo_venda;sku;quantidade;preco_unitario;desconto;forma_pagamento\n";
    var result =
        parse(
            header
                + "03/11/2026;V1;CAM-1;2;10,00;;pix\n"
                + "03/11/2026;V1;JEA-1;1;abc;;pix\n" // bad price: V1 waits whole
                + "02/11/2026;1;CAM-1;1;10,00;;pix\n"
                + "03/11/2026;1;CAM-1;1;10,00;;pix\n" // code 1 again, another day
                + ";;;;;;\n"); // Excel's trailing separators
    assertThat(result.sales()).extracting(CounterImportParser.Sale::code).containsExactly("1", "1");
    assertThat(result.problems()).extracting(CounterImportParser.Problem::line).contains(2, 3);
    assertThat(result.itemLines()).isEqualTo(4);
    // A decimal comma split by the "," separator is never read as a smaller price.
    var split =
        parse("forma_pagamento,data,sku,quantidade,preco_unitario\npix,03/11/2026,CAM-1,1,59,90\n");
    assertThat(split.sales()).isEmpty();
    assertThat(split.problems().get(0).message()).contains("mais colunas");
  }

  @Test
  void refusesWholeFilesThatAreEmptyHugeOrWithoutTheColumns() {
    assertThat(CounterImportParser.parse(new byte[0], TODAY).fileRefused()).isTrue();
    assertThat(
            CounterImportParser.parse(new byte[CounterImportParser.MAX_BYTES + 1], TODAY)
                .fileRefused())
        .isTrue();
    assertThat(parse("data;sku;quantidade\n03/11/2026;CAM-1;1\n").problems().get(0).message())
        .contains("preco_unitario")
        .contains("forma_pagamento");
    assertThat(parse("data;sku;quantidade;preco_unitario;forma_pagamento\n").fileRefused())
        .isTrue();
    StringBuilder many = new StringBuilder("data;sku;quantidade;preco_unitario;forma_pagamento\n");
    for (int i = 0; i <= CounterImportParser.MAX_LINES; i++)
      many.append("03/11/2026;CAM-1;1;10,00;pix\n");
    assertThat(parse(many.toString()).fileRefused()).isTrue();
    assertThat(
            parse(
                    "data;sku;quantidade;preco_unitario;forma_pagamento\n\"03/11/2026;CAM-1;1;1;pix\n")
                .problems()
                .get(0)
                .message())
        .contains("aspas");
  }
}
