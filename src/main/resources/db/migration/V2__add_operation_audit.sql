CREATE TABLE flow_operation_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    operation_id VARCHAR(64) NOT NULL,
    rule_code VARCHAR(64) NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    target_version INT DEFAULT NULL,
    operator VARCHAR(64) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    result_message VARCHAR(512) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME DEFAULT NULL,
    UNIQUE KEY uk_operation_id (operation_id),
    KEY idx_operation_rule (rule_code, created_at),
    KEY idx_operation_status (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
