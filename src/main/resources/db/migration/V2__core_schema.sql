-- Faz 1: çekirdek tablolar (CLAUDE.md §6).
-- Enum alanları metin olarak saklanır ve CHECK ile korunur; adlar API'deki değerlerle birebir aynıdır.
-- Resolution.embedding sütunu Faz 4'te eklenecek: boyutu seçilecek embedding modeline bağlıdır.

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    full_name     VARCHAR(100) NOT NULL,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('Specialist', 'TeamLead', 'Manager')),
    password_hash VARCHAR(100) NOT NULL
);

CREATE TABLE payment_transactions (
    id             BIGSERIAL PRIMARY KEY,
    reference      VARCHAR(30)    NOT NULL UNIQUE,
    type           VARCHAR(20)    NOT NULL CHECK (type IN ('Transfer', 'Fast', 'BulkPayment', 'CorporateOrder')),
    amount         NUMERIC(18, 2) NOT NULL CHECK (amount > 0),
    currency       VARCHAR(3)     NOT NULL DEFAULT 'TRY',
    sender_name    VARCHAR(150)   NOT NULL,
    sender_iban    VARCHAR(34)    NOT NULL,
    receiver_name  VARCHAR(150)   NOT NULL,
    receiver_iban  VARCHAR(34)    NOT NULL,
    description    VARCHAR(255),
    channel        VARCHAR(20)    NOT NULL CHECK (channel IN ('InternetBranch', 'Mobile', 'Branch', 'CorporateApi')),
    value_date     DATE           NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL
);

CREATE TABLE exception_cases (
    id                  BIGSERIAL PRIMARY KEY,
    case_number         VARCHAR(30)  NOT NULL UNIQUE,
    transaction_id      BIGINT       NOT NULL REFERENCES payment_transactions (id),
    error_code          VARCHAR(30)  NOT NULL,
    error_description   VARCHAR(255) NOT NULL,
    priority            VARCHAR(10)  NOT NULL CHECK (priority IN ('Low', 'Medium', 'High', 'Critical')),
    status              VARCHAR(20)  NOT NULL CHECK (status IN
        ('New', 'Assigned', 'InReview', 'AwaitingInfo', 'SolutionProposed', 'Escalated', 'Closed', 'Rejected')),
    assigned_to_user_id BIGINT REFERENCES users (id),
    proposed_by_user_id BIGINT REFERENCES users (id),
    closed_by_user_id   BIGINT REFERENCES users (id),
    sla_deadline        TIMESTAMPTZ  NOT NULL,
    is_escalated        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    closed_at           TIMESTAMPTZ
);
CREATE INDEX idx_cases_status       ON exception_cases (status);
CREATE INDEX idx_cases_assigned_to  ON exception_cases (assigned_to_user_id);
CREATE INDEX idx_cases_sla_deadline ON exception_cases (sla_deadline);
CREATE INDEX idx_cases_created_at   ON exception_cases (created_at);
CREATE INDEX idx_cases_error_code   ON exception_cases (error_code);

CREATE TABLE case_notes (
    id             BIGSERIAL PRIMARY KEY,
    case_id        BIGINT      NOT NULL REFERENCES exception_cases (id),
    author_user_id BIGINT      NOT NULL REFERENCES users (id),
    text           TEXT        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_case_notes_case ON case_notes (case_id);

CREATE TABLE status_history (
    id                 BIGSERIAL PRIMARY KEY,
    case_id            BIGINT      NOT NULL REFERENCES exception_cases (id),
    from_status        VARCHAR(20),
    to_status          VARCHAR(20) NOT NULL,
    changed_by_user_id BIGINT REFERENCES users (id),   -- NULL: sistem (ör. otomatik dağıtım)
    reason             VARCHAR(500),
    changed_at         TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_status_history_case ON status_history (case_id);

CREATE TABLE resolutions (
    id                        BIGSERIAL PRIMARY KEY,
    case_id                   BIGINT      NOT NULL REFERENCES exception_cases (id),
    text                      TEXT        NOT NULL,
    proposed_by_user_id       BIGINT      NOT NULL REFERENCES users (id),
    approved_by_user_id       BIGINT REFERENCES users (id),
    is_verified               BOOLEAN     NOT NULL DEFAULT FALSE,
    include_in_knowledge_base BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at                TIMESTAMPTZ NOT NULL,
    approved_at               TIMESTAMPTZ,
    -- Onaylanmamış çözüm doğrulanmış sayılamaz ve bilgi tabanına giremez
    CONSTRAINT chk_resolution_verified CHECK (
        (is_verified = FALSE AND include_in_knowledge_base = FALSE AND approved_by_user_id IS NULL)
        OR (is_verified = TRUE AND approved_by_user_id IS NOT NULL AND approved_by_user_id <> proposed_by_user_id)
    )
);
CREATE INDEX idx_resolutions_case ON resolutions (case_id);

CREATE TABLE ai_analyses (
    id                  BIGSERIAL PRIMARY KEY,
    case_id             BIGINT       NOT NULL REFERENCES exception_cases (id),
    result_json         JSONB        NOT NULL,
    referenced_case_ids JSONB        NOT NULL DEFAULT '[]',
    masked_field_count  INTEGER      NOT NULL DEFAULT 0,
    model_name          VARCHAR(100) NOT NULL,
    latency_ms          INTEGER      NOT NULL,
    feedback            VARCHAR(20) CHECK (feedback IN ('Useful', 'NotUseful')),
    created_at          TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_ai_analyses_case ON ai_analyses (case_id);

CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    timestamp   TIMESTAMPTZ  NOT NULL,
    user_id     BIGINT REFERENCES users (id),          -- NULL: sistem
    action      VARCHAR(40)  NOT NULL,
    entity_type VARCHAR(40)  NOT NULL,
    entity_id   BIGINT       NOT NULL,
    details     JSONB
);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);

-- Audit log yalnızca eklemeye açıktır: güncelleme ve silme veritabanı seviyesinde engellenir.
CREATE FUNCTION prevent_audit_log_modification() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs tablosu yalnızca eklemeye açıktır';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_logs_append_only
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_log_modification();

-- error_code NULL ise kural öncelik bazlıdır; doluysa o hata koduna özel ve daha önceliklidir.
-- duration_minutes: Critical için gerçek dakika, diğerleri için mesai dakikası (Low = 2 iş günü = 1080).
CREATE TABLE sla_policies (
    id               BIGSERIAL PRIMARY KEY,
    priority         VARCHAR(10) NOT NULL CHECK (priority IN ('Low', 'Medium', 'High', 'Critical')),
    error_code       VARCHAR(30),
    duration_minutes INTEGER     NOT NULL CHECK (duration_minutes > 0),
    UNIQUE (priority, error_code)
);
-- UNIQUE NULL değerleri farklı saydığı için öncelik bazlı kuralların tekilliği ayrıca sağlanır
CREATE UNIQUE INDEX uq_sla_policies_priority_default ON sla_policies (priority) WHERE error_code IS NULL;

CREATE TABLE public_holidays (
    holiday_date DATE PRIMARY KEY,
    name         VARCHAR(100) NOT NULL
);

-- Varsayılan SLA süreleri (CLAUDE.md §9)
INSERT INTO sla_policies (priority, error_code, duration_minutes) VALUES
    ('Critical', NULL, 30),
    ('High',     NULL, 120),
    ('Medium',   NULL, 480),
    ('Low',      NULL, 1080);
