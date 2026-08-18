CREATE SCHEMA IF NOT EXISTS finance;

CREATE TABLE IF NOT EXISTS finance.invoice (
    id                    UUID PRIMARY KEY,
    invoice_number        VARCHAR(50) NOT NULL UNIQUE,
    student_id            UUID NOT NULL,
    student_user_id       UUID NOT NULL,
    student_display_name  VARCHAR(200) NOT NULL,
    student_email         VARCHAR(255) NOT NULL,
    student_code          VARCHAR(50) NOT NULL,
    semester_id           UUID NOT NULL,
    semester_name         VARCHAR(200) NOT NULL,
    semester_name_en      VARCHAR(200),
    semester_name_vi      VARCHAR(200),
    status                VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    subtotal              DECIMAL(10, 2) NOT NULL,
    discount              DECIMAL(10, 2) NOT NULL DEFAULT 0,
    total                 DECIMAL(10, 2) NOT NULL,
    due_date              TIMESTAMP NOT NULL,
    paid_at               TIMESTAMP,
    notes                 TEXT,
    created_at            TIMESTAMP NOT NULL,
    updated_at            TIMESTAMP NOT NULL,
    version               BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_invoice_number ON finance.invoice (invoice_number);
CREATE INDEX IF NOT EXISTS idx_invoice_student ON finance.invoice (student_id);
CREATE INDEX IF NOT EXISTS idx_invoice_status ON finance.invoice (status);

CREATE TABLE IF NOT EXISTS finance.invoice_item (
    id          UUID PRIMARY KEY,
    invoice_id  UUID NOT NULL,
    description VARCHAR(500) NOT NULL,
    quantity    INTEGER NOT NULL DEFAULT 1,
    unit_price  DECIMAL(10, 2) NOT NULL,
    total       DECIMAL(10, 2) NOT NULL,
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL,
    version     BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_invoice_item_invoice ON finance.invoice_item (invoice_id);

CREATE TABLE IF NOT EXISTS finance.payment (
    id               UUID PRIMARY KEY,
    payment_number   VARCHAR(50) NOT NULL UNIQUE,
    invoice_id       UUID NOT NULL,
    student_id       UUID NOT NULL,
    amount           DECIMAL(10, 2) NOT NULL,
    method           VARCHAR(50) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    paid_at          TIMESTAMP,
    transaction_id   VARCHAR(200),
    notes            TEXT,
    created_at       TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP NOT NULL,
    version          BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_payment_number ON finance.payment (payment_number);
CREATE INDEX IF NOT EXISTS idx_payment_invoice ON finance.payment (invoice_id);

CREATE TABLE IF NOT EXISTS finance.payment_intent (
    id            UUID PRIMARY KEY,
    intent_number VARCHAR(50) NOT NULL UNIQUE,
    invoice_id    UUID NOT NULL,
    student_id    UUID NOT NULL,
    provider      VARCHAR(20) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'REQUIRES_ACTION',
    amount        DECIMAL(10, 2) NOT NULL,
    currency      VARCHAR(10) NOT NULL DEFAULT 'VND',
    expires_at    TIMESTAMP NOT NULL,
    finalized_at  TIMESTAMP,
    created_at    TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP NOT NULL,
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_payment_intent_number ON finance.payment_intent (intent_number);
CREATE INDEX IF NOT EXISTS idx_payment_intent_invoice ON finance.payment_intent (invoice_id);
