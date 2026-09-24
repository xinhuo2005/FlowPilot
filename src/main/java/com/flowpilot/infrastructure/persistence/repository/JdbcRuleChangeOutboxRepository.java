package com.flowpilot.infrastructure.persistence.repository;

import com.flowpilot.domain.rule.model.RuleChangeEvent;
import com.flowpilot.domain.rule.repository.RuleChangeOutboxRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class JdbcRuleChangeOutboxRepository implements RuleChangeOutboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcRuleChangeOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void append(RuleChangeEvent event) {
        jdbcTemplate.update(
                """
                INSERT INTO rule_change_outbox
                    (event_id, rule_code, rule_id, event_type, aggregate_version, payload)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                event.eventId(), event.ruleCode(), event.ruleId(), event.eventType(),
                event.aggregateVersion(), event.payload());
    }

    @Override
    public List<OutboxRecord> findPending(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return jdbcTemplate.query(
                """
                SELECT id, event_id, rule_code, rule_id, event_type, aggregate_version,
                       payload, status, attempts, available_at
                FROM rule_change_outbox
                WHERE status = 'PENDING' AND available_at <= CURRENT_TIMESTAMP
                ORDER BY id
                LIMIT ?
                """,
                (resultSet, rowNum) -> new OutboxRecord(
                        resultSet.getLong("id"),
                        new RuleChangeEvent(
                                resultSet.getString("event_id"),
                                resultSet.getString("rule_code"),
                                resultSet.getLong("rule_id"),
                                resultSet.getString("event_type"),
                                (Integer) resultSet.getObject("aggregate_version"),
                                resultSet.getString("payload")),
                        resultSet.getString("status"),
                        resultSet.getInt("attempts"),
                        resultSet.getTimestamp("available_at").toLocalDateTime()),
                safeLimit);
    }

    @Override
    public boolean claim(Long id) {
        return jdbcTemplate.update(
                """
                UPDATE rule_change_outbox
                SET status = 'PROCESSING', updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'PENDING'
                """,
                id) == 1;
    }

    @Override
    public void markProcessed(Long id) {
        jdbcTemplate.update(
                """
                UPDATE rule_change_outbox
                SET status = 'PROCESSED', processed_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'PROCESSING'
                """,
                id);
    }

    @Override
    public void markRetry(Long id, String error, LocalDateTime availableAt) {
        jdbcTemplate.update(
                """
                UPDATE rule_change_outbox
                SET status = 'PENDING', attempts = attempts + 1,
                    available_at = ?, updated_at = CURRENT_TIMESTAMP, last_error = ?
                WHERE id = ? AND status = 'PROCESSING'
                """,
                Timestamp.valueOf(availableAt), truncate(error), id);
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
