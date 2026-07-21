# Repository Guidelines

## Project Goal

EconPulse is a Spring Boot backend that extends an economic glossary with automatically mapped current
news and Redis-backed daily popular terms. Phases 0 through 5 are complete. EconPulse backend MVP and
the Phase 5 operational-readiness scope are complete at version 1.0.0: product flows, automated tests,
CI, bounded operational observability, MySQL query-plan review, and isolated clean-environment
reproduction are verified. This status does not claim production deployment or zero-downtime
operations; product and infrastructure extensions remain in `docs/15-backlog.md`.

The completed flow includes provider-neutral and idempotent news ingestion, pure and idempotent
term-news mapping, public related-news retrieval, and Redis UTC daily term-detail-view rankings.
Popular ranking read failures return 503 while detail-view recording is fail-open. Actuator exposes
only health and info; readiness requires MySQL and Redis but never Naver. Every response includes a
validated or generated `X-Request-Id`, production-default logs are structured JSON, local logs remain
human-readable, and Micrometer metrics stay inside `MeterRegistry` without a public metrics endpoint.

Micrometer Core now records bounded-tag counters and timers for news ingestion, actual Naver HTTP
calls, single-news automatic mapping, and popular-term Redis record/query boundaries. Metrics remain
internal to `MeterRegistry`; Actuator web exposure remains `health,info`, without metrics or
Prometheus endpoints.

MySQL 8 query plans are documented against representative data. Flyway V2 removes only two general
indexes that exactly duplicated UNIQUE indexes; business uniqueness remains the concurrency backstop.
Term list/search pages hydrate aliases in one page-scoped EntityGraph query to avoid N+1 without
collection-fetch pagination. Contains search still uses leading wildcards and is not made indexable by
adding speculative B-trees. The local analysis script must never be run against an operational database.

For a single stored news article, `TermNewsAutoMappingService.mapNews` provides an atomic application boundary: it loads that article and all ACTIVE terms with aliases, evaluates them sequentially with the pure matcher, and joins all mapping saves in one transaction. The conditional `POST /internal/api/v1/news/{newsId}/term-mappings/auto` endpoint exposes only this single-news operation and is disabled by default. It is not invoked automatically by news ingestion.

## Source of Truth

Read the harness documents before changing behavior:

- `docs/01-product-requirements.md`: scope, users, and acceptance criteria
- `docs/02-domain-model.md`: entities, relationships, and business rules
- `docs/03-api-spec.md`: HTTP contracts
- `docs/04-db-schema.md`: MySQL and Redis persistence design
- `docs/05-development-plan.md`: implementation order and phase gates

If implementation requires a contract change, update the affected documents in the same change.

## Intended Project Structure

Application code will use Java 17 and package-by-feature under `src/main/java/com/econpulse`:

```text
term/        # glossary and term search
news/        # news ingestion and retrieval
mapping/     # term-news matching
popular/     # Redis rankings and snapshots
global/      # configuration, errors, shared types
```

Mirror production packages under `src/test/java/com/econpulse`. Keep migrations in `src/main/resources/db/migration` and configuration templates in repository root.

## Development Workflow

All planned phases are complete. For follow-up work, identify the backlog item and its acceptance criteria
before coding, and prefer small changes that preserve the completed API and persistence contracts.
External news providers must remain behind a port and provider DTOs inside adapter packages. The Fake
Adapter is for tests/local development and must not be wired as the production provider. Redis ranking
behavior stays behind its Port; only the documented successful-detail-view facade records scores.

## Style and Naming

Use four-space indentation and standard Java conventions: `PascalCase` types, `camelCase` members, and lowercase package names. Name Spring components by responsibility, such as `EconomicTermService` and `NewsArticleRepository`. Use DTOs at API boundaries; do not expose JPA entities directly. Favor constructor injection, immutable request/response objects, and explicit transaction boundaries.

## Validation Commands

After every change, Codex must run:

```bash
bash -n scripts/*.sh
./scripts/check.sh
```

When Docker Compose configuration exists, also run `docker compose config`. For infrastructure or integration changes, run `./scripts/reset-db.sh` and the relevant integration tests. Report commands that could not run and the reason.

GitHub Actions CI uses the same Gradle quality gates with MySQL and Redis managed by Testcontainers.
For workflow changes, validate YAML structure and GitHub expressions, then run the local CI-equivalent
commands documented in `docs/13-continuous-integration.md`. Do not weaken, skip, disable, or mark any
quality step `continue-on-error`; do not replace Testcontainers with Actions service containers.
Workflows must not contain real Secrets or request write permissions. Confirm official releases before
changing Action major versions, and diagnose CI failures instead of repeatedly rerunning unchanged code.

Clean-environment verification must use a disposable Compose project, distinct host ports, and a
temporary repository copy. Never run `docker compose down -v` against an existing development project
as part of verification. Smoke tests write fixture data, so run them only with explicit opt-in against
the disposable local environment; they must use the Fake Provider and never call an actual external
provider. Shell scripts may terminate only processes whose PID they started, must not print environment
variables or Secrets, and must keep macOS/Linux compatibility. Verify every documented command before
publishing it. After shell changes run `bash -n scripts/*.sh` and the standard `./scripts/check.sh`.

## Commits and Pull Requests

Use concise Conventional Commit subjects, for example `docs: define term-news mapping rules` or `feat: add popular term ranking`. Pull requests must state the phase, summarize contract changes, list verification commands, link issues, and include example requests or screenshots when behavior is user-visible.

## Security

Never commit credentials, production data, or `.env`. Provide `.env.example` with placeholder values. Validate external input, avoid logging secrets or full news payloads, and keep provider credentials behind environment variables.
