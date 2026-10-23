package com.smartcommerce.advisor.dto.response;

/** Whether the assistant is on here and how many questions the store has left this month. */
public record AdvisorStatusView(
    boolean enabled, int monthlyQuota, long usedThisMonth, long remaining) {}
