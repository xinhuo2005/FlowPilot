package com.flowpilot.interfaces.rest;

public record ApiErrorResponse(
        String code,
        String message,
        String executionId
) {
}
