package com.flowpilot.application;

import com.flowpilot.domain.execution.model.ExecutionContext;
import com.flowpilot.domain.execution.model.ExecutionResult;
import com.flowpilot.domain.execution.repository.FlowExecutionRepository;
import com.flowpilot.domain.execution.repository.NodeExecutionRepository;
import com.flowpilot.domain.execution.service.ExecutionTraceService;
import com.flowpilot.domain.rule.model.RuleSnapshot;
import com.flowpilot.domain.rule.service.RuleResolver;
import com.flowpilot.engine.RuleEngine;
import com.flowpilot.exception.ExecutionNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class DefaultFlowExecutionApplicationService implements FlowExecutionApplicationService {

    private final RuleResolver ruleResolver;
    private final RuleEngine ruleEngine;
    private final ExecutionTraceService traceService;
    private final FlowExecutionRepository flowExecutionRepository;
    private final NodeExecutionRepository nodeExecutionRepository;

    public DefaultFlowExecutionApplicationService(
            RuleResolver ruleResolver,
            RuleEngine ruleEngine,
            ExecutionTraceService traceService,
            FlowExecutionRepository flowExecutionRepository,
            NodeExecutionRepository nodeExecutionRepository
    ) {
        this.ruleResolver = ruleResolver;
        this.ruleEngine = ruleEngine;
        this.traceService = traceService;
        this.flowExecutionRepository = flowExecutionRepository;
        this.nodeExecutionRepository = nodeExecutionRepository;
    }

    @Override
    public FlowExecuteResponse execute(String ruleCode, FlowExecuteCommand command) {
        requireText(ruleCode, "ruleCode");
        Objects.requireNonNull(command, "command must not be null");

        String executionId = UUID.randomUUID().toString();
        RuleSnapshot snapshot = ruleResolver.resolve(ruleCode, command.routingKey());
        ExecutionContext context = new ExecutionContext(
                executionId,
                command.routingKey(),
                command.variables() == null ? Map.of() : command.variables());
        long startedAt = System.nanoTime();
        traceService.startExecution(executionId, snapshot, command.routingKey());
        ExecutionResult result;
        try {
            result = ruleEngine.execute(snapshot, context);
        } catch (RuntimeException | Error throwable) {
            traceService.failExecution(executionId, elapsedMillis(startedAt), throwable);
            throw throwable;
        }
        traceService.successExecution(executionId, elapsedMillis(startedAt));
        return new FlowExecuteResponse(
                result.executionId(), result.ruleCode(), result.version(),
                result.success(), result.result());
    }

    @Override
    public ExecutionDetailResponse queryExecution(String executionId) {
        requireText(executionId, "executionId");
        var execution = flowExecutionRepository.findByExecutionId(executionId)
                .orElseThrow(() -> new ExecutionNotFoundException(executionId));
        var nodes = nodeExecutionRepository.findByExecutionId(executionId).stream()
                .map(NodeExecutionResponse::from)
                .toList();
        return ExecutionDetailResponse.from(execution, nodes);
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
