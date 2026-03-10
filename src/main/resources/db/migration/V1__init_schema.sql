CREATE TABLE IF NOT EXISTS payment_messages (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id      VARCHAR(255)   NOT NULL,
    message_type    VARCHAR(50)    NOT NULL,
    direction       VARCHAR(20)    NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    raw_xml         LONGTEXT       NOT NULL,
    end_to_end_id   VARCHAR(255),
    instruction_id  VARCHAR(255),
    debtor_name     VARCHAR(255),
    debtor_account  VARCHAR(255),
    creditor_name   VARCHAR(255),
    creditor_account VARCHAR(255),
    amount          DECIMAL(19,4),
    currency        VARCHAR(3),
    fineract_transaction_id VARCHAR(255),
    error_message   TEXT,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_message_id (message_id),
    INDEX idx_message_type (message_type),
    INDEX idx_status (status),
    INDEX idx_end_to_end_id (end_to_end_id),
    INDEX idx_debtor_account (debtor_account),
    INDEX idx_creditor_account (creditor_account),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS message_audit_log (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_message_id  BIGINT       NOT NULL,
    action              VARCHAR(100) NOT NULL,
    details             TEXT,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_payment_message_id (payment_message_id),
    CONSTRAINT fk_audit_payment_message FOREIGN KEY (payment_message_id)
        REFERENCES payment_messages (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
