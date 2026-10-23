# mes-lite

[![CI](https://github.com/juanjguerrer/mes-lite/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/juanjguerrer/mes-lite/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A lightweight **Manufacturing Execution System (MES)** built with Spring Boot and PostgreSQL.

The goal is to track what happens on a shop floor and compute **OEE** (Overall Equipment Effectiveness = availability × performance × quality). The project grows in finished versions: **v0.1** delivered the catalog of production lines and machines, **v0.2** machine state events, and **v0.3** shifts, production counts and the OEE itself, per machine and per line.

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

### Shifts, production counts and OEE (v0.3)

- **Shifts** per line (e.g. A 06:00–14:00, B 14:00–22:00, C 22:00–06:00) in **plant local time**; a shift that crosses midnight belongs to the date it starts. Shifts of a line may touch but never overlap.
- The plant time zone (`mes.plant.zone-id`, default `America/Mexico_City`) turns "shift C of 2026-10-22" into an exact UTC range, daylight-saving changes included (a night shift can last 7 or 9 hours).
- **Production counts**: good and scrap pieces per time interval, reported by line and machine code with the same idempotent ingestion as state events.
- **OEE** per machine, for a free range or a shift and date, and per line through its **reference machine** (usually the bottleneck).

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
| `MES_PLANT_ZONE_ID` | `America/Mexico_City` | no |

Environment variables take precedence over `.env`.

### 3. Run

```bash
./mvnw spring-boot:run
```

Flyway creates the schema on startup. The API listens on `http://localhost:8080`.

To start with sample lines, machines, shifts, state events and production counts, run with the `dev` profile. The seed data lives in a repeatable Flyway migration (`db/seed`) and is only loaded with this profile:

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

### Shifts

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/lines/{lineId}/shifts` | Create a shift → `201` + `Location`; `422` if it overlaps another shift |
| `GET` | `/api/lines/{lineId}/shifts` | Shifts of a line, ordered by start time |
| `GET` | `/api/shifts` | Search shifts across lines (paginated, filterable) |
| `GET` | `/api/shifts/{id}` | Get a shift |
| `PUT` | `/api/shifts/{id}` | Update name and times (the code is immutable) |
| `DELETE` | `/api/shifts/{id}` | Delete a shift → `204` |

Shift times are local `HH:mm` values, e.g. `{"code": "C", "name": "Night", "startTime": "22:00", "endTime": "06:00"}`.

### Production counts

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/lines/{lineCode}/machines/{machineCode}/counts` | Report good/scrap pieces for an interval → `201`; `200` on retry |
| `GET` | `/api/production-counts/{id}` | Get a count |
| `GET` | `/api/machines/{id}/production-counts/totals?from=&to=` | Good, scrap and total pieces within a range |

A count belongs to the range in which its interval **ends** (`from < intervalEnd <= to`), so adjacent ranges never count the same pieces twice. Keep intervals short (one minute) so boundary effects stay small.

### OEE

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/machines/{id}/oee?from=&to=` | OEE of a machine in a free range (max 31 days) |
| `GET` | `/api/machines/{id}/oee?shiftCode=A&date=2026-10-22` | OEE of a machine in a shift |
| `GET` | `/api/lines/{id}/oee?...` | OEE of the line's reference machine (same parameters) |

Pass either `from`/`to` or `shiftCode`/`date`, never both (`400`). To set a line's reference machine, include `referenceMachineId` when updating the line (`PUT /api/lines/{id}`); it must belong to that line, and omitting it clears it.

```text
OEE           = availability x performance x quality
availability  = RUNNING time / planned time
performance   = total pieces x ideal cycle time / RUNNING time
quality       = good pieces / total pieces
planned time  = range - PLANNED_STOP time - unknown time
```

How the numbers are defined:

- Only `RUNNING` produces. `DOWN`, `SETUP` and `IDLE` are losses; `PLANNED_STOP` is not counted as planned time.
- Time without any state (before a machine's first event) is excluded from planned time instead of counting as a loss; `dataCoverage` tells how much of the range had data.
- Performance is not capped at 100%: a value above it is reported with a warning, because it usually means the ideal cycle time is too long.
- A factor that cannot be computed (no ideal cycle time, no pieces, no planned time) is `null`, so is the OEE, and `warnings` explains why.
- Factors are ratios between 0 and 1, rounded to 4 decimals only in the response.

Example: a press with a 12 s ideal cycle, an 8 h shift with 30 min planned stop, 360 min running, 1470 good and 30 scrap pieces:

```json
{
  "machineId": 1,
  "from": "2026-10-19T12:00:00Z",
  "to": "2026-10-19T20:00:00Z",
  "shiftCode": "A",
  "plannedMs": 27000000,
  "runningMs": 21600000,
  "unknownMs": 0,
  "goodCount": 1470,
  "scrapCount": 30,
  "availability": 0.8,
  "performance": 0.8333,
  "quality": 0.98,
  "oee": 0.6533,
  "dataCoverage": 1.0,
  "warnings": []
}
```

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
| `400` | Invalid body (field errors listed in `errors`), malformed JSON, unknown sort property, invalid OEE parameter combination |
| `404` | Line, machine, event, shift or count not found |
| `409` | Duplicate code, `eventId` reused for different data, or deleting something still referenced (a line with machines or shifts, a machine with events, counts or that is a line's reference) |
| `422` | Business rule violated: invalid code format, invalid cycle time, a time more than 5 minutes in the future, an invalid time range, overlapping shifts, or a line without reference machine |
| `500` | Unexpected error (details are logged, never returned) |

## Project structure

Packages are organized by feature, not by layer:

```
src/main/java/io/github/juanjguerrer/meslite/
  common/    shared rules (codes, cycle times), exceptions and the global error handler
  line/      production line entity, repository, service, controller, DTOs
  machine/   machine entity, repository, service, controller, DTOs
  event/     state events: entity, ingestion, queries, duration calculation (Java and SQL)
  shift/     shifts, overlap rule and shift-to-UTC resolution
  production/ production counts: ingestion and totals
  oee/       OEE calculation and endpoints
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
- **Pure functions for the domain math** (`StateDurations`, `ShiftWindow`, `ShiftRules`, `OeeCalculator`): no database or Spring, unit-tested with hand-calculated examples.
- **Shifts are stored as local `TIME`** and converted with the plant zone only when computing, so daylight-saving changes are handled by `java.time`.
- **Known limitation**: the shift overlap check is check-then-insert, so two simultaneous creations could slip an overlap through.

## Roadmap

- [x] **v0.1** Production line and machine catalog
- [x] **v0.2** Machine state events (running, idle, down…)
- [x] **v0.3** Shifts, production counts and OEE
- [ ] **v0.4** Plant simulator over MQTT (later OPC UA)
- [ ] Later: dashboard and charts

## License

[MIT](LICENSE)
