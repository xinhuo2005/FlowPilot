CREATE TABLE rule_change_outbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL,
    rule_code VARCHAR(64) NOT NULL,
    rule_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    aggregate_version INT DEFAULT NULL,
    payload VARCHAR(2048) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    available_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at DATETIME DEFAULT NULL,
    last_error VARCHAR(1000) DEFAULT NULL,
    UNIQUE KEY uk_outbox_event (event_id),
    KEY idx_outbox_pending (status, available_at, id),
    KEY idx_outbox_rule (rule_code, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
