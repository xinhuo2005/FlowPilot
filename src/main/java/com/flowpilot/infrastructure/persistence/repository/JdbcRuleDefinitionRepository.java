package com.flowpilot.infrastructure.persistence.repository;

import com.flowpilot.domain.rule.model.RuleDefinition;
import com.flowpilot.domain.rule.model.RuleStatus;
import com.flowpilot.domain.rule.repository.RuleDefinitionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.Optional;

@Repository
public class JdbcRuleDefinitionRepository implements RuleDefinitionRepository {

    private static final RowMapper<RuleDefinition> ROW_MAPPER = (resultSet, rowNumber) ->
            new RuleDefinition(
                    resultSet.getLong("id"),
                    resultSet.getString("rule_code"),
                    resultSet.getString("rule_name"),
                    resultSet.getObject("current_version", Integer.class),
                    RuleStatus.valueOf(resultSet.getString("status")),
                    resultSet.getString("description"),
                    resultSet.getObject("created_at", java.time.LocalDateTime.class),
                    resultSet.getObject("updated_at", java.time.LocalDateTime.class));

    private final JdbcTemplate jdbcTemplate;

    public JdbcRuleDefinitionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<RuleDefinition> findByCode(String ruleCode) {
        return jdbcTemplate.query("""
                        SELECT id, rule_code, rule_name, current_version, status,
                               description, created_at, updated_at
                        FROM rule_definition
                        WHERE rule_code = ?
                        """, ROW_MAPPER, ruleCode)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<RuleDefinition> findByCodeForUpdate(String ruleCode) {
        return jdbcTemplate.query("""
                        SELECT id, rule_code, rule_name, current_version, status,
                               description, created_at, updated_at
                        FROM rule_definition
                        WHERE rule_code = ?
                        FOR UPDATE
                        """, ROW_MAPPER, ruleCode)
                .stream()
                .findFirst();
    }

    @Override
    public RuleDefinition save(RuleDefinition definition) {
        if (definition.id() != null) {
            jdbcTemplate.update("""
                            UPDATE rule_definition
                            SET rule_name = ?, status = ?, description = ?, updated_at = CURRENT_TIMESTAMP
                            WHERE id = ?
                            """,
                    definition.ruleName(), definition.status().name(),
                    definition.description(), definition.id());
            return findByCode(definition.ruleCode()).orElseThrow();
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO rule_definition(rule_code, rule_name, current_version, status, description)
                    VALUES (?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setString(1, definition.ruleCode());
            statement.setString(2, definition.ruleName());
            statement.setObject(3, definition.currentVersion());
            statement.setString(4, definition.status().name());
            statement.setString(5, definition.description());
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("database did not return a rule id");
        }
        return findByCode(definition.ruleCode()).orElseThrow();
    }

    @Override
    public boolean updateCurrentVersion(Long ruleId, Integer expectedVersion, Integer newVersion) {
        int affectedRows;
        if (expectedVersion == null) {
            affectedRows = jdbcTemplate.update("""
                    UPDATE rule_definition
                    SET current_version = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND current_version IS NULL
                    """, newVersion, ruleId);
        } else {
            affectedRows = jdbcTemplate.update("""
                    UPDATE rule_definition
                    SET current_version = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND current_version = ?
                    """, newVersion, ruleId, expectedVersion);
        }
        return affectedRows == 1;
    }
}
