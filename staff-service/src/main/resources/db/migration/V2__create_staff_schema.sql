-- ============================================================
-- V2: Create staff schema (employees, working hours, services, employee_services)
-- ============================================================

CREATE TABLE employees (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    external_id         CHAR(44)        NOT NULL UNIQUE,
    tenant_id           CHAR(44)        NOT NULL,
    first_name          VARCHAR(100)    NOT NULL,
    last_name           VARCHAR(100)    NOT NULL,
    email               VARCHAR(255)    NULL,
    phone               VARCHAR(20)     NULL,
    role                ENUM('STYLIST','BARBER','COLORIST','ASSISTANT','RECEPTIONIST','MANAGER') NOT NULL DEFAULT 'STYLIST',
    keycloak_user_id    VARCHAR(36)     NULL,
    active              BOOLEAN         DEFAULT TRUE,
    created_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_employee_tenant (tenant_id),
    INDEX idx_employee_keycloak (keycloak_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE employee_working_hours (
    id                  BIGINT      AUTO_INCREMENT PRIMARY KEY,
    employee_id         BIGINT      NOT NULL,
    day_of_week         TINYINT     NOT NULL,
    is_open             BOOLEAN     DEFAULT TRUE,
    open_time           TIME        NULL,
    close_time          TIME        NULL,
    break_start_time    TIME        NULL,
    break_end_time      TIME        NULL,
    CONSTRAINT fk_wh_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    UNIQUE KEY uq_employee_day (employee_id, day_of_week)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE services (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    external_id         CHAR(44)        NOT NULL UNIQUE,
    tenant_id           CHAR(44)        NOT NULL,
    name                VARCHAR(200)    NOT NULL,
    description         TEXT            NULL,
    duration_minutes    INT             NOT NULL,
    price               DECIMAL(10,2)   NOT NULL,
    currency            VARCHAR(3)      DEFAULT 'EUR',
    active              BOOLEAN         DEFAULT TRUE,
    created_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_service_tenant (tenant_id),
    UNIQUE KEY uq_service_name_tenant (tenant_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE employee_services (
    employee_id         BIGINT          NOT NULL,
    service_id          BIGINT          NOT NULL,
    tenant_id           CHAR(44)        NOT NULL,
    custom_duration     INT             NULL,
    custom_price        DECIMAL(10,2)   NULL,
    PRIMARY KEY (employee_id, service_id),
    CONSTRAINT fk_es_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    CONSTRAINT fk_es_service FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE CASCADE,
    INDEX idx_es_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
