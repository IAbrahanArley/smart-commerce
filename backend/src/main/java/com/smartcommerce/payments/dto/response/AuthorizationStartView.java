package com.smartcommerce.payments.dto.response;

/** The provider URL the admin navigates to. It carries only the state and the PKCE challenge. */
public record AuthorizationStartView(String authorizationUrl) {}
