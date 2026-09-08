package com.flowpilot.domain.execution.repository;

import com.flowpilot.domain.execution.model.NodeExecution;

import java.util.List;

public interface NodeExecutionRepository {

    void create(NodeExecution execution);

    void markSuccess(Long nodeExecutionId, long durationMs);

    void markFailed(Long nodeExecutionId, long durationMs, String errorMessage);

    List<NodeExecution> findByExecutionId(String executionId);
}
