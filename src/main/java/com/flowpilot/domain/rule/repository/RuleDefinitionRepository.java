package com.flowpilot.domain.rule.repository;

import com.flowpilot.domain.rule.model.RuleDefinition;

import java.util.Optional;

public interface RuleDefinitionRepository {

    Optional<RuleDefinition> findByCode(String ruleCode);

    Optional<RuleDefinition> findByCodeForUpdate(String ruleCode);

    RuleDefinition save(RuleDefinition definition);

    /**
     * Atomically changes the stable version. A null expectedVersion represents the first publication
     * and must be implemented with an {@code IS NULL} database predicate.
     */
    boolean updateCurrentVersion(Long ruleId, Integer expectedVersion, Integer newVersion);
}
