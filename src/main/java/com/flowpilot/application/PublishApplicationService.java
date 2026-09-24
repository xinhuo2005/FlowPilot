package com.flowpilot.application;

import com.flowpilot.domain.rule.service.RulePublisher;
import org.springframework.stereotype.Service;

@Service
public class PublishApplicationService {

    private final RulePublisher rulePublisher;
    private final OperationAuditService operationAuditService;

    public PublishApplicationService(
            RulePublisher rulePublisher,
            OperationAuditService operationAuditService
    ) {
        this.rulePublisher = rulePublisher;
        this.operationAuditService = operationAuditService;
    }

    public void publish(String ruleCode, int version) {
        publish(ruleCode, version, null, "system", "RULE_ADMIN");
    }

    public void publish(
            String ruleCode,
            int version,
            String operationId,
            String operator,
            String roles
    ) {
        operationAuditService.execute(
                OperationRequest.of(operationId, ruleCode, "PUBLISH", version, operator, roles),
                () -> rulePublisher.publish(ruleCode, version));
    }

    public void rollback(String ruleCode, int targetVersion) {
        rollback(ruleCode, targetVersion, null, "system", "RULE_ADMIN");
    }

    public void rollback(
            String ruleCode,
            int targetVersion,
            String operationId,
            String operator,
            String roles
    ) {
        operationAuditService.execute(
                OperationRequest.of(
                        operationId, ruleCode, "ROLLBACK", targetVersion, operator, roles),
                () -> rulePublisher.rollback(ruleCode, targetVersion));
    }
}
