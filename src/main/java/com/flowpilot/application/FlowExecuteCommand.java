package com.flowpilot.application;

import com.flowpilot.domain.execution.model.ExecutionMode;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record FlowExecuteCommand(
        @NotBlank String routingKey,
        Map<String, Object> variables,
        ExecutionMode mode,
        Integer shadowVersion
) {

    public FlowExecuteCommand(String routingKey, Map<String, Object> variables) {
        this(routingKey, variables, ExecutionMode.LIVE, null);
    }

    public FlowExecuteCommand {
        mode = mode == null ? ExecutionMode.LIVE : mode;
        if (shadowVersion != null && shadowVersion < 1) {
            throw new IllegalArgumentException("shadowVersion must be positive");
        }
        if (mode == ExecutionMode.SHADOW && shadowVersion == null) {
            throw new IllegalArgumentException("shadowVersion is required for SHADOW mode");
        }
    }
}
