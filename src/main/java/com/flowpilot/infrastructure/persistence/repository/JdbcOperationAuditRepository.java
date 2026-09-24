package com.flowpilot.infrastructure.persistence.repository;

import com.flowpilot.application.OperationRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class JdbcOperationAuditRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcOperationAuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<OperationAuditRecord> findByOperationId(String operationId) {
        return jdbcTemplate.query(
                        """
                        SELECT operation_id, rule_code, operation_type, target_version,
                               operator, request_hash, status, result_message,
                               created_at, completed_at
                        FROM flow_operation_audit
                        WHERE operation_id = ?
                        """,
                        (resultSet, rowNum) -> new OperationAuditRecord(
                                resultSet.getString("operation_id"),
                                resultSet.getString("rule_code"),
                                resultSet.getString("operation_type"),
                                (Integer) resultSet.getObject("target_version"),
                                resultSet.getString("operator"),
                                resultSet.getString("request_hash"),
                                OperationAuditStatus.valueOf(resultSet.getString("status")),
                                resultSet.getString("result_message"),
                                toLocalDateTime(resultSet.getTimestamp("created_at")),
                                toLocalDateTime(resultSet.getTimestamp("completed_at")))
                , operationId)
                .stream()
                .findFirst();
    }

    public boolean insertInProgress(OperationRequest request) {
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO flow_operation_audit
                        (operation_id, rule_code, operation_type, target_version,
                         operator, request_hash, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    request.operationId(), request.ruleCode(), request.operationType(),
                    request.targetVersion(), request.operator(), request.requestHash(),
                    OperationAuditStatus.IN_PROGRESS.name());
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    public void markSucceeded(String operationId, String message) {
        updateStatus(operationId, OperationAuditStatus.SUCCEEDED, message);
    }

    public void markFailed(String operationId, String message) {
        updateStatus(operationId, OperationAuditStatus.FAILED, message);
    }

    private void updateStatus(String operationId, OperationAuditStatus status, String message) {
        jdbcTemplate.update(
                """
                UPDATE flow_operation_audit
                SET status = ?, result_message = ?, completed_at = CURRENT_TIMESTAMP
                WHERE operation_id = ? AND status = ?
                """,
                status.name(), truncate(message), operationId,
                OperationAuditStatus.IN_PROGRESS.name());
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 512 ? message : message.substring(0, 512);
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    public record OperationAuditRecord(
            String operationId,
            String ruleCode,
            String operationType,
            Integer targetVersion,
            String operator,
            String requestHash,
            OperationAuditStatus status,
            String resultMessage,
            LocalDateTime createdAt,
            LocalDateTime completedAt
    ) {
    }

    public enum OperationAuditStatus {
        IN_PROGRESS, SUCCEEDED, FAILED
    }
}
