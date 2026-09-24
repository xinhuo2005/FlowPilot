package com.flowpilot.component;

import com.flowpilot.domain.execution.model.ExecutionContext;
import com.flowpilot.domain.execution.service.ExecutionTraceService;
import com.yomahub.liteflow.annotation.LiteflowComponent;

@LiteflowComponent("createOrder")
public class CreateOrderComponent extends AbstractDemoComponent {

    public CreateOrderComponent(ExecutionTraceService traceService) {
        super(traceService);
    }

    @Override
    protected void apply(ExecutionContext context) {
        context.putVariable(
                context.sideEffectsAllowed() ? "orderCreated" : "orderWouldBeCreated", true);
    }
}
