package com.smartcommerce.advisor;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartcommerce.advisor.domain.AdvisorGrounding;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** ADR-0016 A5: no number or date reaches the owner without a source in the tool results. */
class AdvisorGroundingTest {
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private static AdvisorGrounding report() {
    return new AdvisorGrounding()
        .addResult(
            JSON.readTree(
                """
                {"period":{"key":"THIS_MONTH","firstDay":"2026-10-01","lastDay":"2026-10-31"},
                 "grossRevenue":"1234.56","refunds":"-20.00","marginPercent":"33.33",
                 "paidOrders":7,"share":0.25,"complete":false,
                 "items":[{"productName":"Tênis 42","lastSaleAt":"2026-10-05T02:30:00Z"}]}
                """));
  }

  @Test
  void acceptsBrazilianFormatsRoundingAndSigns() {
    var grounding = report();
    assertThat(grounding.grounded("A receita bruta foi R$ 1.234,56 em 7 pedidos pagos.")).isTrue();
    assertThat(grounding.grounded("Cerca de R$ 1.235 de receita.")).isTrue(); // rounded
    assertThat(grounding.grounded("Reembolsos de R$ 20,00 no mês.")).isTrue(); // sign ignored
    assertThat(grounding.grounded("Margem de 33,33%, ou 33,3% arredondada, ou 33%.")).isTrue();
    assertThat(grounding.grounded("Esse item é 25% do total.")).isTrue(); // fraction × 100
    assertThat(grounding.grounded("Receita de 1234.56 (texto de máquina).")).isTrue();
    assertThat(grounding.grounded("O Tênis 42 vendeu pela última vez em 05/10.")).isTrue();
    assertThat(grounding.grounded("Período de 01/10/2026 a 31/10/2026.")).isTrue();
    // The instant 02:30 UTC on the 5th is the 4th in São Paulo: both days are accepted.
    assertThat(grounding.grounded("Última venda em 04/10/2026.")).isTrue();
    assertThat(grounding.grounded("Sem números, só texto.")).isTrue();
  }

  @Test
  void rejectsInventedNumbersDatesAndWrongRounding() {
    var grounding = report();
    assertThat(grounding.grounded("O lucro foi R$ 1 milhão.")).isFalse(); // 1 has no source
    assertThat(grounding.grounded("A receita foi R$ 999,00.")).isFalse();
    assertThat(grounding.grounded("A receita foi R$ 1.234,57.")).isFalse();
    assertThat(grounding.grounded("Margem de 34%.")).isFalse();
    assertThat(grounding.grounded("Margem de 33,4%.")).isFalse();
    assertThat(grounding.grounded("Foram 8 pedidos pagos.")).isFalse();
    assertThat(grounding.grounded("Última venda em 06/10.")).isFalse();
    assertThat(grounding.grounded("Data impossível 32/13.")).isFalse();
    assertThat(grounding.grounded("Valor 1.23.4 malformado.")).isFalse();
  }

  @Test
  void numbersFromTheQuestionAreSources() {
    var grounding = report().addText("Quanto vendi nos últimos 30 dias, e a meta de R$ 5.000,00?");
    assertThat(grounding.grounded("Nos últimos 30 dias a receita foi R$ 1.234,56.")).isTrue();
    assertThat(grounding.grounded("Abaixo da meta de R$ 5.000,00.")).isTrue();
    assertThat(grounding.grounded("Faltam R$ 3.765,44 para a meta.")).isFalse(); // derived
  }
}
