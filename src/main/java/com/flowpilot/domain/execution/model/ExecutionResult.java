package com.flowpilot.domain.execution.model;

public record ExecutionResult(
        String executionId,
        String ruleCode,
        Integer version,
        boolean success,
        Object result
) {
}
