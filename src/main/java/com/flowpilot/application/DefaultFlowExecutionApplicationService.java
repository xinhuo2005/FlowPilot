package com.flowpilot.application;

import com.flowpilot.domain.execution.model.ExecutionContext;
import com.flowpilot.domain.execution.model.ExecutionResult;
import com.flowpilot.domain.rule.model.RuleSnapshot;
import com.flowpilot.domain.rule.service.RuleResolver;
import com.flowpilot.engine.RuleEngine;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class DefaultFlowExecutionApplicationService implements FlowExecutionApplicationService {

    private final RuleResolver ruleResolver;
    private final RuleEngine ruleEngine;

    public DefaultFlowExecutionApplicationService(RuleResolver ruleResolver, RuleEngine ruleEngine) {
        this.ruleResolver = ruleResolver;
        this.ruleEngine = ruleEngine;
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
        ExecutionResult result = ruleEngine.execute(snapshot, context);
        return new FlowExecuteResponse(
                result.executionId(), result.ruleCode(), result.version(),
                result.success(), result.result());
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
