package com.smartcommerce.advisor.dto.response;

import java.util.List;

/**
 * One answered question (SC-026). {@code outcome} is ANSWERED, REFUSED or UNGROUNDED; {@code
 * answer} exists only when ANSWERED. {@code tools} names the tools whose data was used.
 */
public record AdvisorAnswerView(String outcome, String answer, List<String> tools, int toolCalls) {}
