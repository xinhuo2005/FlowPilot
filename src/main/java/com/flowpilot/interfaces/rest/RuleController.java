package com.flowpilot.interfaces.rest;

import com.flowpilot.application.CreateRuleCommand;
import com.flowpilot.application.CreateRuleVersionCommand;
import com.flowpilot.application.PublishApplicationService;
import com.flowpilot.application.RuleApplicationService;
import com.flowpilot.application.RuleDetailResponse;
import com.flowpilot.application.RuleVersionResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rules")
public class RuleController {

    private final RuleApplicationService ruleService;
    private final PublishApplicationService publishService;

    public RuleController(
            RuleApplicationService ruleService,
            PublishApplicationService publishService
    ) {
        this.ruleService = ruleService;
        this.publishService = publishService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedRuleResponse createRule(@Valid @RequestBody CreateRuleCommand command) {
        return new CreatedRuleResponse(ruleService.createRule(command));
    }

    @GetMapping("/{ruleCode}")
    public RuleDetailResponse getRule(@PathVariable String ruleCode) {
        return ruleService.getRule(ruleCode);
    }

    @PostMapping("/{ruleCode}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedVersionResponse createVersion(
            @PathVariable String ruleCode,
            @Valid @RequestBody CreateRuleVersionCommand command
    ) {
        return new CreatedVersionResponse(ruleService.createVersion(ruleCode, command));
    }

    @GetMapping("/{ruleCode}/versions")
    public List<RuleVersionResponse> listVersions(@PathVariable String ruleCode) {
        return ruleService.listVersions(ruleCode);
    }

    @PostMapping("/{ruleCode}/versions/{version}/publish")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void publish(
            @PathVariable String ruleCode,
            @PathVariable @Min(1) int version
    ) {
        publishService.publish(ruleCode, version);
    }

    @PostMapping("/{ruleCode}/rollback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rollback(
            @PathVariable String ruleCode,
            @Valid @RequestBody RollbackRequest request
    ) {
        publishService.rollback(ruleCode, request.targetVersion());
    }

    public record CreatedRuleResponse(Long id) {
    }

    public record CreatedVersionResponse(Integer version) {
    }

    public record RollbackRequest(@NotNull @Min(1) Integer targetVersion) {
    }
}
