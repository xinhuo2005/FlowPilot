package com.flowpilot.component;

import com.flowpilot.domain.execution.model.ExecutionContext;
import com.yomahub.liteflow.annotation.LiteflowComponent;

@LiteflowComponent("riskCheck")
public class RiskCheckComponent extends AbstractDemoComponent {

    @Override
    protected void apply(ExecutionContext context) {
        if (Boolean.FALSE.equals(context.getVariable("riskPassed"))) {
            throw new IllegalStateException("risk check failed");
        }
    }
}
