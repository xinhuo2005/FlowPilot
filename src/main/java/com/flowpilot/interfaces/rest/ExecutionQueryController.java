package com.flowpilot.interfaces.rest;

import com.flowpilot.application.ExecutionDetailResponse;
import com.flowpilot.application.FlowExecutionApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/executions")
public class ExecutionQueryController {

    private final FlowExecutionApplicationService executionService;

    public ExecutionQueryController(FlowExecutionApplicationService executionService) {
        this.executionService = executionService;
    }

    @GetMapping("/{executionId}")
    public ExecutionDetailResponse getExecution(@PathVariable String executionId) {
        return executionService.queryExecution(executionId);
    }
}
