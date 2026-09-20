package com.flowpilot.component;

import com.flowpilot.domain.execution.model.ExecutionContext;
import com.flowpilot.domain.execution.service.ExecutionTraceService;
import com.yomahub.liteflow.annotation.LiteflowComponent;

@LiteflowComponent("userCheck")
public class UserCheckComponent extends AbstractDemoComponent {

    public UserCheckComponent(ExecutionTraceService traceService) {
        super(traceService);
    }

    @Override
    protected void apply(ExecutionContext context) {
        if (Boolean.FALSE.equals(context.getVariable("userValid"))) {
            throw new IllegalStateException("user check failed");
        }
    }
}
