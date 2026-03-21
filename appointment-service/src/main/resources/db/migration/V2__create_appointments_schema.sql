-- =====================================================
-- appointment-service: appointments table
-- =====================================================

CREATE TABLE appointments (
    id                          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    external_id                 CHAR(44)        NOT NULL UNIQUE,
    tenant_id                   CHAR(44)        NOT NULL,

    -- Client snapshot (immutable after booking)
    client_id                   CHAR(44)        NULL,
    client_name                 VARCHAR(200)    NOT NULL,
    client_phone                VARCHAR(20)     NULL,
    client_email                VARCHAR(255)    NULL,

    -- Employee snapshot
    employee_id                 CHAR(44)        NOT NULL,
    employee_name               VARCHAR(200)    NOT NULL,

    -- Service snapshot
    service_id                  CHAR(44)        NOT NULL,
    service_name                VARCHAR(200)    NOT NULL,
    service_price               DECIMAL(10,2)   NOT NULL,
    service_duration_minutes    INT             NOT NULL,

    -- Scheduling
    start_time                  TIMESTAMP       NOT NULL,
    end_time                    TIMESTAMP       NOT NULL,

    -- Status
    status                      ENUM('PENDING','CONFIRMED','IN_PROGRESS','COMPLETED','CANCELLED','NO_SHOW')
                                DEFAULT 'PENDING',
    cancellation_reason         VARCHAR(500)    NULL,
    cancelled_by                ENUM('CLIENT','SALON','SYSTEM') NULL,

    -- Metadata
    source                      ENUM('ONLINE','PHONE','WALK_IN','MANUAL') DEFAULT 'MANUAL',
    notes                       TEXT            NULL,
    reminder_sent               BOOLEAN         DEFAULT FALSE,

    -- Timestamps
    created_at                  TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- Indexes
    INDEX idx_appointments_tenant_start (tenant_id, start_time),
    INDEX idx_appointments_employee_start (tenant_id, employee_id, start_time),
    INDEX idx_appointments_overlap_check (tenant_id, employee_id, start_time, end_time, status),
    INDEX idx_appointments_reminder (start_time, reminder_sent, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
