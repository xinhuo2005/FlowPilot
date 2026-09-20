package com.flowpilot.infrastructure.persistence.repository;

import com.flowpilot.domain.gray.model.GrayPolicy;
import com.flowpilot.domain.gray.model.GrayPolicyStatus;
import com.flowpilot.domain.gray.repository.GrayPolicyRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JdbcGrayPolicyRepository implements GrayPolicyRepository {

    private static final RowMapper<GrayPolicy> ROW_MAPPER = (resultSet, rowNumber) ->
            new GrayPolicy(
                    resultSet.getLong("id"),
                    resultSet.getLong("rule_id"),
                    resultSet.getInt("base_version"),
                    resultSet.getInt("gray_version"),
                    resultSet.getInt("percentage"),
                    GrayPolicyStatus.valueOf(resultSet.getString("status")),
                    resultSet.getObject("created_at", java.time.LocalDateTime.class),
                    resultSet.getObject("updated_at", java.time.LocalDateTime.class));

    private final JdbcTemplate jdbcTemplate;

    public JdbcGrayPolicyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<GrayPolicy> findActiveByRuleId(Long ruleId) {
        return jdbcTemplate.query("""
                        SELECT id, rule_id, base_version, gray_version, percentage,
                               status, created_at, updated_at
                        FROM rule_gray_policy
                        WHERE rule_id = ? AND status = 'ACTIVE'
                        """, ROW_MAPPER, ruleId)
                .stream()
                .findFirst();
    }

    @Override
    public GrayPolicy save(GrayPolicy policy) {
        int updated = jdbcTemplate.update("""
                UPDATE rule_gray_policy
                SET base_version = ?, gray_version = ?, percentage = ?, status = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE rule_id = ?
                """, policy.baseVersion(), policy.grayVersion(), policy.percentage(),
                policy.status().name(), policy.ruleId());
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO rule_gray_policy(
                        rule_id, base_version, gray_version, percentage, status)
                    VALUES (?, ?, ?, ?, ?)
                    """, policy.ruleId(), policy.baseVersion(), policy.grayVersion(),
                    policy.percentage(), policy.status().name());
        }
        return findActiveByRuleId(policy.ruleId()).orElse(policy);
    }

    @Override
    public boolean activate(GrayPolicy policy) {
        int updated = jdbcTemplate.update("""
                UPDATE rule_gray_policy
                SET base_version = ?, gray_version = ?, percentage = ?, status = 'ACTIVE',
                    updated_at = CURRENT_TIMESTAMP
                WHERE rule_id = ? AND status = 'DISABLED'
                """, policy.baseVersion(), policy.grayVersion(), policy.percentage(), policy.ruleId());
        if (updated == 1) {
            return true;
        }
        try {
            return jdbcTemplate.update("""
                    INSERT INTO rule_gray_policy(
                        rule_id, base_version, gray_version, percentage, status)
                    VALUES (?, ?, ?, ?, 'ACTIVE')
                    """, policy.ruleId(), policy.baseVersion(), policy.grayVersion(),
                    policy.percentage()) == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    public boolean updatePercentage(Long ruleId, Integer expectedPercentage, Integer newPercentage) {
        return jdbcTemplate.update("""
                UPDATE rule_gray_policy
                SET percentage = ?, updated_at = CURRENT_TIMESTAMP
                WHERE rule_id = ? AND status = 'ACTIVE' AND percentage = ?
                """, newPercentage, ruleId, expectedPercentage) == 1;
    }

    @Override
    public boolean disable(Long ruleId, Integer baseVersion, Integer grayVersion) {
        return jdbcTemplate.update("""
                UPDATE rule_gray_policy
                SET status = 'DISABLED', updated_at = CURRENT_TIMESTAMP
                WHERE rule_id = ? AND status = 'ACTIVE'
                  AND base_version = ? AND gray_version = ?
                """, ruleId, baseVersion, grayVersion) == 1;
    }
}
