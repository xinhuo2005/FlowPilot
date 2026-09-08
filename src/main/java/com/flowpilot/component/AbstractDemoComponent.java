package com.flowpilot.component;

import com.flowpilot.domain.execution.model.ExecutionContext;
import com.yomahub.liteflow.core.NodeComponent;

abstract class AbstractDemoComponent extends NodeComponent {

    @Override
    public final void process() {
        ExecutionContext context = getContextBean(ExecutionContext.class);
        context.recordNode(getNodeId());
        apply(context);
    }

    protected void apply(ExecutionContext context) {
    }
}
