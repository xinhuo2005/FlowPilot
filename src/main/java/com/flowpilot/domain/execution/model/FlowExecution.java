package com.flowpilot.domain.execution.model;

import java.time.LocalDateTime;
import java.util.Objects;

public record FlowExecution(
        Long id,
        String executionId,
        String ruleCode,
        Integer ruleVersion,
        String routingKey,
        ExecutionStatus status,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Long durationMs,
        String errorCode,
        String errorMessage,
        LocalDateTime createdAt
) {
    public FlowExecution {
        executionId = requireText(executionId, "executionId");
        ruleCode = requireText(ruleCode, "ruleCode");
        if (ruleVersion == null || ruleVersion < 1) {
            throw new IllegalArgumentException("ruleVersion must be positive");
        }
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(startTime, "startTime must not be null");
        if (durationMs != null && durationMs < 0) {
            throw new IllegalArgumentException("durationMs must not be negative");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
