-- =====================================================
-- notification-service: notification_log table
-- =====================================================

CREATE TABLE notification_log (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    external_id         CHAR(44)        NOT NULL UNIQUE,
    tenant_id           CHAR(44)        NOT NULL,
    recipient_email     VARCHAR(255)    NULL,
    channel             ENUM('EMAIL','SMS') DEFAULT 'EMAIL',
    type                VARCHAR(50)     NOT NULL,
    reference_type      VARCHAR(50)     NULL,
    reference_id        CHAR(44)        NULL,
    subject             VARCHAR(500)    NULL,
    body                TEXT            NOT NULL,
    status              ENUM('PENDING','SENT','FAILED','CANCELLED') DEFAULT 'PENDING',
    scheduled_for       TIMESTAMP       NULL,
    sent_at             TIMESTAMP       NULL,
    retry_count         INT             DEFAULT 0,
    created_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_notification_tenant (tenant_id),
    INDEX idx_notification_status_scheduled (status, scheduled_for),
    INDEX idx_notification_reference (reference_type, reference_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
