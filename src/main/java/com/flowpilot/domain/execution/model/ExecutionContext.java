package com.flowpilot.domain.execution.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ExecutionContext {

    private final String executionId;
    private final String routingKey;
    private final Map<String, Object> variables;
    private final List<String> executedNodes = Collections.synchronizedList(new ArrayList<>());

    public ExecutionContext(String executionId, String routingKey, Map<String, Object> variables) {
        this.executionId = requireText(executionId, "executionId");
        this.routingKey = requireText(routingKey, "routingKey");
        this.variables = Collections.synchronizedMap(
                new HashMap<>(variables == null ? Map.of() : variables));
    }

    public String executionId() {
        return executionId;
    }

    public String routingKey() {
        return routingKey;
    }

    public Object getVariable(String name) {
        return variables.get(name);
    }

    public <T> T getVariable(String name, Class<T> type) {
        Object value = variables.get(name);
        return value == null ? null : type.cast(value);
    }

    public void putVariable(String name, Object value) {
        variables.put(requireText(name, "variable name"), value);
    }

    public Map<String, Object> variables() {
        synchronized (variables) {
            return Collections.unmodifiableMap(new HashMap<>(variables));
        }
    }

    public void recordNode(String componentId) {
        executedNodes.add(requireText(componentId, "componentId"));
    }

    public List<String> executedNodes() {
        synchronized (executedNodes) {
            return List.copyOf(executedNodes);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
