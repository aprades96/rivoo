# Rivoo

B2B multi-tenant SaaS platform for hair salons and barbershops in Barcelona.

## Architecture

Maven monorepo with 10 modules (1 shared library + 1 gateway + 8 microservices), running on **Java 25** with **Spring Boot 4**.

```
                    ┌──────────────┐
                    │  Frontend    │
                    │  (React/Next)│
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │  API Gateway │ :8080
                    │  (WebFlux)   │
                    └──────┬───────┘
           ┌───────────────┼───────────────┐
           │               │               │
    ┌──────▼──────┐ ┌──────▼──────┐ ┌──────▼──────┐
    │auth-service │ │salon-service│ │staff-service│
    │    :8081    │ │    :8082    │ │    :8083    │
    └─────────────┘ └─────────────┘ └─────────────┘
    ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
    │client-svc   │ │appoint-svc  │ │notif-svc    │
    │    :8084    │ │    :8085    │ │    :8086    │
    └─────────────┘ └─────────────┘ └─────────────┘
    ┌─────────────┐ ┌─────────────┐
    │billing-svc  │ │admin-svc    │
    │    :8087    │ │    :8088    │
    └─────────────┘ └─────────────┘
```

## Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 25 | Virtual Threads enabled |
| Spring Boot | 4.0.3 | Application framework |
| Spring Cloud Gateway | 2025.1.1 | API Gateway (reactive) |
| MySQL | 8.0 | Database (7 schemas) |
| Keycloak | 26.0.6 | OAuth2/OIDC identity provider |
| Flyway | - | Database migrations |
| MapStruct | - | Compile-time entity mapping |
| Caffeine | - | In-memory cache (plan limits) |
| Lombok | - | Boilerplate reduction |

## Services

| # | Service | Port | DB | Purpose |
|---|---------|------|----|---------|
| - | rivoo-common | - | - | Shared library (security, tenant, observability) |
| 1 | api-gateway | 8080 | - | Routing, JWT validation, CORS, rate limiting |
| 2 | auth-service | 8081 | auth_db | Keycloak Admin API wrapper |
| 3 | salon-service | 8082 | salon_db | Salon profiles, business hours, onboarding |
| 4 | staff-service | 8083 | staff_db | Employees, working hours, services catalog |
| 5 | client-service | 8084 | client_db | Clients, GDPR compliance |
| 6 | appointment-service | 8085 | appointment_db | Bookings, availability, state machine |
| 7 | notification-service | 8086 | notification_db | Emails, reminders, scheduling |
| 8 | billing-service | 8087 | billing_db | Subscriptions, plans, Stripe (stub) |
| 9 | admin-service | 8088 | - | Platform admin dashboard (BFF) |

## Prerequisites

- **Java 25** (JDK)
- **Maven 3.9+**
- **MySQL 8.0** (running on port 3306)
- **Keycloak 26.x** (running on port 9080)

## Quick Start

```bash
# 1. Create databases
mysql -u root -p < infrastructure/mysql/init-local.sql

# 2. Start Keycloak with realm import
cd /path/to/keycloak && bin/kc.sh start-dev --http-port=9080 --import-realm

# 3. Build all modules
mvn clean package -DskipTests

# 4. Start all services
./infrastructure/scripts/dev-start-all.sh

# 5. Verify
curl http://localhost:8080/actuator/health
```

## Key Features

- **Multi-tenancy**: 6-layer isolation (JWT → Gateway → Header → ThreadLocal → Hibernate @Filter → @PrePersist)
- **Hexagonal Architecture**: domain → application → infrastructure in every service
- **Public Booking**: `POST /api/v1/appointments/book` (no JWT, anti-abuse protections)
- **Plan Limits**: FREE_TRIAL (1 employee, 50 appointments/month), BASIC (3/200), PREMIUM (10/unlimited), ENTERPRISE (unlimited)
- **Structured Logging**: ECS JSON with correlationId propagation
- **Rate Limiting**: 100 req/min general, 10 req/min public booking

## API Overview

### Public Endpoints (no JWT)
- `POST /api/v1/salons` — Register new salon
- `GET /api/v1/salons/public/{slug}` — Public salon page
- `POST /api/v1/appointments/book` — Public booking
- `POST /api/webhooks/stripe` — Stripe webhook

### Authenticated Endpoints (JWT required)
- `/api/v1/salons/me` — Salon profile (GET/PUT)
- `/api/v1/staff/employees` — Employee CRUD
- `/api/v1/services` — Service catalog CRUD
- `/api/v1/clients` — Client CRUD + GDPR
- `/api/v1/appointments` — Appointment CRUD + availability
- `/api/v1/billing/subscription` — Subscription management

### Internal Endpoints (PSK)
All `/api/internal/**` endpoints require `X-Internal-Service-Key` header.

## Development

```bash
# Build
mvn clean package -DskipTests

# Run tests
mvn clean test

# Start individual service
java -jar salon-service/target/salon-service-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

## License

Private — All rights reserved.
