package com.flowpilot.domain.gray.repository;

import com.flowpilot.domain.gray.model.GrayPolicy;

import java.util.Optional;

public interface GrayPolicyRepository {

    Optional<GrayPolicy> findActiveByRuleId(Long ruleId);

    GrayPolicy save(GrayPolicy policy);

    void disable(Long ruleId);
}
