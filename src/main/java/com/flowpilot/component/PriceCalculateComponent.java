package com.flowpilot.component;

import com.flowpilot.domain.execution.model.ExecutionContext;
import com.yomahub.liteflow.annotation.LiteflowComponent;

@LiteflowComponent("priceCalculate")
public class PriceCalculateComponent extends AbstractDemoComponent {

    @Override
    protected void apply(ExecutionContext context) {
        Object amount = context.getVariable("amount");
        if (amount != null) {
            context.putVariable("finalPrice", amount);
        }
    }
}
