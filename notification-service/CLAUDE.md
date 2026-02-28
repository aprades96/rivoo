# notification-service — Module CLAUDE.md

## Purpose

Handles all outbound communications: emails (reminders, confirmations, welcome, payment alerts). Manages notification scheduling, templates, and retry logic. I/O-bound service — if it fails, it must NOT affect appointment creation.

**Port**: 8086 | **DB**: `notification_db` | **Package**: `com.rivoo.notification`

---

## Database: `notification_db`

### Table: `notification_log` (prefix: `ntf_`)

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT PK AUTO_INCREMENT | Internal PK |
| `external_id` | CHAR(44) NOT NULL UNIQUE | `ntf_` prefix |
| `tenant_id` | CHAR(44) NOT NULL | |
| `recipient_email` | VARCHAR(255) NULL | |
| `channel` | ENUM('EMAIL','SMS') NOT NULL | |
| `type` | ENUM('APPOINTMENT_REMINDER','APPOINTMENT_CONFIRMATION','APPOINTMENT_CANCELLATION','WELCOME','PAYMENT_FAILED','SUBSCRIPTION_CANCELED') NOT NULL | |
| `reference_type` | VARCHAR(50) NULL | e.g., 'APPOINTMENT' |
| `reference_id` | CHAR(44) NULL | e.g., appointment external_id |
| `subject` | VARCHAR(500) NULL | Email subject |
| `body` | TEXT NOT NULL | Rendered email body |
| `status` | ENUM('PENDING','SENT','FAILED') DEFAULT 'PENDING' | |
| `scheduled_for` | TIMESTAMP NULL | Scheduled send time |
| `sent_at` | TIMESTAMP NULL | Actual send time |
| `retry_count` | INT DEFAULT 0 | |
| `created_at` | TIMESTAMP | |

---

## Email Templates

| Type | Trigger | Content |
|------|---------|---------|
| `WELCOME` | Salon onboarding complete | Welcome message, getting started guide |
| `APPOINTMENT_CONFIRMATION` | Appointment created | Appointment details, salon info |
| `APPOINTMENT_REMINDER` | Scheduled (24h and 1h before) | Reminder with appointment details |
| `APPOINTMENT_CANCELLATION` | Appointment cancelled | Cancellation notice |
| `PAYMENT_FAILED` | Stripe payment failure | Warning, update payment method link |
| `SUBSCRIPTION_CANCELED` | Subscription terminated | Account suspended notice |

Implementation: Spring Mail (SMTP Gmail or SendGrid). Templates as simple text/HTML with variable interpolation.

---

## Cron Jobs

| Schedule | Job | Purpose |
|----------|-----|---------|
| Every 5 min | `SendPendingNotifications` | Pick up PENDING notifications where `scheduled_for <= NOW()` and send them |
| Daily 08:00 CET | `TrialExpirationReminder` | Warn salons with trial expiring in 3 days |
| Daily 03:00 CET | `TrialExpirationCheck` | Expire trials past `trial_end`, suspend salons |
| Daily 03:00 CET | `StripeReconciliation` | Compare local billing state vs Stripe API, fix discrepancies, update Keycloak attributes |
| Every 1 min | `ReminderScheduler` | Find appointments in next 24h/1h with `reminder_sent=false`, create PENDING notifications |

---

## Endpoints (ALL internal)

| Method | Path | Purpose | Called by |
|--------|------|---------|----------|
| POST | `/api/internal/notifications/send` | Send immediately (fire-and-forget) | salon-service (welcome email) |
| POST | `/api/internal/notifications/schedule` | Schedule notifications for future delivery | appointment-service (reminders) |
| DELETE | `/api/internal/notifications/appointment/{appointmentId}` | Cancel scheduled reminders for an appointment | appointment-service (cancellation) |
| POST | `/api/internal/notifications/send-now` | Send immediately with specific type/data | appointment-service (cancellation notice), billing-service (payment alerts) |

---

## Business Rules

1. **Fire-and-forget principle**: calling services do NOT wait for notification success. If sending fails, it's logged and retried by cron.
2. **Retry logic**: max 3 retries for FAILED notifications, then mark as permanently FAILED
3. **Plan-based features**:
   - Email reminders: enabled for BASIC, PREMIUM, ENTERPRISE (disabled for FREE_TRIAL)
   - SMS reminders: enabled for PREMIUM, ENTERPRISE only
4. **Idempotency**: before scheduling, check if notification with same `reference_id + type + scheduled_for` already exists

---

## Dependencies

- **rivoo-common** (security, tenant, observability)
- **Calls**: billing-service (for reconciliation cron), auth-service (for attribute sync in reconciliation), salon-service (for trial expiration — suspend salon)
- **Called by**: salon-service, appointment-service, billing-service
- **External**: SMTP server (Gmail / SendGrid)
