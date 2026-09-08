package com.flowpilot.domain.rule.service;

import com.flowpilot.cache.RuleCache;
import com.flowpilot.domain.gray.model.GrayPolicy;
import com.flowpilot.domain.gray.repository.GrayPolicyRepository;
import com.flowpilot.domain.gray.service.GrayRouter;
import com.flowpilot.domain.rule.model.RuleDefinition;
import com.flowpilot.domain.rule.model.RuleSnapshot;
import com.flowpilot.domain.rule.model.RuleStatus;
import com.flowpilot.domain.rule.model.RuleVersion;
import com.flowpilot.domain.rule.model.RuleVersionStatus;
import com.flowpilot.domain.rule.repository.RuleDefinitionRepository;
import com.flowpilot.domain.rule.repository.RuleVersionRepository;
import com.flowpilot.exception.IllegalRuleStateException;
import com.flowpilot.exception.RuleNotFoundException;
import com.flowpilot.exception.RuleVersionNotFoundException;

import java.util.Objects;

public final class DefaultRuleResolver implements RuleResolver {

    private final RuleDefinitionRepository ruleDefinitionRepository;
    private final RuleVersionRepository ruleVersionRepository;
    private final GrayPolicyRepository grayPolicyRepository;
    private final GrayRouter grayRouter;
    private final RuleCache ruleCache;

    public DefaultRuleResolver(
            RuleDefinitionRepository ruleDefinitionRepository,
            RuleVersionRepository ruleVersionRepository,
            GrayPolicyRepository grayPolicyRepository,
            GrayRouter grayRouter,
            RuleCache ruleCache
    ) {
        this.ruleDefinitionRepository = Objects.requireNonNull(ruleDefinitionRepository);
        this.ruleVersionRepository = Objects.requireNonNull(ruleVersionRepository);
        this.grayPolicyRepository = Objects.requireNonNull(grayPolicyRepository);
        this.grayRouter = Objects.requireNonNull(grayRouter);
        this.ruleCache = Objects.requireNonNull(ruleCache);
    }

    @Override
    public RuleSnapshot resolve(String ruleCode, String routingKey) {
        requireText(ruleCode, "ruleCode");
        requireText(routingKey, "routingKey");

        RuleDefinition definition = ruleDefinitionRepository.findByCode(ruleCode)
                .orElseThrow(() -> new RuleNotFoundException(ruleCode));
        ensureExecutable(definition);

        int version = grayPolicyRepository.findActiveByRuleId(definition.id())
                .map(policy -> routeGray(definition, policy, routingKey))
                .orElse(definition.currentVersion());

        RuleSnapshot cachedSnapshot = ruleCache.get(ruleCode, version);
        if (cachedSnapshot != null) {
            return cachedSnapshot;
        }

        RuleVersion ruleVersion = ruleVersionRepository.find(definition.id(), version)
                .orElseThrow(() -> new RuleVersionNotFoundException(ruleCode, version));
        ensurePublished(ruleCode, ruleVersion);

        RuleSnapshot snapshot = new RuleSnapshot(
                definition.id(),
                definition.ruleCode(),
                ruleVersion.version(),
                ruleVersion.ruleContent(),
                ruleVersion.checksum()
        );
        ruleCache.put(snapshot);
        return snapshot;
    }

    private int routeGray(RuleDefinition definition, GrayPolicy policy, String routingKey) {
        if (!definition.currentVersion().equals(policy.baseVersion())) {
            throw new IllegalRuleStateException(
                    "Active gray policy base version does not match current version for rule: "
                            + definition.ruleCode()
            );
        }
        return grayRouter.route(
                routingKey,
                policy.baseVersion(),
                policy.grayVersion(),
                policy.percentage()
        );
    }

    private static void ensureExecutable(RuleDefinition definition) {
        if (definition.id() == null) {
            throw new IllegalRuleStateException("Persisted rule must have an id: " + definition.ruleCode());
        }
        if (definition.status() != RuleStatus.ENABLED) {
            throw new IllegalRuleStateException("Rule is disabled: " + definition.ruleCode());
        }
        if (definition.currentVersion() == null) {
            throw new IllegalRuleStateException("Rule has no published version: " + definition.ruleCode());
        }
    }

    private static void ensurePublished(String ruleCode, RuleVersion ruleVersion) {
        if (ruleVersion.status() != RuleVersionStatus.PUBLISHED) {
            throw new IllegalRuleStateException(
                    "Resolved rule version is not published: " + ruleCode + "@" + ruleVersion.version()
            );
        }
        if (ruleVersion.checksum() == null || ruleVersion.checksum().isBlank()) {
            throw new IllegalRuleStateException(
                    "Published rule version has no checksum: " + ruleCode + "@" + ruleVersion.version()
            );
        }
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
