# Repository Guidelines

## Project Goal

EconPulse is a Spring Boot backend that extends an economic glossary with automatically mapped current news and Redis-backed real-time popular search terms. Phase 2 and Phase 3 are complete. Phase 3 covers provider-neutral news collection, idempotent storage, pure term matching, idempotent term-news mapping, explicit single-news automatic mapping, and public term-related-news retrieval.

Implemented Phase 3 scope includes provider-neutral news collection, idempotent storage, automatic term matching and the public term-related-news API. A Fake Provider E2E test covers ingestion through public related-news retrieval and repeat-run idempotency. Phase 4 is in progress. Its current scope includes an independent Redis Sorted Set Port/Adapter for UTC daily term-ID search counts with seven-day TTL and an Application Query that joins ranked IDs to ACTIVE MySQL terms in one query. Missing and inactive IDs are filtered and exposed ranks are recalculated. Public popular-term APIs, automatic recording from term APIs, MySQL snapshots and schedulers are not implemented yet.

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

Implement one phase at a time. Before coding, identify the phase and its success criteria. Prefer small changes that preserve documented API and persistence contracts. Phase 3 work must keep external news providers behind a port and must keep provider-specific DTOs inside adapter packages. The Fake Adapter is for tests/local development and must not be wired as the production provider. Phase 4 Redis behavior must remain isolated from completed Phase 2 and 3 flows until an explicit orchestration task connects them.

## Style and Naming

Use four-space indentation and standard Java conventions: `PascalCase` types, `camelCase` members, and lowercase package names. Name Spring components by responsibility, such as `EconomicTermService` and `NewsArticleRepository`. Use DTOs at API boundaries; do not expose JPA entities directly. Favor constructor injection, immutable request/response objects, and explicit transaction boundaries.

## Validation Commands

After every change, Codex must run:

```bash
bash -n scripts/*.sh
./scripts/check.sh
```

When Docker Compose configuration exists, also run `docker compose config`. For infrastructure or integration changes, run `./scripts/reset-db.sh` and the relevant integration tests. Report commands that could not run and the reason.

## Commits and Pull Requests

Use concise Conventional Commit subjects, for example `docs: define term-news mapping rules` or `feat: add popular term ranking`. Pull requests must state the phase, summarize contract changes, list verification commands, link issues, and include example requests or screenshots when behavior is user-visible.

## Security

Never commit credentials, production data, or `.env`. Provide `.env.example` with placeholder values. Validate external input, avoid logging secrets or full news payloads, and keep provider credentials behind environment variables.
