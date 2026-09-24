package com.flowpilot.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class FlowPilotMetrics {

    private final MeterRegistry registry;

    public FlowPilotMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public Timer.Sample startExecutionTimer() {
        return Timer.start(registry);
    }

    public void recordExecution(
            Timer.Sample sample,
            String ruleCode,
            int version,
            String status,
            long durationMs
    ) {
        Counter.builder("flowpilot.execution.total")
                .description("Total rule executions by outcome")
                .tag("rule", ruleCode)
                .tag("version", Integer.toString(version))
                .tag("status", status)
                .register(registry)
                .increment();
        sample.stop(Timer.builder("flowpilot.execution.duration")
                .description("Rule execution duration")
                .tag("rule", ruleCode)
                .tag("version", Integer.toString(version))
                .tag("status", status)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry));
    }

    public void recordGrayProtection(String ruleCode, String action) {
        Counter.builder("flowpilot.gray.protection.total")
                .description("Automatic gray protection actions")
                .tag("rule", ruleCode)
                .tag("action", action)
                .register(registry)
                .increment();
    }
}
