package com.flowpilot.domain.execution.service;

import com.flowpilot.domain.rule.model.RuleSnapshot;

public interface ExecutionTraceService {

    void startExecution(String executionId, RuleSnapshot snapshot, String routingKey);

    void successExecution(String executionId, long durationMs);

    void failExecution(String executionId, long durationMs, Throwable throwable);

    Long startNode(String executionId, String nodeId);

    void successNode(Long nodeExecutionId, long durationMs);

    void failNode(Long nodeExecutionId, long durationMs, Throwable throwable);
}
