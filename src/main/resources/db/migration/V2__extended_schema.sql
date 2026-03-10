-- Account mapping: IBAN/BIC to Fineract account IDs
CREATE TABLE IF NOT EXISTS account_mapping (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    iban                 VARCHAR(34),
    bic                  VARCHAR(11),
    external_id          VARCHAR(255),
    fineract_account_id  VARCHAR(255) NOT NULL,
    account_type         VARCHAR(20)  NOT NULL DEFAULT 'SAVINGS',
    account_holder_name  VARCHAR(255),
    currency             VARCHAR(3),
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_iban (iban),
    INDEX idx_bic (bic),
    INDEX idx_external_id (external_id),
    INDEX idx_fineract_account (fineract_account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Direct debit mandates
CREATE TABLE IF NOT EXISTS direct_debit_mandates (
    id                                BIGINT AUTO_INCREMENT PRIMARY KEY,
    mandate_id                        VARCHAR(255) NOT NULL,
    creditor_name                     VARCHAR(255),
    creditor_account                  VARCHAR(255) NOT NULL,
    creditor_agent_bic                VARCHAR(11),
    debtor_name                       VARCHAR(255),
    debtor_account                    VARCHAR(255) NOT NULL,
    debtor_agent_bic                  VARCHAR(11),
    max_amount                        DECIMAL(19,4),
    currency                          VARCHAR(3),
    frequency                         VARCHAR(50),
    status                            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    valid_from                        DATE,
    valid_to                          DATE,
    fineract_standing_instruction_id  VARCHAR(255),
    created_at                        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_mandate_id (mandate_id),
    INDEX idx_debtor_account (debtor_account),
    INDEX idx_creditor_account (creditor_account),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Payment investigations (camt.056 cancellation tracking)
CREATE TABLE IF NOT EXISTS payment_investigations (
    id                              BIGINT AUTO_INCREMENT PRIMARY KEY,
    investigation_id                VARCHAR(255) NOT NULL,
    original_message_id             VARCHAR(255) NOT NULL,
    original_end_to_end_id          VARCHAR(255),
    original_transaction_id         VARCHAR(255),
    cancellation_reason_code        VARCHAR(50),
    cancellation_reason_description TEXT,
    status                          VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    resolution_xml                  LONGTEXT,
    fineract_undo_transaction_id    VARCHAR(255),
    original_payment_id             BIGINT,
    created_at                      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_investigation_id (investigation_id),
    INDEX idx_original_message_id (original_message_id),
    INDEX idx_status (status),
    CONSTRAINT fk_investigation_payment FOREIGN KEY (original_payment_id)
        REFERENCES payment_messages (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Add operation_type column to payment_messages
ALTER TABLE payment_messages ADD COLUMN operation_type VARCHAR(30) AFTER status;
ALTER TABLE payment_messages ADD COLUMN original_message_ref VARCHAR(255) AFTER fineract_transaction_id;
ALTER TABLE payment_messages ADD INDEX idx_operation_type (operation_type);
