package com.flowpilot.infrastructure.persistence.repository;

import com.flowpilot.domain.execution.model.ExecutionStatus;
import com.flowpilot.domain.execution.model.FlowExecution;
import com.flowpilot.domain.execution.repository.FlowExecutionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class JdbcFlowExecutionRepository implements FlowExecutionRepository {

    private static final RowMapper<FlowExecution> ROW_MAPPER = (resultSet, rowNumber) ->
            new FlowExecution(
                    resultSet.getLong("id"),
                    resultSet.getString("execution_id"),
                    resultSet.getString("rule_code"),
                    resultSet.getInt("rule_version"),
                    resultSet.getString("routing_key"),
                    ExecutionStatus.valueOf(resultSet.getString("status")),
                    resultSet.getObject("start_time", java.time.LocalDateTime.class),
                    resultSet.getObject("end_time", java.time.LocalDateTime.class),
                    resultSet.getObject("duration_ms", Long.class),
                    resultSet.getString("error_code"),
                    resultSet.getString("error_message"),
                    resultSet.getObject("created_at", java.time.LocalDateTime.class));

    private final JdbcTemplate jdbcTemplate;

    public JdbcFlowExecutionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void create(FlowExecution execution) {
        jdbcTemplate.update("""
                INSERT INTO flow_execution(
                    execution_id, rule_code, rule_version, routing_key, status, start_time)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                execution.executionId(), execution.ruleCode(), execution.ruleVersion(),
                execution.routingKey(), execution.status().name(), execution.startTime());
    }

    @Override
    public void markSuccess(String executionId, long durationMs) {
        requireSingleUpdate(jdbcTemplate.update("""
                UPDATE flow_execution
                SET status = 'SUCCESS', end_time = CURRENT_TIMESTAMP,
                    duration_ms = ?, error_code = NULL, error_message = NULL
                WHERE execution_id = ? AND status = 'RUNNING'
                """, durationMs, executionId), executionId);
    }

    @Override
    public void markFailed(String executionId, long durationMs, String errorMessage) {
        requireSingleUpdate(jdbcTemplate.update("""
                UPDATE flow_execution
                SET status = 'FAILED', end_time = CURRENT_TIMESTAMP,
                    duration_ms = ?, error_code = 'RULE_EXECUTION_FAILED', error_message = ?
                WHERE execution_id = ? AND status = 'RUNNING'
                """, durationMs, truncate(errorMessage, 1000), executionId), executionId);
    }

    @Override
    public Optional<FlowExecution> findByExecutionId(String executionId) {
        return jdbcTemplate.query("""
                        SELECT id, execution_id, rule_code, rule_version, routing_key, status,
                               start_time, end_time, duration_ms, error_code, error_message, created_at
                        FROM flow_execution
                        WHERE execution_id = ?
                        """, ROW_MAPPER, executionId)
                .stream()
                .findFirst();
    }

    @Override
    public int deleteOlderThan(LocalDateTime cutoff, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 5_000));
        List<String> executionIds = jdbcTemplate.query(
                """
                SELECT execution_id
                FROM flow_execution
                WHERE created_at < ?
                ORDER BY id
                LIMIT ?
                """,
                (resultSet, rowNum) -> resultSet.getString("execution_id"),
                cutoff, safeLimit);
        int deleted = 0;
        for (String executionId : executionIds) {
            jdbcTemplate.update("DELETE FROM flow_execution_node WHERE execution_id = ?", executionId);
            deleted += jdbcTemplate.update("DELETE FROM flow_execution WHERE execution_id = ?", executionId);
        }
        return deleted;
    }

    private static void requireSingleUpdate(int affectedRows, String executionId) {
        if (affectedRows != 1) {
            throw new IllegalStateException("Execution trace state changed unexpectedly: " + executionId);
        }
    }

    private static String truncate(String value, int maximumLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }
}
