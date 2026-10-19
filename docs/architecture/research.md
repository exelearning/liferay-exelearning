# Research

Research carried out on 2026-10-19 before and during the implementation of
[exelearning/exelearning#2575](https://github.com/exelearning/exelearning/issues/2575).
**Verified** means checked in source code, metadata or a running system; **assumed** means not
verified.

## 1. Existing eXeLearning integrations

| Topic | WordPress (`wp-exelearning`) | Omeka S | Moodle (`mod_exelearning`) | This integration |
| --- | --- | --- | --- | --- |
| Validation | `content.xml` required, legacy rejected, 10 000 entries, 1 GB declared size, unsafe names, server-file blocklist | Same rules, 50 000 entries, counts inflated bytes | `content.xml` required, Moodle file packer | WP/Omeka rules + duplicate, length, control-character, ratio checks and inflated-byte counting |
| Publication | Extract the HTML5 export already inside `.elpx` | Same | Extract per revision, fail without `index.html` | Same principle, atomic per version |
| Delivery | PHP proxy, CSP, `nosniff`; iframe `allow-same-origin` | Similar | `pluginfile.php`, `allow-same-origin` | Opaque origin (core ADR-348-01), signed URLs |
| Editor | Wrapper page injects `__EXE_EMBEDDING_CONFIG__`; `initialProjectUrl`; custom save messages | Same + bridge script | `initialProjectUrl`; `REQUEST_EXPORT` elpx | Official `OPEN_FILE` + `REQUEST_SAVE` (fallback `REQUEST_EXPORT`) |
| Editor build | `make build-editor` from `.editor-version` tag with Bun; never at runtime | Same | Same | Same, with per-ref cache and optional verified release asset |
| Coverage | `MIN_COVERAGE` gate | 90 % gate (PHPUnit text) | Codecov ratchet | JaCoCo and Vitest thresholds, 90 % each |
| Teacher mode | `?exe-teacher=1` flag | Same | Same | Same, presentation only |

Moodle gradebook and SCORM tracking were not carried over (not part of #2575).
Sources: the three repositories at their default branches on 2026-10-19 and the eXeLearning core
(`doc/development/embedding.md`, `public/app/core/EmbeddingBridge.js`,
`src/shared/security/publicViewSandbox.ts`, ADR-348-01).

## 2. Liferay extension mechanisms

| Mechanism | Fit | Notes |
| --- | --- | --- |
| Custom Element client extension (LUFFA) | Partial | **Verified** to deploy and serve static files on CE GA132, but cannot store, validate or serve protected packages; widget settings are free-text properties. |
| JAX-RS whiteboard application | Yes | **Verified** session (with CSRF) and Basic authentication; SAP applies to non-admin users. |
| HTTP whiteboard servlets (`/o/...`) | Yes | Needed for token-based content delivery and the editor wrapper. |
| MVC portlets, panel app, `ConfigurationAction` | Yes | Native admin screen and widget configuration. |
| Documents and Media (`DLAppService`) | Yes | Versions, permissions, site scoping. |
| Liferay Objects / Service Builder | Not needed | Metadata is 1:1 with a document; Expando suffices (ADR-2575-02). |
| Web-ContextPath static resources | Yes | Used for the compiled frontend; editor files go through `EditorServlet` instead. |

## 3. Baseline version

`liferay.workspace.product=portal-7.4-ga132` (**verified** in `releases.json`, promoted,
"recommended", target platform 7.4.3.132, released 2025-02-18). Docker image
`liferay/portal:7.4.3.132-ga132`, index digest
`sha256:5358cea6a39703d31d2d38b275314cde75d490b08ad1a6b5d757e295f0256dd7`, arm64 and amd64.

## 4. Toolchain compatibility

| Item | Value | Status |
| --- | --- | --- |
| Liferay Workspace plugin | 17.1.11 | Verified (latest on Nexus; README "tested with Gradle 8.5") |
| Gradle wrapper | 8.5 (`distributionSha256Sum` pinned) | Verified |
| Build JDK | Temurin 21 (`--release 11`) | Verified |
| Runtime JDK | Zulu 21 in the image | Verified |
| `release.portal.api` | 7.4.3.132 (via the product BOM) | Verified |
| Source Formatter | 1.0.1633 (pinned) | Verified |
| JUnit / Mockito / JaCoCo | 4.13.2 / 5.23.0 / 0.8.14 | Verified |
| Node.js | 22+ (CI uses 24) | Verified locally with 26 |
| Bun (editor build) | 1.4.x | Verified with 1.4.3 |
| PostgreSQL | 16.15 | Works (verified); official matrix for 7.4 lists 12–15 (assumed) |
| DXP 7.4 / quarterly | — | **Assumed** compatible, not tested |
| Firefox | Playwright `e2e-firefox` | Verified locally (E2E 12/12), not part of CI |

## 5. Recommended architecture

Two OSGi bundles (backend + static editor), no client extension and no external runtime; see
ADR-2575-01 and [overview.md](overview.md).

## 6. Authentication and authorization

Liferay sessions and Documents and Media permissions; the eXeLearning admin portlet permission
gates authoring because site members may add documents by default (**verified**: Site Member has
*Add Document* on new Documents and Media folders). See ADR-2575-05.

## 7. Storage and publication

Documents and Media versions plus Expando fields, filesystem cache of extracted versions with
atomic commits, explicit publication of a version, stable references by document ID. See
ADR-2575-02 and ADR-2575-06.

## 8. Tests and CI

JUnit/Mockito/JaCoCo and Vitest/V8 with separate 90 % gates; Playwright black-box integration tests
and E2E against the Docker stack; GitHub Actions with independent lint/test jobs, then build, then
integration and E2E. Liferay's Arquillian integration testing needs a local bundle and JMX, so
black-box tests were chosen instead.

## 9. Packaging

`make package VERSION=x.y.z`: the two bundles, licenses, notices, install guide and version
metadata, checksums and build provenance; the editor is built from the pinned tag. See ADR-2575-04.

## 10. Licensing

The eXeLearning editor and the sibling plugins are AGPL-3.0-or-later; the integration is
AGPL-3.0-or-later and links only against Liferay's public APIs (Liferay Portal CE is
LGPL-2.1-or-later). Fixtures are CC BY-SA 4.0 or AGPL-3.0 (see `tests/fixtures/README.md`). Vendored
agent skills keep MIT/Apache-2.0 licenses.

## Sources

- <https://learn.liferay.com/w/dxp/integration/jax-rs>
- <https://learn.liferay.com/w/dxp/development/client-extensions/working-with-client-extensions>
- <https://learn.liferay.com/w/dxp/installation-and-upgrades/installing-liferay/using-liferay-docker-images/configuring-containers>
- <https://github.com/liferay/liferay-portal/tree/7.4.3.132-ga132> (`PortalSessionAuthVerifier`,
  `AccountAdminApplication`, `WabProcessor`, `DLApp*Service`, `SitePageResourceImpl`)
- <https://github.com/liferay/liferay-portal/tree/master/workspaces/liferay-sample-workspace>
- <https://github.com/liferay/liferay-blade-samples>
- <https://releases-cdn.liferay.com/releases.json>
- <https://repository.liferay.com/nexus/content/groups/public/com/liferay/com.liferay.gradle.plugins.workspace/>
- eXeLearning core `doc/development/embedding.md`, `EmbeddingBridge.js`, ADR-348-01
- `wp-exelearning`, `omeka-s-exelearning`, `moodle-mod_exelearning` repositories
