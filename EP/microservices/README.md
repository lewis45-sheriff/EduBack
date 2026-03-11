# EP Microservices (Phase 1 Split)

This folder contains a **phase-1 microservice split** of the EP monolith.
Each service is independently deployable with its own Spring Boot launcher and port.

## What was split

- `auth-service` (port `8081`): auth, users, roles, audit
- `academics-service` (port `8082`): students, grades, academics, exams/scoring
- `finance-service` (port `8083`): fee/invoicing/finance transactions, M-Pesa, bank integration
- `transport-service` (port `8084`): transport and transport transactions
- `communications-service` (port `8086`): announcements/messages
- `reporting-service` (port `8087`): Jasper reports
- `analytics-service` (port `8088`): analytics dashboards
- `gateway` (port `8080`): Nginx route gateway

## Important notes

- This is a **strangler-style initial split**: services are separated at deployment/runtime level first.
- Services currently share one MariaDB instance (`db`) for compatibility during migration.
- `application.properties` in each service is environment-driven with safe defaults.
- Services include package-scoped scanning (`@ComponentScan`, `@EntityScan`, `@EnableJpaRepositories`) to load only their bounded context.

## Run with Docker Compose

From this folder:

```powershell
docker compose up --build
```

Gateway URL:

- `http://localhost:8080`

Direct service URLs:

- `http://localhost:8081` auth
- `http://localhost:8082` academics
- `http://localhost:8083` finance
- `http://localhost:8084` transport
- `http://localhost:8086` communications
- `http://localhost:8087` reporting
- `http://localhost:8088` analytics

## Gateway route map

- `/api/v1/auth/**`, `/api/v1/user/**`, `/api/v1/role/**`, `/api/v1/audit/**` -> `auth-service`
- `/api/v1/students/**`, `/api/v1/grade/**`, `/api/v1/academics/**`, `/api/v1/exams/**`, `/api/v1/enter-marks/**` -> `academics-service`
- `/api/v1/finance-transactions/**`, `/api/v1/invoicing/**`, `/api/v1/payments/**`, `/api/v1/bank/**`, `/api/v1/fee-components/**` -> `finance-service`
- `/api/v1/register-url`, `/api/v1/generate-token`, `/api/v1/validate`, `/api/v1/process-call-back` -> `finance-service`
- `/api/v1/transport/**` -> `transport-service`
- `/api/v1/communication/**` -> `communications-service`
- `/api/v1/reports-j/**` -> `reporting-service`
- `/api/v1/analytics/**` -> `analytics-service`

## Next migration steps (recommended)

1. Move each service to its own schema/database.
2. Replace cross-service JPA coupling with API/event contracts.
3. Introduce service-to-service auth (JWT validation per service).
4. Add CI per service build and health checks.
5. Remove duplicated code by creating shared libs only for true cross-cutting concerns.