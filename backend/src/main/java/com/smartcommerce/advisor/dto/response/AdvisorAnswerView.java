package com.smartcommerce.advisor.dto.response;

import java.util.List;

/**
 * One answered question (SC-026, SC-027). {@code outcome} is ANSWERED, REFUSED or UNGROUNDED;
 * {@code answer} exists only when ANSWERED, and then every number in it has a source among the
 * tools that ran (A5). {@code tools} names every tool whose data was read.
 */
public record AdvisorAnswerView(
    String outcome, String answer, List<Source> sources, List<String> tools, int toolCalls) {
  /**
   * Where the numbers came from: the tool, the period it ran for (null for tools without one) and
   * the metrics, as the model described them (null when it named none).
   */
  public record Source(String tool, String period, String metric) {}
}
