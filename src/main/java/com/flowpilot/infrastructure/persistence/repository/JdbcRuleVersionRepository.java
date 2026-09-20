package com.flowpilot.infrastructure.persistence.repository;

import com.flowpilot.domain.rule.model.RuleVersion;
import com.flowpilot.domain.rule.model.RuleVersionStatus;
import com.flowpilot.domain.rule.repository.RuleVersionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcRuleVersionRepository implements RuleVersionRepository {

    private static final RowMapper<RuleVersion> ROW_MAPPER = (resultSet, rowNumber) ->
            new RuleVersion(
                    resultSet.getLong("id"),
                    resultSet.getLong("rule_id"),
                    resultSet.getInt("version"),
                    resultSet.getString("rule_content"),
                    RuleVersionStatus.valueOf(resultSet.getString("status")),
                    resultSet.getString("checksum"),
                    resultSet.getString("created_by"),
                    resultSet.getObject("created_at", java.time.LocalDateTime.class),
                    resultSet.getObject("published_at", java.time.LocalDateTime.class));

    private final JdbcTemplate jdbcTemplate;

    public JdbcRuleVersionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<RuleVersion> find(Long ruleId, Integer version) {
        return jdbcTemplate.query("""
                        SELECT id, rule_id, version, rule_content, status, checksum,
                               created_by, created_at, published_at
                        FROM rule_version
                        WHERE rule_id = ? AND version = ?
                        """, ROW_MAPPER, ruleId, version)
                .stream()
                .findFirst();
    }

    @Override
    public List<RuleVersion> findAll(Long ruleId) {
        return jdbcTemplate.query("""
                SELECT id, rule_id, version, rule_content, status, checksum,
                       created_by, created_at, published_at
                FROM rule_version
                WHERE rule_id = ?
                ORDER BY version
                """, ROW_MAPPER, ruleId);
    }

    @Override
    public RuleVersion save(RuleVersion version) {
        if (version.id() != null) {
            jdbcTemplate.update("""
                            UPDATE rule_version
                            SET rule_content = ?, status = ?, checksum = ?, created_by = ?
                            WHERE id = ?
                            """,
                    version.ruleContent(), version.status().name(), version.checksum(),
                    version.createdBy(), version.id());
            return find(version.ruleId(), version.version()).orElseThrow();
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO rule_version(rule_id, version, rule_content, status, checksum, created_by)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, version.ruleId());
            statement.setInt(2, version.version());
            statement.setString(3, version.ruleContent());
            statement.setString(4, version.status().name());
            statement.setString(5, version.checksum());
            statement.setString(6, version.createdBy());
            return statement;
        }, keyHolder);

        if (keyHolder.getKey() == null) {
            throw new IllegalStateException("database did not return a rule version id");
        }
        return find(version.ruleId(), version.version()).orElseThrow();
    }

    @Override
    public boolean changeStatus(
            Long ruleId,
            Integer version,
            RuleVersionStatus expectedStatus,
            RuleVersionStatus targetStatus
    ) {
        int affectedRows = jdbcTemplate.update("""
                UPDATE rule_version
                SET status = ?,
                    published_at = CASE WHEN ? = 'PUBLISHED' THEN CURRENT_TIMESTAMP ELSE published_at END
                WHERE rule_id = ? AND version = ? AND status = ?
                """,
                targetStatus.name(), targetStatus.name(), ruleId, version, expectedStatus.name());
        return affectedRows == 1;
    }
}
