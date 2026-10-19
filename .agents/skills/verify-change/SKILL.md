---
name: verify-change
description: Select and run the checks a liferay-exelearning diff needs (lint, Source Formatter, unit tests, 90% coverage gates, integration and e2e against the Docker stack) before pushing or reviewing.
---

# Verify a change

Inspect the whole diff against the PR base, including untracked files, and follow
callers of changed code. The `Makefile` and `.github/workflows/ci.yml` are the source
of truth for commands; `make help` lists targets. Gradle always goes through
`scripts/gradlew.sh` (local JDK 21, otherwise the pinned Docker build image).

| Change | Validation |
|---|---|
| Java in `modules/exelearning-web` | `make fix`, then `make lint` (runs `checkSourceFormatting` and `compileTestJava`); `make test-coverage` |
| One Java test while iterating | `scripts/gradlew.sh :modules:exelearning-web:test --tests '*ElpxArchiveTest'` |
| JSP, `content/Language*.properties`, portlet properties | `make lint`; affected Playwright spec; both locales have the same keys |
| `frontend/src/**` | `npx vitest run frontend/test/<name>.test.ts`, then `make lint` and `make test-coverage` |
| REST, servlets, permissions, packages | Unit tests plus `make test-integration`; `make test-e2e` when the UI flow changes |
| Editor bridge, `.editor-version`, editor bundle | `embedded-editor` skill; `make build`, `make deploy`, `tests/e2e/05-editing.spec.ts` |
| `scripts/*.sh` | `shellcheck scripts/*.sh` (part of `make lint`) |
| `.github/workflows/*` | `actionlint` (part of `make lint`) and the `github-actions-hardening` skill |
| `docker-compose.yml`, `.env.example` | `docker compose --env-file .env.example config -q` |
| Release packaging | `release-preflight` skill |
| Markdown or skills only | Frontmatter, relative links, provenance of vendored skills; no rebuild |

`make test-integration` and `make test-e2e` need the running stack: `cp .env.example .env`
(set passwords), `make up`, `make seed`. Narrow a run with
`make test-e2e PLAYWRIGHT_ARGS='tests/e2e/05-editing.spec.ts'`. After Java or frontend
edits, `make build && make deploy` before browser tests; `make health` checks the portal.

Coverage gates: JaCoCo LINE >= 0.90 (`jacocoTestCoverageVerification`) and Vitest
lines/statements/functions 90, branches 85 (`vitest.config.ts`). Never lower a threshold,
add exclusions or delete assertions to pass; add a meaningful test instead. Reports land in
`modules/exelearning-web/build/reports/jacoco/test/` and `build/reports/frontend-coverage/`.

`make fix` rewrites files (Biome and Source Formatter); review its diff. `make check` is
lint + all tests + build and needs the stack.

Report each command with its result. Distinguish not applicable from not run; an
unexecuted suite is not a pass, and missing prerequisites (Docker, `.env`, Bun) are
reported, not worked around by skipping gates.
