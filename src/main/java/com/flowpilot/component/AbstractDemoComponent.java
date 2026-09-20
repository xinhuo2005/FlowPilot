package com.flowpilot.component;

import com.flowpilot.domain.execution.model.ExecutionContext;
import com.flowpilot.domain.execution.service.ExecutionTraceService;
import com.yomahub.liteflow.core.NodeComponent;

abstract class AbstractDemoComponent extends NodeComponent {

    private final ExecutionTraceService traceService;

    protected AbstractDemoComponent(ExecutionTraceService traceService) {
        this.traceService = traceService;
    }

    @Override
    public final void process() {
        ExecutionContext context = getContextBean(ExecutionContext.class);
        Long nodeExecutionId = traceService.startNode(context.executionId(), getNodeId());
        long startedAt = System.nanoTime();
        try {
            context.recordNode(getNodeId());
            apply(context);
            traceService.successNode(nodeExecutionId, elapsedMillis(startedAt));
        } catch (RuntimeException | Error throwable) {
            traceService.failNode(nodeExecutionId, elapsedMillis(startedAt), throwable);
            throw throwable;
        }
    }

    protected void apply(ExecutionContext context) {
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }
}
