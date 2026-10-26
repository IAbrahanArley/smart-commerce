package com.smartcommerce.advisor.dto.request;

import com.smartcommerce.advisor.domain.AdvisorExchange;
import java.util.List;

/**
 * The owner's question (1 to 500 characters) and, in a conversation, up to 4 earlier answered
 * exchanges shown on the page (A7); all validated and cleaned by the service.
 */
public record AdvisorQuestionRequest(String question, List<AdvisorExchange> history) {}
