---
id: ADR-2575-04
title: "Bundle a pinned static eXeLearning editor in its own OSGi bundle"
status: Proposed
date: 2026-10-19
tracking_issue: 2575
deciders:
  - "@erseco"
reviewers: []
related:
  prs: []
  changes: []
  adrs: ["ADR-2575-01"]
supersedes: []
superseded_by: []
---

# ADR-2575-04: Bundle a pinned static eXeLearning editor in its own OSGi bundle

## Context

eXeLearning 4 ships a static build (`bun run build:static`) that runs without a server and
supports an embedding protocol (`postMessage`: `OPEN_FILE`, `REQUEST_SAVE`, `REQUEST_EXPORT`,
...). The editor reads `window.__EXE_EMBEDDING_CONFIG__` from its own window and needs the same
origin as its parent for IndexedDB, its Service Worker and the trusted-origin check. The build is
about 58 MB and 3,000 files. The WordPress, Omeka S and Moodle integrations build it from a tag
pinned in `.editor-version`.

## Decision

- Pin the editor in `.editor-version`, currently `v4.0.5`. `make build-editor` clones that tag (or
  downloads a release asset verified with `EXELEARNING_EDITOR_SHA256`) and runs the static build
  with Bun. The output is cached per ref.
- Package the output in `org.exelearning.liferay.editor`, which contains no code. The editor JAR
  can then be updated or audited independently, and the web bundle stays small.
- `EditorServlet` serves files from that bundle at `/o/exelearning-editor/`. It injects
  `__EXE_EMBEDDING_CONFIG__` (trusted origins, locale, `hideUI` options) into `index.html` and sets
  `frame-ancestors` to the portal plus `allowedEditorParentOrigins`. Only users with UPDATE
  permission on the resource can open it from the UI. The editor's save goes through the REST
  API, which checks permissions again.
- The bridge uses the official messages: `REQUEST_SAVE` first, then `REQUEST_EXPORT` with format
  `elpx` when v4.0.5 answers `Export not available`.

## Alternatives considered

- **Load the editor from a CDN or `exelearning.net` at runtime:** this breaks offline and
  intranet installations, makes the running editor version unknown, and puts third-party code in
  the portal origin. Rejected.
- **Put the files in the web bundle's `META-INF/resources`:** this works, but a 26 MB web JAR
  slows every redeploy and mixes upstream files with project code.
- **Separate editor host:** supported as an option through `allowedEditorParentOrigins`, but not
  the default, because it needs an extra deployment.
- **Build from `main`:** not reproducible. Releases always use a tag.

## Consequences

- The editor runs in the portal origin. A package opened in the editor is processed by
  eXeLearning's own code and sanitizer in that origin, so only users with UPDATE permission can
  open it (see SECURITY.md).
- Updating the editor means editing `.editor-version`, rebuilding, and running E2E-05/06.
  `check-editor-releases.yml` proposes these updates.
- Release archives include the editor's AGPL license and third-party notices.

## Validation

- `EditorServletTest` covers serving files, configuration injection (including script breakout
  escaping), the CSP, and disabled or missing editors.
- `frontend/test/editor-bridge.test.ts` covers the protocol, including the `REQUEST_EXPORT`
  fallback, origin and source filtering, and timeouts.
- E2E-05/06 opens the real editor, edits, saves as version 1.1 and republishes.

## References

- eXeLearning core `doc/development/embedding.md`, `public/app/core/EmbeddingBridge.js` at
  `v4.0.5` (`63748a3d87c8c3a757ef5f3479945db871ddb4c2`)
- `wp-exelearning`, `omeka-s-exelearning`, `moodle-mod_exelearning` editor build scripts
