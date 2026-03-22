-- =====================================================
-- Seed: 4 subscription plans + plan limits
-- =====================================================

INSERT INTO subscription_plans (external_id, name, display_name, monthly_price, stripe_monthly_price_id, trial_days, active) VALUES
('pln_00000000-0000-0000-0000-000000000001', 'FREE_TRIAL',  'Free Trial',         0.00,  NULL, 14, TRUE),
('pln_00000000-0000-0000-0000-000000000002', 'BASIC',       'Rivoo - Basic',      29.00, NULL,  0, TRUE),
('pln_00000000-0000-0000-0000-000000000003', 'PREMIUM',     'Rivoo - Premium',    59.00, NULL,  0, TRUE),
('pln_00000000-0000-0000-0000-000000000004', 'ENTERPRISE',  'Rivoo - Enterprise', 99.00, NULL,  0, TRUE);

-- Plan limits: limit_value = -1 means unlimited
-- FREE_TRIAL
INSERT INTO plan_limits (plan_id, limit_key, limit_value) VALUES
((SELECT id FROM subscription_plans WHERE name = 'FREE_TRIAL'), 'max_employees', 1),
((SELECT id FROM subscription_plans WHERE name = 'FREE_TRIAL'), 'max_appointments_per_month', 50),
((SELECT id FROM subscription_plans WHERE name = 'FREE_TRIAL'), 'email_reminders_enabled', 0),
((SELECT id FROM subscription_plans WHERE name = 'FREE_TRIAL'), 'sms_reminders_enabled', 0);

-- BASIC
INSERT INTO plan_limits (plan_id, limit_key, limit_value) VALUES
((SELECT id FROM subscription_plans WHERE name = 'BASIC'), 'max_employees', 3),
((SELECT id FROM subscription_plans WHERE name = 'BASIC'), 'max_appointments_per_month', 200),
((SELECT id FROM subscription_plans WHERE name = 'BASIC'), 'email_reminders_enabled', 1),
((SELECT id FROM subscription_plans WHERE name = 'BASIC'), 'sms_reminders_enabled', 0);

-- PREMIUM
INSERT INTO plan_limits (plan_id, limit_key, limit_value) VALUES
((SELECT id FROM subscription_plans WHERE name = 'PREMIUM'), 'max_employees', 10),
((SELECT id FROM subscription_plans WHERE name = 'PREMIUM'), 'max_appointments_per_month', -1),
((SELECT id FROM subscription_plans WHERE name = 'PREMIUM'), 'email_reminders_enabled', 1),
((SELECT id FROM subscription_plans WHERE name = 'PREMIUM'), 'sms_reminders_enabled', 1);

-- ENTERPRISE
INSERT INTO plan_limits (plan_id, limit_key, limit_value) VALUES
((SELECT id FROM subscription_plans WHERE name = 'ENTERPRISE'), 'max_employees', -1),
((SELECT id FROM subscription_plans WHERE name = 'ENTERPRISE'), 'max_appointments_per_month', -1),
((SELECT id FROM subscription_plans WHERE name = 'ENTERPRISE'), 'email_reminders_enabled', 1),
((SELECT id FROM subscription_plans WHERE name = 'ENTERPRISE'), 'sms_reminders_enabled', 1);
