# Development

## Setup

```bash
cp .env.example .env   # choose development passwords
make deps
make up                # first run: pulls images, builds the editor (Bun) and the bundles
make seed
make health
```

Tools: Docker (≈ 6 GB for Liferay + PostgreSQL + the bundled Elasticsearch sidecar), Node.js 22+,
GNU Make, Bun (editor builds from source) and, optionally, JDK 21. Without a local JDK 21,
`scripts/gradlew.sh` runs Gradle in `docker/build` (Temurin 21 + git) with a persistent
`liferay-exelearning-gradle` volume.

## Docker stack

| Service | Image | Notes |
| --- | --- | --- |
| `liferay` | `liferay/portal:7.4.3.132-ga132` (pinned digest) | `127.0.0.1:${LIFERAY_PORT:-8080}`, JPDA `127.0.0.1:8000` when `LIFERAY_DEBUG=true` |
| `db` | `postgres:16.15-alpine` (pinned digest) | volume `db-data` |

Volumes `liferay-data` (Documents and Media store, search index, publication cache) and
`liferay-modules` (deployed bundles) survive `make down`/`make up`; only `make reset` deletes them.
`make deploy` streams changed JARs into the container's deploy folder (as the `liferay` user, with
an atomic rename) and waits until the bundles answer again.

## Everyday loop

| Change | Command |
| --- | --- |
| Java, JSP, language keys | `make build-java deploy` |
| TypeScript or CSS | `make build-frontend build-java deploy` |
| Editor version | edit `.editor-version`, `make build-editor build-java deploy` |
| Formatting | `make fix` |
| Unit tests / coverage | `make test-unit` / `make test-coverage` |
| Integration / E2E | `make test-integration` / `make test-e2e` (`PLAYWRIGHT_ARGS=--headed`) |
| README and issue screenshots | `make reset seed screenshots` (writes `docs/images`) |

Debug Java with `LIFERAY_DEBUG=true` in `.env`, `docker compose up -d liferay` and a remote
debugger on `localhost:8000`. Liferay's log: `make logs`. Gogo shell: `make shell` then
`telnet localhost 11311`.

## Source layout

```text
modules/exelearning-web/src/main/java/org/exelearning/liferay/{elpx,publication,content,resource,rest,editor,portlet,configuration,constants}
modules/exelearning-web/src/main/resources/META-INF/resources/{admin,viewer}/*.jsp
modules/exelearning-web/src/main/resources/content/Language*.properties
modules/exelearning-web/src/test/java/...                   JUnit 4 + Mockito
frontend/src/*.ts, frontend/test/*.test.ts                   custom elements + Vitest
tests/integration/*.spec.ts, tests/e2e/*.spec.ts             Playwright
tests/fixtures/{valid,adversarial}                           packages
```

## Tests

- **Java:** `make test-unit` (JUnit, Mockito inline mocks, `--add-opens` for Liferay kernel
  utilities). Reports: `modules/exelearning-web/build/reports/{tests,jacoco}/`.
- **TypeScript:** Vitest with happy-dom. Coverage: `build/reports/frontend-coverage/`.
- **Coverage gate:** `make test-coverage` runs both with 90 % line thresholds and prints a summary
  (`scripts/coverage-summary.mjs`).
- **Integration:** `tests/integration` calls the deployed REST API, content servlet and headless
  APIs with Basic authentication and inspects the container filesystem.
- **E2E:** `tests/e2e` drives Chromium through the administration screens, the widget and the
  embedded editor. `09-persistence` restarts Liferay. Failures keep screenshots, videos and traces
  in `test-results/` and the HTML report in `playwright-report/`. Firefox:
  `npx playwright test --project=e2e-firefox` (not part of CI).

## Editor builds

`scripts/build-editor.sh` clones `exelearning/exelearning` into `.cache/exelearning-editor/src`,
checks out `EXELEARNING_EDITOR_REF` (`tag` by default, or `branch`/`commit`) and runs
`bun run build:static`. Output goes to `build/exelearning-editor/` with a `VERSION` stamp, so the
same ref is never rebuilt. `EXELEARNING_EDITOR_REF_TYPE=release` downloads the official
`exelearning-static-<tag>.zip` asset instead and requires `EXELEARNING_EDITOR_SHA256`.

## Packaging

```bash
make package VERSION=0.1.0
```

Checks that `VERSION` matches both `Bundle-Version`s and `package.json`, rebuilds everything,
verifies manifests and contents, rejects development files and writes
`dist/liferay-exelearning-<version>.zip` plus `.sha256`.

## Agent skills

See `AGENTS.md` §9. `make update-skills` updates the vendored skills with `gh skill update`.
