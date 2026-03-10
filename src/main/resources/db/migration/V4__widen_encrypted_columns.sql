ALTER TABLE account_mapping MODIFY COLUMN iban VARCHAR(255);
ALTER TABLE direct_debit_mandates MODIFY COLUMN creditor_account VARCHAR(255) NOT NULL;
ALTER TABLE direct_debit_mandates MODIFY COLUMN debtor_account VARCHAR(255) NOT NULL;
