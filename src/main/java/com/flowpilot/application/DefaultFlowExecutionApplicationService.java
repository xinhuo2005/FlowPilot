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
import com.flowpilot.observability.FlowPilotMetrics;
import com.flowpilot.observability.GrayProtectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class DefaultFlowExecutionApplicationService implements FlowExecutionApplicationService {

    private static final Logger log = LoggerFactory.getLogger(DefaultFlowExecutionApplicationService.class);

    private final RuleResolver ruleResolver;
    private final RuleEngine ruleEngine;
    private final ExecutionTraceService traceService;
    private final FlowExecutionRepository flowExecutionRepository;
    private final NodeExecutionRepository nodeExecutionRepository;
    private final FlowPilotMetrics metrics;
    private final GrayProtectionService grayProtectionService;

    @Autowired
    public DefaultFlowExecutionApplicationService(
            RuleResolver ruleResolver,
            RuleEngine ruleEngine,
            ExecutionTraceService traceService,
            FlowExecutionRepository flowExecutionRepository,
            NodeExecutionRepository nodeExecutionRepository,
            FlowPilotMetrics metrics,
            GrayProtectionService grayProtectionService
    ) {
        this.ruleResolver = ruleResolver;
        this.ruleEngine = ruleEngine;
        this.traceService = traceService;
        this.flowExecutionRepository = flowExecutionRepository;
        this.nodeExecutionRepository = nodeExecutionRepository;
        this.metrics = metrics;
        this.grayProtectionService = grayProtectionService;
    }

    /**
     * Compatibility constructor for focused unit tests that do not load the observability layer.
     */
    public DefaultFlowExecutionApplicationService(
            RuleResolver ruleResolver,
            RuleEngine ruleEngine,
            ExecutionTraceService traceService,
            FlowExecutionRepository flowExecutionRepository,
            NodeExecutionRepository nodeExecutionRepository
    ) {
        this(ruleResolver, ruleEngine, traceService, flowExecutionRepository,
                nodeExecutionRepository, null, null);
    }

    @Override
    public FlowExecuteResponse execute(String ruleCode, FlowExecuteCommand command) {
        requireText(ruleCode, "ruleCode");
        Objects.requireNonNull(command, "command must not be null");

        String executionId = UUID.randomUUID().toString();
        Timer.Sample metricsTimer = metrics == null ? null : metrics.startExecutionTimer();
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
            long durationMs = elapsedMillis(startedAt);
            traceService.failExecution(executionId, durationMs, throwable);
            if (metrics != null) {
                metrics.recordExecution(metricsTimer, snapshot.ruleCode(), snapshot.version(), "FAILED", durationMs);
            }
            if (grayProtectionService != null) {
                grayProtectionService.recordFailure(snapshot.ruleCode(), snapshot.version());
            }
            log.warn("flow_execution_failed executionId={} ruleCode={} ruleVersion={} durationMs={} reason={}",
                    executionId, snapshot.ruleCode(), snapshot.version(), durationMs, throwable.getMessage());
            throw throwable;
        }
        long durationMs = elapsedMillis(startedAt);
        traceService.successExecution(executionId, durationMs);
        if (metrics != null) {
            metrics.recordExecution(metricsTimer, snapshot.ruleCode(), snapshot.version(), "SUCCEEDED", durationMs);
        }
        log.info("flow_execution_succeeded executionId={} ruleCode={} ruleVersion={} durationMs={}",
                executionId, snapshot.ruleCode(), snapshot.version(), durationMs);
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
