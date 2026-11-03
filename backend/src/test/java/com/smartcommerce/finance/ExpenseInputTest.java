package com.smartcommerce.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartcommerce.finance.domain.ExpenseInput;
import com.smartcommerce.finance.domain.ExpensePayment;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * SC-020, SC-051: expense and payable input rules (ADR-0015 F6, F10), without Spring or a database.
 */
class ExpenseInputTest {
  private static final String REQUEST = "11111111-1111-4111-8111-111111111111";
  private static final String ACCOUNT = "22222222-2222-4222-8222-222222222222";
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
    assertThat(input.dueDate()).isEqualTo(input.competenceDate());
    assertThat(
            ExpenseInput.of(
                    REQUEST,
                    "RENT",
                    "FIXED",
                    "Aluguel",
                    "1500",
                    "2026-10-01",
                    null,
                    null,
                    null,
                    null,
                    null,
                    "2026-10-21",
                    ACCOUNT,
                    TODAY)
                .paidOn())
        .isEqualTo(TODAY);
  }

  @Test
  void installmentsSplitTheTotalAndCountInTheirDueMonth() {
    var input = bill("1000.00", null, "2026-11-30", 3, null, null, null);
    assertThat(input.competenceDate()).isEqualTo(LocalDate.of(2026, 11, 30));
    assertThat(input.installmentAmounts())
        .extracting(java.math.BigDecimal::toPlainString)
        .containsExactly("333.33", "333.33", "333.34");
    assertThat(bill("0.03", null, "2026-11-30", 3, null, null, null).installmentAmounts())
        .extracting(java.math.BigDecimal::toPlainString)
        .containsExactly("0.01", "0.01", "0.01");
    // A due date alone is also the competence; a competence alone is also the due date.
    assertThat(bill("10", null, "2026-12-05", 1, null, null, null).competenceDate())
        .isEqualTo(LocalDate.of(2026, 12, 5));
    assertThat(bill("10", "2026-12-05", null, 1, null, null, null).dueDate())
        .isEqualTo(LocalDate.of(2026, 12, 5));
  }

  @Test
  void rejectsInconsistentInstallmentsRecurrencesAndPayments() {
    for (Runnable invalid :
        List.<Runnable>of(
            () -> bill("10", null, "2026-11-30", 0, null, null, null),
            () -> bill("10", null, "2026-11-30", 25, null, null, null),
            () -> bill("0.02", null, "2026-11-30", 3, null, null, null),
            () -> bill("10", null, null, 2, null, null, null),
            () -> bill("10", "2026-11-01", "2026-11-30", 2, null, null, null),
            () -> bill("10", null, "2026-11-30", 2, "MONTHLY", null, null),
            () -> bill("10", null, "2026-11-30", 2, null, "2026-10-21", ACCOUNT),
            () -> bill("10", null, "2026-11-30", 1, "WEEKLY", null, null),
            () -> bill("10", null, "2026-11-30", 1, null, "2026-10-21", null),
            () -> bill("10", null, "2026-11-30", 1, null, null, ACCOUNT),
            () -> bill("10", null, "2026-11-30", 1, null, "2026-10-21", "conta"),
            () -> bill("10", null, null, 1, null, null, null)))
      assertThatThrownBy(invalid::run).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void paymentsAcceptOptionalSurchargeAndDiscountButNoFutureDay() {
    var payment = ExpensePayment.of("2026-10-21", ACCOUNT, "", null, TODAY);
    assertThat(payment.surcharge().toPlainString()).isEqualTo("0.00");
    assertThat(payment.discount().toPlainString()).isEqualTo("0.00");
    assertThat(ExpensePayment.of("2026-10-01", ACCOUNT, "2.5", "1", TODAY).surcharge())
        .isEqualByComparingTo("2.50");
    for (String[] values :
        new String[][] {
          {"2026-10-22", ACCOUNT, null, null},
          {"1999-12-31", ACCOUNT, null, null},
          {"2026-10-21", "conta", null, null},
          {"2026-10-21", ACCOUNT, "-1", null},
          {"2026-10-21", ACCOUNT, null, "1.001"}
        })
      assertThatThrownBy(() -> ExpensePayment.of(values[0], values[1], values[2], values[3], TODAY))
          .as(java.util.Arrays.toString(values))
          .isInstanceOf(IllegalArgumentException.class);
  }

  private static ExpenseInput bill(
      String amount,
      String competence,
      String due,
      Integer installments,
      String recurrence,
      String paidOn,
      String account) {
    return ExpenseInput.of(
        REQUEST,
        "OTHER",
        "FIXED",
        "Duplicata",
        amount,
        competence,
        due,
        null,
        null,
        installments,
        recurrence,
        paidOn,
        account,
        TODAY);
  }

  @Test
  void rejectsUnknownListsBadAmountsDatesAndText() {
    String[][] invalid = {
      {"not-a-uuid", "RENT", "FIXED", "Aluguel", "10", "2026-10-01", ""},
      {REQUEST, "TAXES", "FIXED", "Aluguel", "10", "2026-10-01", ""},
      {REQUEST, null, "FIXED", "Aluguel", "10", "2026-10-01", ""},
      {REQUEST, "RENT", null, "Aluguel", "10", "2026-10-01", ""},
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
