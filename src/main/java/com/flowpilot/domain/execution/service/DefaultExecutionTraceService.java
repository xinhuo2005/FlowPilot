package com.flowpilot.domain.execution.service;

import com.flowpilot.domain.execution.model.ExecutionStatus;
import com.flowpilot.domain.execution.model.FlowExecution;
import com.flowpilot.domain.execution.model.NodeExecution;
import com.flowpilot.domain.execution.model.NodeExecutionStatus;
import com.flowpilot.domain.execution.repository.FlowExecutionRepository;
import com.flowpilot.domain.execution.repository.NodeExecutionRepository;
import com.flowpilot.domain.rule.model.RuleSnapshot;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class DefaultExecutionTraceService implements ExecutionTraceService {

    private final FlowExecutionRepository flowExecutionRepository;
    private final NodeExecutionRepository nodeExecutionRepository;

    public DefaultExecutionTraceService(
            FlowExecutionRepository flowExecutionRepository,
            NodeExecutionRepository nodeExecutionRepository
    ) {
        this.flowExecutionRepository = flowExecutionRepository;
        this.nodeExecutionRepository = nodeExecutionRepository;
    }

    @Override
    public void startExecution(String executionId, RuleSnapshot snapshot, String routingKey) {
        flowExecutionRepository.create(new FlowExecution(
                null, executionId, snapshot.ruleCode(), snapshot.version(), routingKey,
                ExecutionStatus.RUNNING, LocalDateTime.now(), null, null, null, null, null));
    }

    @Override
    public void successExecution(String executionId, long durationMs) {
        flowExecutionRepository.markSuccess(executionId, durationMs);
    }

    @Override
    public void failExecution(String executionId, long durationMs, Throwable throwable) {
        flowExecutionRepository.markFailed(executionId, durationMs, errorMessage(throwable));
    }

    @Override
    public Long startNode(String executionId, String nodeId) {
        return nodeExecutionRepository.create(new NodeExecution(
                null, executionId, nodeId, NodeExecutionStatus.RUNNING,
                LocalDateTime.now(), null, null, null, null));
    }

    @Override
    public void successNode(Long nodeExecutionId, long durationMs) {
        nodeExecutionRepository.markSuccess(nodeExecutionId, durationMs);
    }

    @Override
    public void failNode(Long nodeExecutionId, long durationMs, Throwable throwable) {
        nodeExecutionRepository.markFailed(nodeExecutionId, durationMs, errorMessage(throwable));
    }

    private static String errorMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown execution failure";
        }
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        String message = rootCause.getMessage();
        return message == null || message.isBlank()
                ? rootCause.getClass().getSimpleName()
                : message;
    }
}
