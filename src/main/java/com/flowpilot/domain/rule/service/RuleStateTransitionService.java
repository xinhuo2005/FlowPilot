package com.flowpilot.domain.rule.service;

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
public class RuleStateTransitionService {

    private final RuleDefinitionRepository definitionRepository;
    private final RuleVersionRepository versionRepository;
    private final GrayPolicyRepository grayPolicyRepository;

    public RuleStateTransitionService(
            RuleDefinitionRepository definitionRepository,
            RuleVersionRepository versionRepository,
            GrayPolicyRepository grayPolicyRepository
    ) {
        this.definitionRepository = definitionRepository;
        this.versionRepository = versionRepository;
        this.grayPolicyRepository = grayPolicyRepository;
    }

    @Transactional
    public void publish(String ruleCode, int version) {
        RuleDefinition definition = lockRule(ruleCode);
        ensureNoActiveGray(definition);
        RuleVersion target = findVersion(definition, version);
        requireStatus(target, RuleVersionStatus.DRAFT, "publish");

        changeStatus(definition.id(), version, RuleVersionStatus.DRAFT, RuleVersionStatus.PUBLISHED);
        if (definition.currentVersion() != null) {
            RuleVersion current = findVersion(definition, definition.currentVersion());
            requireStatus(current, RuleVersionStatus.PUBLISHED, "archive current version");
            changeStatus(definition.id(), current.version(),
                    RuleVersionStatus.PUBLISHED, RuleVersionStatus.ARCHIVED);
        }
        changeCurrentVersion(definition, version);
    }

    @Transactional
    public void rollback(String ruleCode, int targetVersion) {
        RuleDefinition definition = lockRule(ruleCode);
        ensureNoActiveGray(definition);
        if (definition.currentVersion() == null) {
            throw new IllegalRuleStateException("Rule has no published version: " + ruleCode);
        }
        if (definition.currentVersion().equals(targetVersion)) {
            throw new IllegalRuleStateException("Target version is already current: " + ruleCode);
        }

        RuleVersion target = findVersion(definition, targetVersion);
        RuleVersion current = findVersion(definition, definition.currentVersion());
        requireStatus(target, RuleVersionStatus.ARCHIVED, "rollback");
        requireStatus(current, RuleVersionStatus.PUBLISHED, "archive current version");

        changeStatus(definition.id(), targetVersion,
                RuleVersionStatus.ARCHIVED, RuleVersionStatus.PUBLISHED);
        changeStatus(definition.id(), current.version(),
                RuleVersionStatus.PUBLISHED, RuleVersionStatus.ARCHIVED);
        changeCurrentVersion(definition, targetVersion);
    }

    private RuleDefinition lockRule(String ruleCode) {
        RuleDefinition definition = definitionRepository.findByCodeForUpdate(ruleCode)
                .orElseThrow(() -> new RuleNotFoundException(ruleCode));
        if (definition.status() != RuleStatus.ENABLED) {
            throw new IllegalRuleStateException("Rule is disabled: " + ruleCode);
        }
        return definition;
    }

    private RuleVersion findVersion(RuleDefinition definition, int version) {
        return versionRepository.find(definition.id(), version)
                .orElseThrow(() -> new RuleVersionNotFoundException(definition.ruleCode(), version));
    }

    private void ensureNoActiveGray(RuleDefinition definition) {
        if (grayPolicyRepository.findActiveByRuleId(definition.id()).isPresent()) {
            throw new IllegalRuleStateException(
                    "Rule has an active gray policy: " + definition.ruleCode());
        }
    }

    private void requireStatus(
            RuleVersion version,
            RuleVersionStatus expected,
            String action
    ) {
        if (version.status() != expected) {
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

    private void changeCurrentVersion(RuleDefinition definition, int newVersion) {
        if (!definitionRepository.updateCurrentVersion(
                definition.id(), definition.currentVersion(), newVersion)) {
            throw new IllegalRuleStateException(
                    "Concurrent current version change detected: " + definition.ruleCode());
        }
    }
}
