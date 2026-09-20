package com.flowpilot.component;

import com.flowpilot.domain.execution.model.ExecutionContext;
import com.flowpilot.domain.execution.service.ExecutionTraceService;
import com.yomahub.liteflow.annotation.LiteflowComponent;

@LiteflowComponent("stockCheck")
public class StockCheckComponent extends AbstractDemoComponent {

    public StockCheckComponent(ExecutionTraceService traceService) {
        super(traceService);
    }

    @Override
    protected void apply(ExecutionContext context) {
        if (Boolean.FALSE.equals(context.getVariable("stockAvailable"))) {
            throw new IllegalStateException("stock check failed");
        }
    }
}
