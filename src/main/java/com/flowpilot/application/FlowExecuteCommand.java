package com.flowpilot.application;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record FlowExecuteCommand(
        @NotBlank String routingKey,
        Map<String, Object> variables
) {
}
