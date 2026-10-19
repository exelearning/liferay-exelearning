---
name: embedded-editor
description: Change the embedded eXeLearning editor integration in Liferay - the postMessage EditorBridge, the editor dialog save flow, EditorServlet, the editor OSGi bundle or the pinned .editor-version.
---

# Embedded editor

Read the code at the boundary you change before editing:

- Browser: `frontend/src/protocol.ts` (typed protocol, `parseEditorMessage`),
  `editor-bridge.ts` (`EditorBridge`), `editor-dialog.ts` (load/save lifecycle), `api.ts`.
- Server: `editor/EditorServlet.java` serves `/o/exelearning-editor/*` from the
  `org.exelearning.liferay.editor` bundle and injects `window.__EXE_EMBEDDING_CONFIG__`
  (with `trustedOrigins`) after `<head>` of `index.html`.
- Build: `scripts/build-editor.sh` builds the pinned tag into `build/exelearning-editor/`;
  `modules/exelearning-editor/build.gradle` packs `static/` plus `LICENSE`, `VERSION`.

Upstream contract: `exelearning/exelearning` `public/app/core/EmbeddingBridge.js` and
`doc/development/embedding.md` at the tag in `.editor-version`. Check them there, not
from memory, when the protocol changes.

## Invariants

- `EditorBridge.receive` accepts a message only when `event.origin` equals the editor
  origin **and** `event.source` is the iframe's `contentWindow`; requests post to that
  exact origin, never `'*'`. Inbound data is `unknown` until `parseEditorMessage` accepts it.
- Loading is two-stage: wait for `EXELEARNING_READY`, then `DOCUMENT_LOADED`, then send
  `OPEN_FILE`. Every request has a `requestId` and a timeout.
- `saveProject()` sends `REQUEST_SAVE` and falls back to `REQUEST_EXPORT` with format
  `elpx` only on an `EditorRequestError` while not disposed: v4.0.5 static builds answer
  `REQUEST_SAVE` with `Export not available`. Timeouts and a closed editor must not fall back.
  Keep the fallback until the pinned editor's `REQUEST_SAVE` works; then remove it with its tests.
- Save success is reported only after `PUT /o/exelearning/v1/resources/{id}/package` stored
  the revision. The request carries `If-Match` with the edited version; 409 shows
  `resource-changed-conflict` and keeps the editor open and dirty.
- Closing with unsaved changes asks for confirmation; `dispose()` rejects pending requests.
- `EditorServlet` returns 404 when `editorEnabled` is off or the editor bundle is missing,
  sends `frame-ancestors 'self'` plus `allowedEditorParentOrigins`, and resolves paths with
  `ElpxEntryNames.normalize`. No editor code is downloaded at runtime.
- New UI strings: add the key to `FrontendStrings.keys` and to both
  `content/Language.properties` and `content/Language_es.properties`.

## Editor version

`.editor-version` pins an upstream tag; `make build-editor` caches per ref
(`EXELEARNING_EDITOR_FORCE=1` rebuilds, `EXELEARNING_EDITOR_REF`/`_REF_TYPE` override it).
`.github/workflows/check-editor-releases.yml` opens the bump PR. Never hand-edit
`build/exelearning-editor/` or substitute `main` for a release.

## Verify

`npx vitest run frontend/test/editor-bridge.test.ts frontend/test/editor-dialog.test.ts
frontend/test/protocol.test.ts`, `make test-coverage`, then with the stack up:
`make build && make deploy` and `make test-e2e PLAYWRIGHT_ARGS='tests/e2e/05-editing.spec.ts'`.
For a version bump also run `tests/e2e/10-failure-recovery.spec.ts` and compare the
upstream bridge diff between the old and new tags.
