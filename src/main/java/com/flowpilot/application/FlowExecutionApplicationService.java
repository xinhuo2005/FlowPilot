package com.flowpilot.application;

public interface FlowExecutionApplicationService {

    FlowExecuteResponse execute(String ruleCode, FlowExecuteCommand command);
}
