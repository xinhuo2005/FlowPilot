package com.flowpilot.domain.execution.model;

import java.time.LocalDateTime;
import java.util.Objects;

public record NodeExecution(
        Long id,
        String executionId,
        String nodeId,
        NodeExecutionStatus status,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Long durationMs,
        String errorMessage,
        LocalDateTime createdAt
) {
    public NodeExecution {
        executionId = requireText(executionId, "executionId");
        nodeId = requireText(nodeId, "nodeId");
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
