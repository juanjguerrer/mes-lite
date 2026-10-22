# mes-lite

[![CI](https://github.com/juanjguerrer/mes-lite/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/juanjguerrer/mes-lite/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A lightweight **Manufacturing Execution System (MES)** built with Spring Boot and PostgreSQL.

The goal is to track what happens on a shop floor and compute **OEE** (Overall Equipment Effectiveness = availability × performance × quality). The project grows in finished versions: **v0.1** delivered the catalog of production lines and machines; **v0.2** adds machine state events, the raw material for the availability part of OEE.

## Features

### Catalog (v0.1)

- CRUD for **production lines** and their **machines** (one line has many machines).
- Business codes (`LINE-01`, `PRS-003`) normalized to upper case and validated; unique per line for machines.
- Ideal cycle time per machine, accepted in `MILLISECONDS`, `SECONDS`, `MINUTES` or `HOURS` and stored in milliseconds (max 24 h).
- Pagination, sorting and filters (`code` and `name` contain, case-insensitive, combinable with `AND`/`OR`).
### Machine state events (v0.2)

- Machines report state changes (`RUNNING`, `IDLE`, `DOWN`, `SETUP`, `PLANNED_STOP`) by **line code and machine code**, the way the shop floor names them.
- **Idempotent ingestion**: each event carries a client-generated `eventId`; a retry returns `200` with the original event, while reusing an `eventId` for a different event returns `409`.
- Events are **append-only** and ordered by when they happened (`occurredAt`), not by arrival, so late events land in the right place.
- Queries: event history, current state of a machine, current state of every machine in a line (one query, no N+1), and **time spent in each state** within a range.
- State durations are computed in Java and, for learning purposes, also in SQL (CTEs + `LEAD()`); a parity test keeps both in agreement.

### Platform

- Consistent errors using **ProblemDetail** (RFC 9457, `application/problem+json`).
- Schema managed by **Flyway**; Hibernate only validates it.
- Interactive API docs with **Swagger UI**.
- Unit, slice and integration tests; integration tests run against a real PostgreSQL in **Testcontainers**.

## Tech stack

| Area | Choice |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4.1 (Web MVC, Data JPA, Validation) |
| Database | PostgreSQL 18, migrations with Flyway |
| API docs | springdoc-openapi (Swagger UI) |
| Tests | JUnit 5, AssertJ, MockMvc, Testcontainers |
| CI | GitHub Actions |

## Getting started

### Prerequisites

- JDK 25
- PostgreSQL 18 running locally (for the app)
- Docker (for the integration tests only)

You do not need Maven installed: the project ships the Maven Wrapper (`./mvnw`).

### 1. Create the database

```sql
CREATE DATABASE mes_lite;
```

### 2. Configure credentials

Copy `.env.example` to `.env` and set your database password. `.env` is git-ignored.

```bash
cp .env.example .env
```

| Variable | Default | Required |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/mes_lite` | no |
| `DB_USERNAME` | `postgres` | no |
| `DB_PASSWORD` | none | yes |

Environment variables take precedence over `.env`.

### 3. Run

```bash
./mvnw spring-boot:run
```

Flyway creates the schema on startup. The API listens on `http://localhost:8080`.

To start with sample lines, machines and state events, run with the `dev` profile. The seed data lives in a repeatable Flyway migration (`db/seed`) and is only loaded with this profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI spec: http://localhost:8080/v3/api-docs

### 4. Test

```bash
./mvnw test
```

Docker must be running: integration tests start a disposable `postgres:18` container, so they never touch your local database.

## API overview

### Production lines

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/lines` | Create a line → `201` + `Location` |
| `GET` | `/api/lines` | List lines (paginated, filterable) |
| `GET` | `/api/lines/{id}` | Get a line |
| `PUT` | `/api/lines/{id}` | Update name and description (the code is immutable) |
| `DELETE` | `/api/lines/{id}` | Delete a line → `204`; `409` if it still has machines |

### Machines

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/lines/{lineId}/machines` | Create a machine in a line → `201` + `Location` |
| `GET` | `/api/lines/{lineId}/machines` | List the machines of a line |
| `GET` | `/api/machines` | Search machines across all lines |
| `GET` | `/api/machines/{id}` | Get a machine |
| `PUT` | `/api/machines/{id}` | Update name, description and cycle time (the code is immutable) |
| `DELETE` | `/api/machines/{id}` | Delete a machine → `204` |

### Machine state events

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/lines/{lineCode}/machines/{machineCode}/events` | Report a state change → `201` + `Location`; `200` on retry |
| `GET` | `/api/events/{id}` | Get an event |
| `GET` | `/api/machines/{id}/events?from=&to=` | Event history, newest first (paginated, range optional) |
| `GET` | `/api/machines/{id}/state` | Current state; `state` is `null` if the machine has no events |
| `GET` | `/api/lines/{id}/machines/states` | Current state of every machine in a line |
| `GET` | `/api/machines/{id}/state-durations?from=&to=` | Milliseconds per state within `[from, to)` (both required, max 31 days) |

Times are ISO-8601 instants, e.g. `2026-10-22T08:00:00Z`.

### Query parameters for lists

| Parameter | Example | Notes |
|---|---|---|
| `code` | `code=line` | Contains, case-insensitive |
| `name` | `name=assembly` | Contains, case-insensitive |
| `operator` | `operator=OR` | How `code` and `name` combine; default `AND` |
| `page`, `size` | `page=0&size=20` | Default size 20, max 100 |
| `sort` | `sort=code,asc` | Any entity property |

### Example

```bash
curl -i -X POST http://localhost:8080/api/lines/1/machines \
  -H "Content-Type: application/json" \
  -d '{"name": "Press 3", "code": "prs-003", "idealCycleTime": 12, "idealCycleTimeUnit": "SECONDS"}'
```

```json
{
  "id": 1,
  "name": "Press 3",
  "code": "PRS-003",
  "description": null,
  "idealCycleTimeMs": 12000,
  "lineId": 1,
  "createdAt": "2026-10-20T15:04:05.123Z",
  "updatedAt": "2026-10-20T15:04:05.123Z"
}
```

Reporting a state change:

```bash
curl -i -X POST http://localhost:8080/api/lines/LINE-01/machines/PRS-003/events   -H "Content-Type: application/json"   -d '{"eventId": "3f0c8a2e-5b1d-4c7e-9a41-2d6f0b8e7c19", "state": "DOWN", "occurredAt": "2026-10-22T08:15:00Z", "reason": "Jammed die"}'
```

Time per state, where the state at `from` comes from the last event before it and time before the machine's first event counts as `unknownMs`:

```json
{
  "machineId": 1,
  "from": "2026-10-22T08:00:00Z",
  "to": "2026-10-22T12:00:00Z",
  "durationsMs": { "RUNNING": 9000000, "IDLE": 1800000, "DOWN": 3600000, "SETUP": 0, "PLANNED_STOP": 0 },
  "unknownMs": 0
}
```

### Errors

Every error uses the same shape:

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "A machine with the same code already exists in this production line.",
  "instance": "/api/lines/1/machines"
}
```

| Status | When |
|---|---|
| `400` | Invalid body (field errors listed in `errors`), malformed JSON, unknown sort property |
| `404` | Line, machine or event not found |
| `409` | Duplicate code, `eventId` reused for a different event, or deleting a line with machines / a machine with events |
| `422` | Business rule violated: invalid code format, invalid cycle time, `occurredAt` more than 5 minutes in the future, or an invalid time range |
| `500` | Unexpected error (details are logged, never returned) |

## Project structure

Packages are organized by feature, not by layer:

```
src/main/java/io/github/juanjguerrer/meslite/
  common/    shared rules (codes, cycle times), exceptions and the global error handler
  line/      production line entity, repository, service, controller, DTOs
  machine/   machine entity, repository, service, controller, DTOs
  event/     state events: entity, ingestion, queries, duration calculation (Java and SQL)
src/main/resources/db/migration/   Flyway migrations
src/main/resources/db/seed/        dev-only sample data (repeatable migration)
```

Each request flows **controller → service → repository**. Controllers only speak DTOs; entities never leave the service layer.

## Design decisions

- **Flyway owns the schema** and Hibernate runs with `ddl-auto: validate`, so a mismatch between entities and tables fails at startup.
- **Database constraints are the source of truth** for uniqueness and referential integrity; constraint names are mapped to readable error messages.
- **Codes are immutable** because other systems (and the upcoming simulator) refer to machines by code.
- **Deleting a line with machines is rejected** (`ON DELETE RESTRICT`) to protect future production history.
- **Timestamps are `TIMESTAMPTZ`** to avoid ambiguity across shifts and daylight-saving changes; incoming instants are truncated to microseconds to match PostgreSQL.
- **State history is append-only**: events are never edited; durations are derived from consecutive events.
- **Idempotency relies on the database**: the unique `event_id` constraint resolves concurrent retries, not a check-then-insert.
- **Time-dependent rules take a `Clock`**, so tests can fix "now".

## Roadmap

- [x] **v0.1** Production line and machine catalog
- [x] **v0.2** Machine state events (running, idle, down…)
- [ ] **v0.3** Shifts, production counts and OEE
- [ ] **v0.4** Plant simulator over MQTT (later OPC UA)
- [ ] Later: dashboard and charts

## License

[MIT](LICENSE)
