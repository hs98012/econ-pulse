# Repository Guidelines

## Project Goal

EconPulse is a Spring Boot backend that extends an economic glossary with automatically mapped current news and Redis-backed real-time popular search terms. Phase 2 is complete: the economic term glossary API, persistence, migration, tests, local seed policy, and Phase 2 documentation are in place. Phase 3 has started with the news provider Port and Fake Adapter only.

Current Phase 3 scope includes the provider-neutral news search Port, immutable provider request/response models, provider exception model, an in-memory Fake Adapter for tests/local development, and `NewsIngestionService` for idempotent MySQL storage by normalized source URL hash. Real external news provider integration, HTTP clients, automatic term-news mapping, schedulers, and internal sync APIs are not implemented yet. Do not implement Redis popular-search behavior until Phase 4.

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

Implement one phase at a time. Before coding, identify the phase and its success criteria. Prefer small changes that preserve documented API and persistence contracts. Phase 3 work must keep external news providers behind a port and must keep provider-specific DTOs inside adapter packages. The Fake Adapter is for tests/local development and must not be wired as the production provider. Redis behavior belongs to Phase 4 and must not be introduced during Phase 3.

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
