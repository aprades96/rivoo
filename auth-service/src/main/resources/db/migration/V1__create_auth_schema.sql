-- Auth service schema: audit log + tenant-user mapping

CREATE TABLE onboarding_events (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    tenant_id           CHAR(44)        NOT NULL,
    keycloak_user_id    VARCHAR(36)     NOT NULL,
    email               VARCHAR(255)    NOT NULL,
    event_type          ENUM('OWNER_CREATED','EMPLOYEE_CREATED','USER_DISABLED','USER_ENABLED','ROLE_CHANGED') NOT NULL,
    details             JSON            NULL,
    created_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_onboarding_tenant (tenant_id),
    INDEX idx_onboarding_keycloak_user (keycloak_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tenant_user_mapping (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    tenant_id           CHAR(44)        NOT NULL,
    keycloak_user_id    VARCHAR(36)     NOT NULL,
    role                ENUM('SALON_OWNER','EMPLOYEE') NOT NULL,
    is_active           BOOLEAN         DEFAULT TRUE,
    created_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_keycloak_user (keycloak_user_id),
    INDEX idx_tenant_user_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
