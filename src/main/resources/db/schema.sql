CREATE TABLE rule_definition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    rule_code VARCHAR(64) NOT NULL,
    rule_name VARCHAR(128) NOT NULL,
    current_version INT DEFAULT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    description VARCHAR(512) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_rule_code (rule_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE rule_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    rule_id BIGINT NOT NULL,
    version INT NOT NULL,
    rule_content TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    checksum VARCHAR(64) DEFAULT NULL,
    created_by VARCHAR(64) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at DATETIME DEFAULT NULL,
    UNIQUE KEY uk_rule_version (rule_id, version),
    KEY idx_rule_status (rule_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE rule_gray_policy (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    rule_id BIGINT NOT NULL,
    base_version INT NOT NULL,
    gray_version INT NOT NULL,
    percentage INT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_gray_rule (rule_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE flow_execution (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    execution_id VARCHAR(64) NOT NULL,
    rule_code VARCHAR(64) NOT NULL,
    rule_version INT NOT NULL,
    routing_key VARCHAR(128) DEFAULT NULL,
    status VARCHAR(32) NOT NULL,
    start_time DATETIME(3) NOT NULL,
    end_time DATETIME(3) DEFAULT NULL,
    duration_ms BIGINT DEFAULT NULL,
    error_code VARCHAR(64) DEFAULT NULL,
    error_message VARCHAR(1000) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_execution_id (execution_id),
    KEY idx_rule_execution (rule_code, rule_version, created_at),
    KEY idx_routing_execution (routing_key, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE flow_execution_node (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    execution_id VARCHAR(64) NOT NULL,
    node_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    start_time DATETIME(3) NOT NULL,
    end_time DATETIME(3) DEFAULT NULL,
    duration_ms BIGINT DEFAULT NULL,
    error_message VARCHAR(1000) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_execution_node (execution_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

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
