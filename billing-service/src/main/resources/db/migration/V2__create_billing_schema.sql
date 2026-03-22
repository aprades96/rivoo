-- =====================================================
-- billing-service: subscription plans, limits, subscriptions, webhook log
-- =====================================================

CREATE TABLE subscription_plans (
    id                          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    external_id                 CHAR(44)        NOT NULL UNIQUE,
    name                        ENUM('FREE_TRIAL','BASIC','PREMIUM','ENTERPRISE') NOT NULL UNIQUE,
    display_name                VARCHAR(100)    NOT NULL,
    monthly_price               DECIMAL(10,2)   NOT NULL DEFAULT 0.00,
    stripe_monthly_price_id     VARCHAR(100)    NULL,
    trial_days                  INT             DEFAULT 0,
    active                      BOOLEAN         DEFAULT TRUE,
    created_at                  TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE plan_limits (
    id                          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    plan_id                     BIGINT          NOT NULL,
    limit_key                   VARCHAR(100)    NOT NULL,
    limit_value                 INT             NOT NULL,
    CONSTRAINT fk_pl_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans(id) ON DELETE CASCADE,
    UNIQUE KEY uq_plan_limit (plan_id, limit_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE subscriptions (
    id                          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    external_id                 CHAR(44)        NOT NULL UNIQUE,
    tenant_id                   CHAR(44)        NOT NULL UNIQUE,
    plan_id                     BIGINT          NOT NULL,
    status                      ENUM('TRIALING','ACTIVE','PAST_DUE','CANCELLED','EXPIRED') DEFAULT 'TRIALING',
    stripe_customer_id          VARCHAR(100)    NULL UNIQUE,
    stripe_subscription_id      VARCHAR(100)    NULL UNIQUE,
    trial_start                 TIMESTAMP       NULL,
    trial_end                   TIMESTAMP       NULL,
    current_period_start        TIMESTAMP       NULL,
    current_period_end          TIMESTAMP       NULL,
    cancel_at_period_end        BOOLEAN         DEFAULT FALSE,
    created_at                  TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_sub_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans(id),
    INDEX idx_sub_tenant (tenant_id),
    INDEX idx_sub_stripe_customer (stripe_customer_id),
    INDEX idx_sub_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE webhook_event_log (
    id                          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    stripe_event_id             VARCHAR(100)    NOT NULL UNIQUE,
    event_type                  VARCHAR(100)    NOT NULL,
    processed_at                TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    payload                     JSON            NULL,
    INDEX idx_webhook_event_type (event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
