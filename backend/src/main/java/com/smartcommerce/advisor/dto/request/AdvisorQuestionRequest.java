package com.smartcommerce.advisor.dto.request;

/** The owner's question; validated and cleaned by the service (1 to 500 characters). */
public record AdvisorQuestionRequest(String question) {}
