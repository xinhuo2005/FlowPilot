package com.flowpilot.component;

import com.flowpilot.domain.execution.model.ExecutionContext;
import com.yomahub.liteflow.annotation.LiteflowComponent;

@LiteflowComponent("createOrder")
public class CreateOrderComponent extends AbstractDemoComponent {

    @Override
    protected void apply(ExecutionContext context) {
        context.putVariable("orderCreated", true);
    }
}
