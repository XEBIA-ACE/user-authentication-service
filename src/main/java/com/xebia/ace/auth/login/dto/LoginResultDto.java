package com.xebia.ace.auth.login.dto;

import java.time.Instant;

public record LoginResultDto(
        boolean success,
        String errorCode,
        String errorMessage,
        boolean requiresAdditionalAction,
        String sessionId,
        UserSummaryDto user,
        Instant issuedAt,
        Instant expiresAt) {

    /** Shared by every authentication failure so the response never reveals which check failed. */
    public static final String GENERIC_LOGIN_FAILURE_MESSAGE =
            "Login failed. Please check your credentials and try again.";

    public static LoginResultDto success(String sessionId, UserSummaryDto user, Instant issuedAt, Instant expiresAt) {
        return new LoginResultDto(true, null, null, false, sessionId, user, issuedAt, expiresAt);
    }

    public static LoginResultDto failure(LoginErrorCode errorCode) {
        return failure(errorCode, GENERIC_LOGIN_FAILURE_MESSAGE);
    }

    public static LoginResultDto failure(LoginErrorCode errorCode, String errorMessage) {
        return new LoginResultDto(false, errorCode.name(), errorMessage, false, null, null, null, null);
    }
}
