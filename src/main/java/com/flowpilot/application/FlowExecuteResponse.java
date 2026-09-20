package com.flowpilot.application;

public record FlowExecuteResponse(
        String executionId,
        String ruleCode,
        Integer ruleVersion,
        boolean success,
        Object result
) {
}
