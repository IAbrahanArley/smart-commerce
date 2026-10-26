package com.smartcommerce.advisor.domain;

/**
 * One earlier exchange of the page's conversation (A7): the owner's question and the answer shown.
 * It travels with the next question and is never stored.
 */
public record AdvisorExchange(String question, String answer) {}
