package com.flowpilot.component;

import com.flowpilot.domain.execution.model.ExecutionContext;
import com.flowpilot.domain.execution.service.ExecutionTraceService;
import com.yomahub.liteflow.annotation.LiteflowComponent;

@LiteflowComponent("notify")
public class NotifyComponent extends AbstractDemoComponent {

    public NotifyComponent(ExecutionTraceService traceService) {
        super(traceService);
    }

    @Override
    protected void apply(ExecutionContext context) {
        context.putVariable("notified", true);
    }
}
