package com.flowpilot.domain.execution.repository;

import com.flowpilot.domain.execution.model.FlowExecution;

import java.util.Optional;
import java.time.LocalDateTime;

public interface FlowExecutionRepository {

    void create(FlowExecution execution);

    void markSuccess(String executionId, long durationMs);

    void markFailed(String executionId, long durationMs, String errorMessage);

    Optional<FlowExecution> findByExecutionId(String executionId);

    default int deleteOlderThan(LocalDateTime cutoff, int limit) {
        return 0;
    }
}
