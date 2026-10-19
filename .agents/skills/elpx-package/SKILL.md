---
name: elpx-package
description: Change how .elpx packages are inspected, extracted, published or served in the Liferay integration (ElpxArchive, ElpxEntryNames, ElpxMetadataParser, PublicationStore, ContentServlet, ExeResourceService revisions).
---

# ELPX packages

Packages are untrusted input. Read the classes on the path you change, in
`modules/exelearning-web/src/main/java/org/exelearning/liferay/`:
`elpx/ElpxArchive`, `elpx/ElpxEntryNames`, `elpx/ElpxMetadataParser`,
`publication/PublicationStore`, `content/ContentServlet`, `content/ContentToken`,
`resource/ExeResourceService`. Limits come from `ExeLearningConfiguration`
(`maxUploadSize`, `maxEntries`, `maxExtractedSize`, `maxCompressionRatio`).

## Rules that must hold

- Every entry name goes through `ElpxEntryNames.normalize` (rejects empty, over-long,
  absolute, backslash, drive/URL-scheme, control-character and `..` names) and files through
  `isForbidden` (server config such as `.htaccess`/`web.config`, PHP-family and server-script
  extensions, after stripping trailing dots/spaces). Add rules there, not in callers.
- `inspect` rejects the whole package on the first bad entry, detects duplicates
  case-insensitively, sums declared sizes and checks the compression ratio. `extract`
  re-applies every rule, counts the bytes actually inflated (lying headers), writes with
  `CREATE_NEW` and checks `path.startsWith(root)` after `normalize()`.
- A valid package has root `content.xml`; `contentv3.xml` alone is a legacy `.elp` and is
  rejected. Publishable means a root `index.html`; packages without it are stored but not published.
- `ElpxMetadataParser` uses StAX with `SUPPORT_DTD=false`, external entities off and a
  resolver that throws. Real exports declare `<!DOCTYPE ode SYSTEM "content.dtd">`; do not
  enable DTD loading or entity expansion to make a fixture pass. Values and property counts are capped.
- Revisions: inspect, stage into `.staging/<uuid>` and verify, store the Documents and Media
  version, `commit` with `ATOMIC_MOVE`, then switch the published pointer. Any failure deletes
  only the staging data; the previously published revision keeps working. Published
  directories `{companyId}/{fileEntryId}/{version}` are immutable and rebuildable from D&M.
- Content is served only by `ContentServlet` at `/o/exelearning-content/{token}/{path}` with a
  signed `ContentToken`, `PublicationStore.resolve` (no symlinks, contained paths) and the
  sandbox CSP without `allow-same-origin`. Do not widen the sandbox or serve the cache directly.
- Validation failures throw `ElpxException(languageKey, args...)`; the REST API returns the key
  as `code` with HTTP 400 and `admin/errors.jspf` renders it. New keys go in both
  `content/Language.properties` and `content/Language_es.properties`.

## Tests

Unit: `ElpxArchiveTest`, `ElpxEntryNamesTest`, `ElpxMetadataParserTest`,
`PublicationStoreTest`, `ContentServletTest`, `ExeResourceServiceTest`; build archives in
memory with `test/TestZips`. Black-box: `tests/fixtures/adversarial/*.elpx` (regenerate with
`node --experimental-strip-types tests/fixtures/generate-adversarial.ts`) mapped to expected
codes in `tests/integration/api.spec.ts`; `tests/e2e/08-security.spec.ts` covers traversal and
the opaque origin. Every new rule gets a unit test and, when it is reachable by upload, an
adversarial fixture plus its expected code. Include a failure that must leave the previous
revision published.
