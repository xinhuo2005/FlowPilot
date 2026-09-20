package com.flowpilot.application;

import com.flowpilot.domain.rule.service.RulePublisher;
import org.springframework.stereotype.Service;

@Service
public class PublishApplicationService {

    private final RulePublisher rulePublisher;

    public PublishApplicationService(RulePublisher rulePublisher) {
        this.rulePublisher = rulePublisher;
    }

    public void publish(String ruleCode, int version) {
        rulePublisher.publish(ruleCode, version);
    }

    public void rollback(String ruleCode, int targetVersion) {
        rulePublisher.rollback(ruleCode, targetVersion);
    }
}
