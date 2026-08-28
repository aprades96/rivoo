ALTER TABLE salons
    ADD COLUMN onboarding_completed_at TIMESTAMP NULL AFTER status;

UPDATE salons
SET onboarding_completed_at = created_at,
    updated_at = updated_at
WHERE status IS NULL OR status <> 'ONBOARDING';
