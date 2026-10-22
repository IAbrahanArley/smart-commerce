package com.smartcommerce.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartcommerce.finance.domain.ExpenseInput;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** SC-020: expense input rules (ADR-0015 F6, F10), without Spring or a database. */
class ExpenseInputTest {
  private static final String REQUEST = "11111111-1111-4111-8111-111111111111";
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

  @Test
  void acceptsAValidExpenseAndNormalizesAmountAndText() {
    var input =
        ExpenseInput.of(
            REQUEST,
            "PACKAGING",
            "VARIABLE",
            "  Caixas de papelão  ",
            "12.5",
            "2026-10-01",
            "",
            TODAY);
    assertThat(input.amount().toPlainString()).isEqualTo("12.50");
    assertThat(input.description()).isEqualTo("Caixas de papelão");
    assertThat(input.paidOn()).isNull();
    assertThat(
            ExpenseInput.of(
                    REQUEST, "RENT", "FIXED", "Aluguel", "1500", "2026-10-01", "2026-10-21", TODAY)
                .paidOn())
        .isEqualTo(TODAY);
  }

  @Test
  void rejectsUnknownListsBadAmountsDatesAndText() {
    String[][] invalid = {
      {"not-a-uuid", "RENT", "FIXED", "Aluguel", "10", "2026-10-01", ""},
      {REQUEST, "TAXES", "FIXED", "Aluguel", "10", "2026-10-01", ""},
      {REQUEST, "RENT", "MONTHLY", "Aluguel", "10", "2026-10-01", ""},
      {REQUEST, "RENT", "FIXED", "Al", "10", "2026-10-01", ""},
      {REQUEST, "RENT", "FIXED", "x".repeat(201), "10", "2026-10-01", ""},
      {REQUEST, "RENT", "FIXED", "Linha\nquebrada", "10", "2026-10-01", ""},
      {REQUEST, "RENT", "FIXED", "Aluguel", "0", "2026-10-01", ""},
      {REQUEST, "RENT", "FIXED", "Aluguel", "-5", "2026-10-01", ""},
      {REQUEST, "RENT", "FIXED", "Aluguel", "1.234", "2026-10-01", ""},
      {REQUEST, "RENT", "FIXED", "Aluguel", "1e3", "2026-10-01", ""},
      {REQUEST, "RENT", "FIXED", "Aluguel", "10", "2026-02-30", ""},
      {REQUEST, "RENT", "FIXED", "Aluguel", "10", "1999-12-31", ""},
      {REQUEST, "RENT", "FIXED", "Aluguel", "10", "2027-10-22", ""},
      {REQUEST, "RENT", "FIXED", "Aluguel", "10", "2026-10-01", "2026-10-22"},
      {REQUEST, "RENT", "FIXED", "Aluguel", "10", "2026-10-01", "ontem"}
    };
    for (String[] values : invalid)
      assertThatThrownBy(
              () ->
                  ExpenseInput.of(
                      values[0], values[1], values[2], values[3], values[4], values[5], values[6],
                      TODAY))
          .as(String.join("|", values))
          .isInstanceOf(IllegalArgumentException.class);
    assertThat(ExpenseInput.line("Lançada em dobro", 300)).isEqualTo("Lançada em dobro");
    assertThatThrownBy(() -> ExpenseInput.line("x".repeat(301), 300))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
