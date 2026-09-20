package com.flowpilot.domain.rule.service;

import com.flowpilot.cache.RuleCache;
import com.flowpilot.domain.rule.model.RuleDefinition;
import com.flowpilot.domain.rule.model.RuleSnapshot;
import com.flowpilot.domain.rule.model.RuleVersion;
import com.flowpilot.domain.rule.model.RuleVersionStatus;
import com.flowpilot.domain.rule.model.ValidationResult;
import com.flowpilot.domain.rule.repository.RuleDefinitionRepository;
import com.flowpilot.domain.rule.repository.RuleVersionRepository;
import com.flowpilot.exception.IllegalRuleStateException;
import com.flowpilot.exception.RuleNotFoundException;
import com.flowpilot.exception.RuleValidationException;
import com.flowpilot.exception.RuleVersionNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DefaultRulePublisher implements RulePublisher {

    private final RuleDefinitionRepository definitionRepository;
    private final RuleVersionRepository versionRepository;
    private final RuleValidator ruleValidator;
    private final RuleLoader ruleLoader;
    private final RuleStateTransitionService transitionService;
    private final RuleCache ruleCache;

    public DefaultRulePublisher(
            RuleDefinitionRepository definitionRepository,
            RuleVersionRepository versionRepository,
            RuleValidator ruleValidator,
            RuleLoader ruleLoader,
            RuleStateTransitionService transitionService,
            RuleCache ruleCache
    ) {
        this.definitionRepository = definitionRepository;
        this.versionRepository = versionRepository;
        this.ruleValidator = ruleValidator;
        this.ruleLoader = ruleLoader;
        this.transitionService = transitionService;
        this.ruleCache = ruleCache;
    }

    @Override
    public void publish(String ruleCode, int version) {
        PreparedVersion prepared = prepare(ruleCode, version, RuleVersionStatus.DRAFT);
        transitionService.publish(ruleCode, version);
        ruleCache.evictAll(prepared.definition().ruleCode());
    }

    @Override
    public void rollback(String ruleCode, int targetVersion) {
        PreparedVersion prepared = prepare(ruleCode, targetVersion, RuleVersionStatus.ARCHIVED);
        transitionService.rollback(ruleCode, targetVersion);
        ruleCache.evictAll(prepared.definition().ruleCode());
    }

    private PreparedVersion prepare(
            String ruleCode,
            int version,
            RuleVersionStatus expectedStatus
    ) {
        requireText(ruleCode, "ruleCode");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        RuleDefinition definition = definitionRepository.findByCode(ruleCode)
                .orElseThrow(() -> new RuleNotFoundException(ruleCode));
        RuleVersion ruleVersion = versionRepository.find(definition.id(), version)
                .orElseThrow(() -> new RuleVersionNotFoundException(ruleCode, version));
        if (ruleVersion.status() != expectedStatus) {
            throw new IllegalRuleStateException(
                    "Expected version " + version + " to be " + expectedStatus
                            + " but was " + ruleVersion.status());
        }
        if (ruleVersion.checksum() == null || ruleVersion.checksum().isBlank()) {
            throw new IllegalRuleStateException(
                    "Rule version has no checksum: " + ruleCode + "@" + version);
        }

        ValidationResult validation = ruleValidator.validate(ruleCode, ruleVersion.ruleContent());
        if (!validation.valid()) {
            throw new RuleValidationException(validation.errors());
        }
        ruleLoader.load(toSnapshot(definition, ruleVersion));
        return new PreparedVersion(definition, ruleVersion);
    }

    private RuleSnapshot toSnapshot(RuleDefinition definition, RuleVersion version) {
        return new RuleSnapshot(
                definition.id(), definition.ruleCode(), version.version(),
                version.ruleContent(), version.checksum());
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    private record PreparedVersion(RuleDefinition definition, RuleVersion version) {
    }
}
