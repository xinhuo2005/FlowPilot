package com.flowpilot.component;

import com.flowpilot.domain.execution.model.ExecutionContext;
import com.yomahub.liteflow.annotation.LiteflowComponent;

@LiteflowComponent("notify")
public class NotifyComponent extends AbstractDemoComponent {

    @Override
    protected void apply(ExecutionContext context) {
        context.putVariable("notified", true);
    }
}
