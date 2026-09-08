package com.flowpilot.domain.rule.repository;

import com.flowpilot.domain.rule.model.RuleVersion;
import com.flowpilot.domain.rule.model.RuleVersionStatus;

import java.util.List;
import java.util.Optional;

public interface RuleVersionRepository {

    Optional<RuleVersion> find(Long ruleId, Integer version);

    List<RuleVersion> findAll(Long ruleId);

    RuleVersion save(RuleVersion version);

    boolean changeStatus(
            Long ruleId,
            Integer version,
            RuleVersionStatus expectedStatus,
            RuleVersionStatus targetStatus
    );
}
