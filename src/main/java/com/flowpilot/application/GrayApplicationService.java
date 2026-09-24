package com.flowpilot.application;

import com.flowpilot.domain.gray.model.GrayPolicy;
import com.flowpilot.domain.gray.service.GrayManager;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class GrayApplicationService {

    private final GrayManager grayManager;
    private final OperationAuditService operationAuditService;

    public GrayApplicationService(
            GrayManager grayManager,
            OperationAuditService operationAuditService
    ) {
        this.grayManager = grayManager;
        this.operationAuditService = operationAuditService;
    }

    public void startGray(String ruleCode, StartGrayCommand command) {
        startGray(ruleCode, command, null, "system", "RULE_ADMIN");
    }

    public void startGray(
            String ruleCode,
            StartGrayCommand command,
            String operationId,
            String operator,
            String roles
    ) {
        Objects.requireNonNull(command, "command must not be null");
        operationAuditService.execute(
                OperationRequest.of(
                        operationId, ruleCode, "START_GRAY", command.grayVersion(), operator, roles,
                        "percentage=" + command.percentage()),
                () -> grayManager.start(ruleCode, command.grayVersion(), command.percentage()));
    }

    public void updatePercentage(String ruleCode, int percentage) {
        updatePercentage(ruleCode, percentage, null, "system", "RULE_ADMIN");
    }

    public void updatePercentage(
            String ruleCode,
            int percentage,
            String operationId,
            String operator,
            String roles
    ) {
        operationAuditService.execute(
                OperationRequest.of(
                        operationId, ruleCode, "UPDATE_GRAY", null, operator, roles,
                        "percentage=" + percentage),
                () -> grayManager.updatePercentage(ruleCode, percentage));
    }

    public void stopGray(String ruleCode) {
        stopGray(ruleCode, null, "system", "RULE_ADMIN");
    }

    public void stopGray(
            String ruleCode,
            String operationId,
            String operator,
            String roles
    ) {
        operationAuditService.execute(
                OperationRequest.of(operationId, ruleCode, "STOP_GRAY", null, operator, roles),
                () -> grayManager.stop(ruleCode));
    }

    public void promote(String ruleCode) {
        promote(ruleCode, null, "system", "RULE_ADMIN");
    }

    public void promote(
            String ruleCode,
            String operationId,
            String operator,
            String roles
    ) {
        operationAuditService.execute(
                OperationRequest.of(operationId, ruleCode, "PROMOTE_GRAY", null, operator, roles),
                () -> grayManager.promote(ruleCode));
    }

    public GrayPolicy getPolicy(String ruleCode) {
        return grayManager.getActivePolicy(ruleCode);
    }
}
