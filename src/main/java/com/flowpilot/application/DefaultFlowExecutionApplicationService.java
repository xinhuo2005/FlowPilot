package com.flowpilot.application;

import com.flowpilot.domain.execution.model.ExecutionContext;
import com.flowpilot.domain.execution.model.ExecutionMode;
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
import com.flowpilot.observability.ExecutionAdmissionController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.LinkedHashMap;
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
    private final ExecutionAdmissionController admissionController;

    @Autowired
    public DefaultFlowExecutionApplicationService(
            RuleResolver ruleResolver,
            RuleEngine ruleEngine,
            ExecutionTraceService traceService,
            FlowExecutionRepository flowExecutionRepository,
            NodeExecutionRepository nodeExecutionRepository,
            FlowPilotMetrics metrics,
            GrayProtectionService grayProtectionService,
            ExecutionAdmissionController admissionController
    ) {
        this.ruleResolver = ruleResolver;
        this.ruleEngine = ruleEngine;
        this.traceService = traceService;
        this.flowExecutionRepository = flowExecutionRepository;
        this.nodeExecutionRepository = nodeExecutionRepository;
        this.metrics = metrics;
        this.grayProtectionService = grayProtectionService;
        this.admissionController = admissionController;
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
                nodeExecutionRepository, null, null, null);
    }

    @Override
    public FlowExecuteResponse execute(String ruleCode, FlowExecuteCommand command) {
        if (admissionController != null) {
            admissionController.acquire();
        }
        try {
            return executeWithAdmission(ruleCode, command);
        } finally {
            if (admissionController != null) {
                admissionController.release();
            }
        }
    }

    private FlowExecuteResponse executeWithAdmission(String ruleCode, FlowExecuteCommand command) {
        requireText(ruleCode, "ruleCode");
        Objects.requireNonNull(command, "command must not be null");

        RuleSnapshot snapshot = ruleResolver.resolve(ruleCode, command.routingKey());
        if (command.mode() == ExecutionMode.SHADOW) {
            ExecutionResult primary = executeSnapshot(snapshot, command, ExecutionMode.DRY_RUN, true);
            RuleSnapshot shadowSnapshot = ruleResolver.resolveVersion(
                    ruleCode, command.routingKey(), command.shadowVersion());
            ExecutionResult shadow = executeSnapshot(
                    shadowSnapshot, command, ExecutionMode.DRY_RUN, true);
            Map<String, Object> comparison = new LinkedHashMap<>();
            comparison.put("matched", Objects.equals(primary.result(), shadow.result()));
            comparison.put("primaryExecutionId", primary.executionId());
            comparison.put("primaryVersion", primary.version());
            comparison.put("primaryResult", primary.result());
            comparison.put("shadowExecutionId", shadow.executionId());
            comparison.put("shadowVersion", shadow.version());
            comparison.put("shadowSuccess", shadow.success());
            comparison.put("shadowResult", shadow.result());
            return new FlowExecuteResponse(
                    primary.executionId(), primary.ruleCode(), primary.version(),
                    primary.success(), comparison);
        }

        ExecutionResult result = executeSnapshot(snapshot, command, command.mode(), false);
        return new FlowExecuteResponse(
                result.executionId(), result.ruleCode(), result.version(),
                result.success(), result.result());
    }

    private ExecutionResult executeSnapshot(
            RuleSnapshot snapshot,
            FlowExecuteCommand command,
            ExecutionMode mode,
            boolean swallowFailure
    ) {
        String executionId = UUID.randomUUID().toString();
        Timer.Sample metricsTimer = metrics == null ? null : metrics.startExecutionTimer();
        ExecutionContext context = new ExecutionContext(
                executionId,
                command.routingKey(),
                command.variables() == null ? Map.of() : command.variables(),
                mode);
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
            if (grayProtectionService != null && mode == ExecutionMode.LIVE) {
                grayProtectionService.recordFailure(snapshot.ruleCode(), snapshot.version());
            }
            log.warn("flow_execution_failed executionId={} ruleCode={} ruleVersion={} durationMs={} reason={}",
                    executionId, snapshot.ruleCode(), snapshot.version(), durationMs, throwable.getMessage());
            if (swallowFailure) {
                return new ExecutionResult(
                        executionId, snapshot.ruleCode(), snapshot.version(), false,
                        Map.of("error", rootMessage(throwable)));
            }
            throw throwable;
        }
        long durationMs = elapsedMillis(startedAt);
        traceService.successExecution(executionId, durationMs);
        if (metrics != null) {
            metrics.recordExecution(metricsTimer, snapshot.ruleCode(), snapshot.version(), "SUCCEEDED", durationMs);
        }
        log.info("flow_execution_succeeded executionId={} ruleCode={} ruleVersion={} durationMs={}",
                executionId, snapshot.ruleCode(), snapshot.version(), durationMs);
        return result;
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

    private static String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() == null || root.getMessage().isBlank()
                ? root.getClass().getSimpleName()
                : root.getMessage();
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
