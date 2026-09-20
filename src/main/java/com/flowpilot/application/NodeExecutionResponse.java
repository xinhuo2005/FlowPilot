package com.flowpilot.application;

import com.flowpilot.domain.execution.model.NodeExecution;
import com.flowpilot.domain.execution.model.NodeExecutionStatus;

import java.time.LocalDateTime;

public record NodeExecutionResponse(
        String nodeId,
        NodeExecutionStatus status,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Long durationMs,
        String errorMessage
) {
    public static NodeExecutionResponse from(NodeExecution execution) {
        return new NodeExecutionResponse(
                execution.nodeId(), execution.status(), execution.startTime(), execution.endTime(),
                execution.durationMs(), execution.errorMessage());
    }
}
