# AGENTS.md — eXeLearning for Liferay

Canonical instructions for coding agents and contributors. `CLAUDE.md` only includes this file.

## 1. What this repository is

An integration of [eXeLearning](https://github.com/exelearning/exelearning) `.elpx` projects into
Liferay Portal CE 7.4 GA132 (and, untested, DXP 7.4), implementing
[exelearning/exelearning#2575](https://github.com/exelearning/exelearning/issues/2575).
Issues are tracked in `exelearning/exelearning` with the `liferay` label.

| Path | Responsibility |
| --- | --- |
| `modules/exelearning-web` | OSGi bundle `org.exelearning.liferay.web`: Java, JSP, language keys, compiled frontend |
| `modules/exelearning-editor` | OSGi bundle with the static editor only (no code) |
| `frontend/` | TypeScript: `<exelearning-editor>` launcher, editor bridge, viewer enhancement |
| `tests/` | Playwright integration (`tests/integration`) and E2E (`tests/e2e`), fixtures |
| `scripts/` | Gradle runner, editor build, packaging, dev helpers (wait, health, seed) |
| `docs/` | Installation, development, configuration, troubleshooting, architecture, ADRs |
| `.agents/skills/` | Agent skills (see §9) |

Java packages under `org.exelearning.liferay`:

- `elpx` — package inspection, safe extraction, metadata (pure Java, no Liferay services).
- `publication` — `PublicationStore`: atomic filesystem cache of extracted versions.
- `content` — signed tokens, `ContentServlet` (sandboxed delivery), content types, file responses.
- `resource` — `ExeResourceService` (Documents and Media, permissions, locks, lifecycle),
  `ResourceAttributes` (hidden Expando fields), `ExeResource` (read model).
- `rest` — JAX-RS application `/o/exelearning/v1` and its exception mapper.
- `editor` — `EditorServlet` serving the bundled editor with the embedding configuration.
- `portlet` — admin portlet (Content & Data › eXeLearning), viewer widget, display contexts,
  configuration action, panel app, `action/EditResourceMVCActionCommand`.

## 2. Architecture rules

- Use supported Liferay extension points only: DS components, JAX-RS whiteboard, HTTP whiteboard,
  MVC portlets, panel apps, `ConfigurationAction`, `release.portal.api`. No internal or
  reflective APIs, no core modifications, no `release.dxp.api`.
- Documents and Media is the source of truth for packages and versions. The publication cache
  under `data/exelearning/publications` must always be rebuildable from it.
- Every user-facing operation goes through `ExeResourceService`, which calls the
  permission-checking `DLAppService`. Never use local services for user requests except where the
  service already checked permissions or a signed token authorizes the call.
- Publication order is fixed: validate → stage and verify → persist the DL version → move the
  staged directory into place → switch the published pointer → prune. A failure must leave the
  published version working.
- Untrusted package content is only served by `ContentServlet`, with the sandbox in both the iframe
  attribute and the response CSP (`ContentServlet.SANDBOX`). Never add `allow-same-origin`.
- The editor is pinned by `.editor-version` and bundled at build time. Never download or reference
  editor code at runtime, never build releases from `main`, never modify the upstream bundle.
- Admin UI follows Liferay's own applications (management toolbar, search container, Clay cards,
  `liferay-ui:icon-menu`, `SessionErrors`). Do not reimplement it in custom JavaScript.
- No new runtime dependencies or services without an ADR in `docs/architecture/adr/`.

## 3. Conventions

- **Java:** Liferay Source Formatter is authoritative (`make fix`, `make lint`). Tabs, `_`-prefixed
  private members, private non-primitive constants in `_camelCase`, `GetterUtil` instead of
  `parseLong`, `Objects.equals`, alphabetical members and imports, no final newline. Compile target
  Java 11 (`--release 11`). Field `@Reference` injection; keep logic testable by avoiding static
  portal utilities (`PortalUtil`, `ParamUtil`, `LanguageUtil`) in code that unit tests exercise.
- **JSP:** logic in display contexts, escaped output (`HtmlUtil.escape*`), language keys only.
- **TypeScript:** Biome (4 spaces, single quotes, semicolons), `strict` and
  `noUncheckedIndexedAccess`, no `any`, no global variables, clean up listeners.
- **i18n:** every user-facing string is a key in `content/Language.properties` and
  `Language_es.properties`; the frontend receives translations through `data-i18n`.
- English for code, comments, docs, commits and pull requests. SPDX headers on source files.

## 4. Commands

```bash
make deps                 # install npm packages, Playwright Chromium, Gradle dependencies
make up | make down       # start (build + deploy + wait) / stop, keeping data
make deploy               # redeploy changed bundles into the running portal
make seed && make health  # demo data / readiness
make lint | make fix      # all linters / automatic formatting
make test-unit            # Vitest + JUnit
make test-coverage        # unit tests with the 90 % line coverage gates
make test-integration     # Playwright API tests against the running stack
make test-e2e             # Playwright browser tests against the running stack
make check                # lint + all tests + build
make package VERSION=x.y.z
```

Gradle always runs through `scripts/gradlew.sh` (local JDK 21 or the pinned container). Use module
task paths (`:modules:exelearning-web:test`); root tasks would run the workspace Node plugin.

## 5. Tests and coverage

- Java (JUnit 4 + Mockito + JaCoCo) and TypeScript (Vitest + V8) each need **≥ 90 % line
  coverage**, measured separately, and new or changed code must be covered. Never lower thresholds,
  exclude business logic, skip tests or delete assertions to pass.
- Unit tests use real archives built with `TestZips`/`tests/support/zip.ts`, real Liferay JSON
  objects and stateful mocks; assert behavior, not just calls.
- Integration and E2E tests run against the Docker stack with the deployed bundles and use the
  fixtures in `tests/fixtures/{valid,adversarial}`. They must not rely on sleeps.

## 6. Security boundaries

- Treat every `.elpx`, its HTML, filenames and metadata as untrusted (see `SECURITY.md`).
- Keep the archive rules in `ElpxEntryNames`/`ElpxArchive` and the XML hardening in
  `ElpxMetadataParser`; add an adversarial fixture and tests for any new rule.
- Never accept `?exe-teacher=1` or other UI flags as authorization.
- No secrets in Git; `.env` is local only. Development-only settings stay in `docker-compose.yml`.

## 7. Build and release

- `make build-editor` builds the editor from `.editor-version` (cached per ref). Experiments:
  `EXELEARNING_EDITOR_REF=<branch|sha> EXELEARNING_EDITOR_REF_TYPE=branch|commit`.
- Releases come from `vX.Y.Z` tags via `.github/workflows/release.yml`. The repository keeps the
  placeholder versions `Bundle-Version: 0.0.0` and `package.json` `0.0.0-alpha`; the packaging
  script stamps the release version into both `bnd.bnd` files for the build only.
- `make package` builds, verifies manifests and contents, excludes development files and writes
  SHA-256 checksums.

### Editor updates

`.editor-version` pins an `exelearning/exelearning` release tag. `check-editor-releases.yml` builds
a newer tag and opens a `feature/editor-<tag>` pull request. Review the upstream
`EmbeddingBridge.js` and `doc/development/embedding.md` changes against `frontend/src/protocol.ts`,
close and reopen the PR to run CI, and confirm `tests/e2e/05-editing.spec.ts`. Manual bump: edit
`.editor-version`, `make build-editor build deploy`, `make test-e2e`.

## 8. Prohibited shortcuts

- Disabling portal security (CSRF, Service Access Policies, permission checks) to make something
  work, or adding `allow-same-origin` to the content sandbox.
- Serving raw `content.xml` or unvalidated packages, or extracting into web-accessible folders.
- Reporting a save or publication as successful before the server stored it.
- Fake tests, placeholder implementations, `@Ignore`/`.skip`, coverage exclusions for real code.
- Copying third-party code without checking its license, or editing vendored skills.

## 9. Skills

Load only the matching skill in `.agents/skills/` (linked into `.claude/skills/`). Local skills hold
project rules; vendored ones are generic.

| Skill | Use for |
| --- | --- |
| `verify-change` | Choose and run the checks for a diff |
| `liferay-osgi-module` | Java/JSP/bnd/language keys, Source Formatter, JUnit 4 + Mockito tests |
| `elpx-package` | Package inspection, extraction, publication, content serving |
| `embedded-editor` | Editor bridge, save flow, `EditorServlet`, editor bundle |
| `release-preflight` / `changelog` | Release audit and ZIP; CHANGELOG drafts |
| `github-actions-hardening` | Writing and reviewing workflows |
| `security-audit` | Requested security reviews only |
| `playwright-cli` | Exploring the running portal; tests stay in `tests/` |

Vendored skills are installed with `gh skill install` and keep their `metadata.github-*`
provenance (`.agents/references/external-skills.md`, licenses in `.agents/licenses/`). Update them
with `make update-skills`; `update-agent-skills.yml` proposes weekly pull requests and never merges.
Workflows use action version tags, least-privilege `permissions`, and pass untrusted values through
`env:`.

## 10. Definition of done

1. `make lint` and `make test-coverage` pass; Java and TypeScript stay ≥ 90 %.
2. `make test-integration` and `make test-e2e` pass against a stack built from the change.
3. User-visible changes have E2E coverage; security-relevant changes have adversarial tests.
4. Docs (`README.md`, `docs/`, ADRs, `CHANGELOG.md`) describe the change; English only.
5. No secrets, no unrelated changes, no new dependencies without justification.
