package com.flowpilot.interfaces.rest;

import com.flowpilot.application.FlowExecuteCommand;
import com.flowpilot.application.FlowExecuteResponse;
import com.flowpilot.application.FlowExecutionApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flows")
public class FlowExecutionController {

    private final FlowExecutionApplicationService executionService;

    public FlowExecutionController(FlowExecutionApplicationService executionService) {
        this.executionService = executionService;
    }

    @PostMapping("/{ruleCode}/execute")
    public FlowExecuteResponse execute(
            @PathVariable String ruleCode,
            @Valid @RequestBody FlowExecuteCommand command
    ) {
        return executionService.execute(ruleCode, command);
    }
}
