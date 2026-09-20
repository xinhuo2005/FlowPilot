package com.flowpilot.application;

import com.flowpilot.domain.execution.model.ExecutionStatus;
import com.flowpilot.domain.execution.model.FlowExecution;

import java.time.LocalDateTime;
import java.util.List;

public record ExecutionDetailResponse(
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
        List<NodeExecutionResponse> nodes
) {
    public static ExecutionDetailResponse from(
            FlowExecution execution,
            List<NodeExecutionResponse> nodes
    ) {
        return new ExecutionDetailResponse(
                execution.executionId(), execution.ruleCode(), execution.ruleVersion(),
                execution.routingKey(), execution.status(), execution.startTime(), execution.endTime(),
                execution.durationMs(), execution.errorCode(), execution.errorMessage(), List.copyOf(nodes));
    }
}
