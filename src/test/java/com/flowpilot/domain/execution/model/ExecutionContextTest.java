package com.flowpilot.domain.execution.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionContextTest {

    @Test
    void shouldDefensivelyCopyVariablesAndExposeSnapshots() {
        Map<String, Object> source = new HashMap<>();
        source.put("amount", 100);
        ExecutionContext context = new ExecutionContext("exec-1", "user-1", source);

        source.put("amount", 200);
        context.putVariable("orderCreated", true);

        assertThat(context.getVariable("amount", Integer.class)).isEqualTo(100);
        assertThat(context.variables()).containsEntry("orderCreated", true);
        assertThatThrownBy(() -> context.variables().put("changed", true))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldKeepAnImmutableExecutionTraceSnapshot() {
        ExecutionContext context = new ExecutionContext("exec-2", "user-2", Map.of());
        context.recordNode("userCheck");

        assertThat(context.executedNodes()).containsExactly("userCheck");
        assertThatThrownBy(() -> context.executedNodes().add("stockCheck"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
