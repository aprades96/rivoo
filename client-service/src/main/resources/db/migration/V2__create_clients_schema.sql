-- ============================================================
-- V2: Create clients schema
-- ============================================================

CREATE TABLE clients (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    external_id         CHAR(44)        NOT NULL UNIQUE,
    tenant_id           CHAR(44)        NOT NULL,
    first_name          VARCHAR(100)    NOT NULL,
    last_name           VARCHAR(100)    NOT NULL,
    email               VARCHAR(255)    NULL,
    phone               VARCHAR(20)     NULL,
    gender              ENUM('MALE','FEMALE','NON_BINARY','PREFER_NOT_TO_SAY') NULL,
    source              ENUM('WALK_IN','ONLINE_BOOKING','REFERRAL','SOCIAL_MEDIA','OTHER') DEFAULT 'WALK_IN',
    notes               TEXT            NULL,
    total_visits        INT             DEFAULT 0,
    last_visit_at       TIMESTAMP       NULL,
    gdpr_consent_at     TIMESTAMP       NULL,
    gdpr_anonymized_at  TIMESTAMP       NULL,
    active              BOOLEAN         DEFAULT TRUE,
    created_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_client_tenant (tenant_id),
    UNIQUE KEY uq_client_tenant_email (tenant_id, email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
