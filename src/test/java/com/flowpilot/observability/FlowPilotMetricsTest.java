package com.flowpilot.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlowPilotMetricsTest {

    @Test
    void shouldRecordExecutionOutcomeWithRuleTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        FlowPilotMetrics metrics = new FlowPilotMetrics(registry);

        metrics.recordExecution(metrics.startExecutionTimer(), "ORDER_FLOW", 2, "SUCCEEDED", 12);

        assertThat(registry.get("flowpilot.execution.total")
                .tag("rule", "ORDER_FLOW")
                .tag("version", "2")
                .tag("status", "SUCCEEDED")
                .counter()
                .count()).isEqualTo(1);
    }
}
