package com.flowpilot.infrastructure.persistence.repository;

import com.flowpilot.domain.execution.model.NodeExecution;
import com.flowpilot.domain.execution.model.NodeExecutionStatus;
import com.flowpilot.domain.execution.repository.NodeExecutionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;

@Repository
public class JdbcNodeExecutionRepository implements NodeExecutionRepository {

    private static final RowMapper<NodeExecution> ROW_MAPPER = (resultSet, rowNumber) ->
            new NodeExecution(
                    resultSet.getLong("id"),
                    resultSet.getString("execution_id"),
                    resultSet.getString("node_id"),
                    NodeExecutionStatus.valueOf(resultSet.getString("status")),
                    resultSet.getObject("start_time", java.time.LocalDateTime.class),
                    resultSet.getObject("end_time", java.time.LocalDateTime.class),
                    resultSet.getObject("duration_ms", Long.class),
                    resultSet.getString("error_message"),
                    resultSet.getObject("created_at", java.time.LocalDateTime.class));

    private final JdbcTemplate jdbcTemplate;

    public JdbcNodeExecutionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long create(NodeExecution execution) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO flow_execution_node(execution_id, node_id, status, start_time)
                    VALUES (?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setString(1, execution.executionId());
            statement.setString(2, execution.nodeId());
            statement.setString(3, execution.status().name());
            statement.setObject(4, execution.startTime());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("database did not return a node execution id");
        }
        return key.longValue();
    }

    @Override
    public void markSuccess(Long nodeExecutionId, long durationMs) {
        requireSingleUpdate(jdbcTemplate.update("""
                UPDATE flow_execution_node
                SET status = 'SUCCESS', end_time = CURRENT_TIMESTAMP,
                    duration_ms = ?, error_message = NULL
                WHERE id = ? AND status = 'RUNNING'
                """, durationMs, nodeExecutionId), nodeExecutionId);
    }

    @Override
    public void markFailed(Long nodeExecutionId, long durationMs, String errorMessage) {
        requireSingleUpdate(jdbcTemplate.update("""
                UPDATE flow_execution_node
                SET status = 'FAILED', end_time = CURRENT_TIMESTAMP,
                    duration_ms = ?, error_message = ?
                WHERE id = ? AND status = 'RUNNING'
                """, durationMs, truncate(errorMessage, 1000), nodeExecutionId), nodeExecutionId);
    }

    @Override
    public List<NodeExecution> findByExecutionId(String executionId) {
        return jdbcTemplate.query("""
                SELECT id, execution_id, node_id, status, start_time, end_time,
                       duration_ms, error_message, created_at
                FROM flow_execution_node
                WHERE execution_id = ?
                ORDER BY id
                """, ROW_MAPPER, executionId);
    }

    private static void requireSingleUpdate(int affectedRows, Long nodeExecutionId) {
        if (affectedRows != 1) {
            throw new IllegalStateException(
                    "Node execution trace state changed unexpectedly: " + nodeExecutionId);
        }
    }

    private static String truncate(String value, int maximumLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }
}
