-- ============================================================
-- V2: Create salon schema (salons + business hours)
-- ============================================================

CREATE TABLE salons (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    external_id         CHAR(44)        NOT NULL UNIQUE,
    tenant_id           CHAR(44)        NOT NULL,
    name                VARCHAR(200)    NOT NULL,
    slug                VARCHAR(200)    UNIQUE,
    owner_user_id       VARCHAR(36)     NULL,
    email               VARCHAR(255)    NOT NULL,
    phone               VARCHAR(20)     NOT NULL,
    description         TEXT            NULL,
    address_street      VARCHAR(300)    NOT NULL,
    address_city        VARCHAR(100)    DEFAULT 'Barcelona',
    address_postal_code VARCHAR(10)     NOT NULL,
    timezone            VARCHAR(50)     DEFAULT 'Europe/Madrid',
    currency            VARCHAR(3)      DEFAULT 'EUR',
    subscription_plan   ENUM('FREE_TRIAL','BASIC','PREMIUM','ENTERPRISE') DEFAULT 'FREE_TRIAL',
    status              ENUM('ONBOARDING','ACTIVE','INACTIVE','SUSPENDED','FAILED') DEFAULT 'ONBOARDING',
    created_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_salon_tenant (tenant_id),
    INDEX idx_salon_slug (slug),
    INDEX idx_salon_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE salon_business_hours (
    id                  BIGINT      AUTO_INCREMENT PRIMARY KEY,
    salon_id            BIGINT      NOT NULL,
    day_of_week         TINYINT     NOT NULL,
    is_open             BOOLEAN     DEFAULT TRUE,
    open_time           TIME        NULL,
    close_time          TIME        NULL,
    break_start_time    TIME        NULL,
    break_end_time      TIME        NULL,
    CONSTRAINT fk_bh_salon FOREIGN KEY (salon_id) REFERENCES salons(id) ON DELETE CASCADE,
    UNIQUE KEY uq_salon_day (salon_id, day_of_week)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
