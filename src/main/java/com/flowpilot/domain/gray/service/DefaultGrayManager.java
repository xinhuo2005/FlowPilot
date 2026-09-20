package com.flowpilot.domain.gray.service;

import com.flowpilot.cache.RuleCache;
import com.flowpilot.domain.gray.model.GrayPolicy;
import com.flowpilot.domain.gray.repository.GrayPolicyRepository;
import com.flowpilot.domain.rule.model.RuleDefinition;
import com.flowpilot.domain.rule.model.RuleSnapshot;
import com.flowpilot.domain.rule.model.RuleVersion;
import com.flowpilot.domain.rule.model.RuleVersionStatus;
import com.flowpilot.domain.rule.model.ValidationResult;
import com.flowpilot.domain.rule.repository.RuleDefinitionRepository;
import com.flowpilot.domain.rule.repository.RuleVersionRepository;
import com.flowpilot.domain.rule.service.RuleLoader;
import com.flowpilot.domain.rule.service.RuleValidator;
import com.flowpilot.exception.IllegalRuleStateException;
import com.flowpilot.exception.RuleNotFoundException;
import com.flowpilot.exception.RuleValidationException;
import com.flowpilot.exception.RuleVersionNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DefaultGrayManager implements GrayManager {

    private final RuleDefinitionRepository definitionRepository;
    private final RuleVersionRepository versionRepository;
    private final GrayPolicyRepository grayPolicyRepository;
    private final RuleValidator ruleValidator;
    private final RuleLoader ruleLoader;
    private final GrayStateTransitionService transitionService;
    private final RuleCache ruleCache;

    public DefaultGrayManager(
            RuleDefinitionRepository definitionRepository,
            RuleVersionRepository versionRepository,
            GrayPolicyRepository grayPolicyRepository,
            RuleValidator ruleValidator,
            RuleLoader ruleLoader,
            GrayStateTransitionService transitionService,
            RuleCache ruleCache
    ) {
        this.definitionRepository = definitionRepository;
        this.versionRepository = versionRepository;
        this.grayPolicyRepository = grayPolicyRepository;
        this.ruleValidator = ruleValidator;
        this.ruleLoader = ruleLoader;
        this.transitionService = transitionService;
        this.ruleCache = ruleCache;
    }

    @Override
    public void start(String ruleCode, int grayVersion, int percentage) {
        requirePercentage(percentage);
        RuleDefinition definition = findRule(ruleCode);
        if (definition.currentVersion() == null) {
            throw new IllegalRuleStateException("Rule has no published version: " + ruleCode);
        }
        RuleVersion version = versionRepository.find(definition.id(), grayVersion)
                .orElseThrow(() -> new RuleVersionNotFoundException(ruleCode, grayVersion));
        if (version.status() != RuleVersionStatus.DRAFT) {
            throw new IllegalRuleStateException(
                    "Gray version must be DRAFT: " + ruleCode + "@" + grayVersion);
        }
        if (version.checksum() == null || version.checksum().isBlank()) {
            throw new IllegalRuleStateException(
                    "Gray version has no checksum: " + ruleCode + "@" + grayVersion);
        }
        validateAndLoad(definition, version);
        transitionService.start(ruleCode, grayVersion, percentage);
        ruleCache.evictAll(ruleCode);
    }

    @Override
    public void updatePercentage(String ruleCode, int percentage) {
        requirePercentage(percentage);
        transitionService.updatePercentage(ruleCode, percentage);
        ruleCache.evictAll(ruleCode);
    }

    @Override
    public void stop(String ruleCode) {
        transitionService.stop(ruleCode);
        ruleCache.evictAll(ruleCode);
    }

    @Override
    public void promote(String ruleCode) {
        transitionService.promote(ruleCode);
        ruleCache.evictAll(ruleCode);
    }

    @Override
    public GrayPolicy getActivePolicy(String ruleCode) {
        RuleDefinition definition = findRule(ruleCode);
        return grayPolicyRepository.findActiveByRuleId(definition.id())
                .orElseThrow(() -> new IllegalRuleStateException(
                        "Rule has no active gray policy: " + ruleCode));
    }

    private RuleDefinition findRule(String ruleCode) {
        if (ruleCode == null || ruleCode.isBlank()) {
            throw new IllegalArgumentException("ruleCode must not be blank");
        }
        return definitionRepository.findByCode(ruleCode)
                .orElseThrow(() -> new RuleNotFoundException(ruleCode));
    }

    private void validateAndLoad(RuleDefinition definition, RuleVersion version) {
        ValidationResult validation = ruleValidator.validate(
                definition.ruleCode(), version.ruleContent());
        if (!validation.valid()) {
            throw new RuleValidationException(validation.errors());
        }
        ruleLoader.load(new RuleSnapshot(
                definition.id(), definition.ruleCode(), version.version(),
                version.ruleContent(), version.checksum()));
    }

    private void requirePercentage(int percentage) {
        if (percentage < 1 || percentage > 99) {
            throw new IllegalArgumentException("percentage must be between 1 and 99");
        }
    }
}
