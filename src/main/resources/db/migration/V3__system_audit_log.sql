CREATE TABLE IF NOT EXISTS system_audit_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type      VARCHAR(60)  NOT NULL,
    event_action    VARCHAR(60)  NOT NULL,
    source_component VARCHAR(80) NOT NULL,
    resource_type   VARCHAR(40),
    resource_id     VARCHAR(255),
    details         TEXT,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sal_event_type (event_type),
    INDEX idx_sal_source (source_component),
    INDEX idx_sal_resource (resource_type, resource_id),
    INDEX idx_sal_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
