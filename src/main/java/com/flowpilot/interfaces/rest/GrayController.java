package com.flowpilot.interfaces.rest;

import com.flowpilot.application.GrayApplicationService;
import com.flowpilot.application.StartGrayCommand;
import com.flowpilot.domain.gray.model.GrayPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rules/{ruleCode}/gray")
public class GrayController {

    private final GrayApplicationService grayService;

    public GrayController(GrayApplicationService grayService) {
        this.grayService = grayService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void startGray(
            @PathVariable String ruleCode,
            @Valid @RequestBody StartGrayCommand command
    ) {
        grayService.startGray(ruleCode, command);
    }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updatePercentage(
            @PathVariable String ruleCode,
            @Valid @RequestBody UpdatePercentageRequest request
    ) {
        grayService.updatePercentage(ruleCode, request.percentage());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stopGray(@PathVariable String ruleCode) {
        grayService.stopGray(ruleCode);
    }

    @PostMapping("/promote")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void promote(@PathVariable String ruleCode) {
        grayService.promote(ruleCode);
    }

    @GetMapping
    public GrayPolicy getPolicy(@PathVariable String ruleCode) {
        return grayService.getPolicy(ruleCode);
    }

    public record UpdatePercentageRequest(@NotNull @Min(1) @Max(99) Integer percentage) {
    }
}
