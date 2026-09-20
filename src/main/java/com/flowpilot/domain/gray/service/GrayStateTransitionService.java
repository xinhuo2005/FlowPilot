package com.flowpilot.domain.gray.service;

import com.flowpilot.domain.gray.model.GrayPolicy;
import com.flowpilot.domain.gray.model.GrayPolicyStatus;
import com.flowpilot.domain.gray.repository.GrayPolicyRepository;
import com.flowpilot.domain.rule.model.RuleDefinition;
import com.flowpilot.domain.rule.model.RuleStatus;
import com.flowpilot.domain.rule.model.RuleVersion;
import com.flowpilot.domain.rule.model.RuleVersionStatus;
import com.flowpilot.domain.rule.repository.RuleDefinitionRepository;
import com.flowpilot.domain.rule.repository.RuleVersionRepository;
import com.flowpilot.exception.IllegalRuleStateException;
import com.flowpilot.exception.RuleNotFoundException;
import com.flowpilot.exception.RuleVersionNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GrayStateTransitionService {

    private final RuleDefinitionRepository definitionRepository;
    private final RuleVersionRepository versionRepository;
    private final GrayPolicyRepository grayPolicyRepository;

    public GrayStateTransitionService(
            RuleDefinitionRepository definitionRepository,
            RuleVersionRepository versionRepository,
            GrayPolicyRepository grayPolicyRepository
    ) {
        this.definitionRepository = definitionRepository;
        this.versionRepository = versionRepository;
        this.grayPolicyRepository = grayPolicyRepository;
    }

    @Transactional
    public void start(String ruleCode, int grayVersion, int percentage) {
        RuleDefinition definition = lockExecutableRule(ruleCode);
        if (definition.currentVersion().equals(grayVersion)) {
            throw new IllegalRuleStateException("Gray version cannot equal current version: " + ruleCode);
        }
        if (grayPolicyRepository.findActiveByRuleId(definition.id()).isPresent()) {
            throw new IllegalRuleStateException("Rule already has an active gray policy: " + ruleCode);
        }

        RuleVersion base = findVersion(definition, definition.currentVersion());
        RuleVersion gray = findVersion(definition, grayVersion);
        requireStatus(base, RuleVersionStatus.PUBLISHED, "use as gray base");
        requireStatus(gray, RuleVersionStatus.DRAFT, "start gray");

        changeStatus(definition.id(), grayVersion,
                RuleVersionStatus.DRAFT, RuleVersionStatus.PUBLISHED);
        GrayPolicy policy = new GrayPolicy(
                null, definition.id(), definition.currentVersion(), grayVersion,
                percentage, GrayPolicyStatus.ACTIVE, null, null);
        if (!grayPolicyRepository.activate(policy)) {
            throw new IllegalRuleStateException("Concurrent gray activation detected: " + ruleCode);
        }
    }

    @Transactional
    public void updatePercentage(String ruleCode, int percentage) {
        RuleDefinition definition = lockExecutableRule(ruleCode);
        GrayPolicy policy = activePolicy(definition);
        if (!grayPolicyRepository.updatePercentage(
                definition.id(), policy.percentage(), percentage)) {
            throw new IllegalRuleStateException("Concurrent gray percentage change detected: " + ruleCode);
        }
    }

    @Transactional
    public void stop(String ruleCode) {
        RuleDefinition definition = lockExecutableRule(ruleCode);
        GrayPolicy policy = activePolicy(definition);
        ensurePolicyMatchesCurrent(definition, policy);

        changeStatus(definition.id(), policy.grayVersion(),
                RuleVersionStatus.PUBLISHED, RuleVersionStatus.ARCHIVED);
        disablePolicy(definition, policy);
    }

    @Transactional
    public void promote(String ruleCode) {
        RuleDefinition definition = lockExecutableRule(ruleCode);
        GrayPolicy policy = activePolicy(definition);
        ensurePolicyMatchesCurrent(definition, policy);
        RuleVersion base = findVersion(definition, policy.baseVersion());
        RuleVersion gray = findVersion(definition, policy.grayVersion());
        requireStatus(base, RuleVersionStatus.PUBLISHED, "archive gray base");
        requireStatus(gray, RuleVersionStatus.PUBLISHED, "promote gray version");

        if (!definitionRepository.updateCurrentVersion(
                definition.id(), policy.baseVersion(), policy.grayVersion())) {
            throw new IllegalRuleStateException("Concurrent current version change detected: " + ruleCode);
        }
        changeStatus(definition.id(), policy.baseVersion(),
                RuleVersionStatus.PUBLISHED, RuleVersionStatus.ARCHIVED);
        disablePolicy(definition, policy);
    }

    private RuleDefinition lockExecutableRule(String ruleCode) {
        RuleDefinition definition = definitionRepository.findByCodeForUpdate(ruleCode)
                .orElseThrow(() -> new RuleNotFoundException(ruleCode));
        if (definition.status() != RuleStatus.ENABLED) {
            throw new IllegalRuleStateException("Rule is disabled: " + ruleCode);
        }
        if (definition.currentVersion() == null) {
            throw new IllegalRuleStateException("Rule has no published version: " + ruleCode);
        }
        return definition;
    }

    private GrayPolicy activePolicy(RuleDefinition definition) {
        return grayPolicyRepository.findActiveByRuleId(definition.id())
                .orElseThrow(() -> new IllegalRuleStateException(
                        "Rule has no active gray policy: " + definition.ruleCode()));
    }

    private RuleVersion findVersion(RuleDefinition definition, int version) {
        return versionRepository.find(definition.id(), version)
                .orElseThrow(() -> new RuleVersionNotFoundException(definition.ruleCode(), version));
    }

    private void ensurePolicyMatchesCurrent(RuleDefinition definition, GrayPolicy policy) {
        if (!definition.currentVersion().equals(policy.baseVersion())) {
            throw new IllegalRuleStateException(
                    "Gray base version does not match current version: " + definition.ruleCode());
        }
    }

    private void requireStatus(RuleVersion version, RuleVersionStatus status, String action) {
        if (version.status() != status) {
            throw new IllegalRuleStateException(
                    "Cannot " + action + " version " + version.version()
                            + " from status " + version.status());
        }
    }

    private void changeStatus(
            Long ruleId,
            int version,
            RuleVersionStatus expected,
            RuleVersionStatus target
    ) {
        if (!versionRepository.changeStatus(ruleId, version, expected, target)) {
            throw new IllegalRuleStateException(
                    "Concurrent rule version state change detected: " + ruleId + "@" + version);
        }
    }

    private void disablePolicy(RuleDefinition definition, GrayPolicy policy) {
        if (!grayPolicyRepository.disable(
                definition.id(), policy.baseVersion(), policy.grayVersion())) {
            throw new IllegalRuleStateException(
                    "Concurrent gray policy state change detected: " + definition.ruleCode());
        }
    }
}
