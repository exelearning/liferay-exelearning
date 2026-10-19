# Architecture overview

```text
┌────────────────────────────── Liferay Portal / DXP 7.4 ───────────────────────────────┐
│                                                                                       │
│  Browser (portal origin)                       org.exelearning.liferay.web            │
│  ┌─────────────────────────────┐   HTTP        ┌──────────────────────────────────┐   │
│  │ Content & Data › eXeLearning│──────────────▶│ ExeLearningAdminPortlet + JSPs   │   │
│  │ (toolbar, cards, actions)   │  actions      │ EditResourceMVCActionCommand     │   │
│  ├─────────────────────────────┤               ├──────────────────────────────────┤   │
│  │ <exelearning-editor>        │  REST + CSRF  │ JAX-RS  /o/exelearning/v1        │   │
│  │  EditorDialog + EditorBridge│──────────────▶│ ExeLearningRestApplication       │   │
│  │   └ iframe: static editor ◀─┼── postMessage │                                  │   │
│  │      /o/exelearning-editor  │◀──────────────│ EditorServlet (config injection) │   │
│  ├─────────────────────────────┤               ├──────────────────────────────────┤   │
│  │ eXeLearning widget          │               │ ExeLearningViewerPortlet         │   │
│  │  iframe sandbox (opaque)  ◀─┼── signed URL  │ ContentServlet /o/exelearning-   │   │
│  └─────────────────────────────┘               │   content/{token}/… (CSP sandbox)│   │
│                                                └───────────────┬──────────────────┘   │
│                                                                │                      │
│                                       ExeResourceService (permissions, lifecycle)     │
│                                        │             │                │               │
│                     DLAppService/Local │   Expando   │  LockManager   │ PublicationStore
│                                        ▼             ▼                ▼               │
│                          Documents and Media   hidden fields   data/exelearning/      │
│                          (packages+versions)   (metadata,      publications/{company} │
│                                                 published ver)  /{entry}/{version}    │
│                                                                                       │
│  org.exelearning.liferay.editor: static eXeLearning editor files only                 │
└───────────────────────────────────────────────────────────────────────────────────────┘
```

## Components

| Component | Type | Responsibility |
| --- | --- | --- |
| `ElpxArchive`, `ElpxEntryNames`, `ElpxMetadataParser` | Plain Java | Validate and extract untrusted packages; read metadata |
| `PublicationStore` | Plain Java | Stage, verify and atomically commit extracted versions; prune; resolve paths |
| `ExeResourceService` | DS component | Documents and Media adapter, permissions, locks, revision lifecycle, tokens |
| `ResourceAttributes` | Plain Java | Hidden Expando fields: metadata and published version |
| `ExeLearningRestApplication` | JAX-RS | `/o/exelearning/v1` API for the editor and automation |
| `ContentServlet` | HTTP whiteboard | Serves published files and packages behind signed tokens with sandbox headers |
| `EditorServlet` | HTTP whiteboard | Serves the editor bundle and injects `__EXE_EMBEDDING_CONFIG__` |
| `ExeLearningAdminPortlet`, `EditResourceMVCActionCommand`, `ExeLearningAdminDisplayContext` | MVC portlet | Site administration UI |
| `ExeLearningViewerPortlet`, `ViewerConfigurationAction`, `ViewerDisplayContext` | MVC portlet | Widget |
| `frontend/` | TypeScript custom elements | Editor launcher and protocol bridge; viewer full-screen toggle |

## Persistence

- **Documents and Media:** one file entry per resource in the site folder *eXeLearning*; every save
  or upload creates a version (`1.0`, `1.1`, ...). Permissions, workflow status, trash rules and
  site scoping are Liferay's.
- **Expando (hidden):** `exelearningAuthor`, `exelearningLicense`, `exelearningLanguage`,
  `exelearningVersion`, `exelearningPublishable` (latest version), `exelearningPublishedVersion`.
- **Filesystem cache:** extracted versions under `data/exelearning/publications`; only the published
  and latest versions are kept, others are rebuilt on demand.

No custom database tables (see ADR-2575-02).

## Revision lifecycle

1. **Validate** the uploaded bytes (`ElpxArchive.inspect`), bounded by `maxUploadSize`.
2. **Prepare** the HTML export in a private staging directory and verify `index.html`.
3. **Persist** the new Documents and Media version (`DLAppService.addFileEntry`/`updateFileEntry`)
   — updates require `If-Match` with the edited version and a cluster lock.
4. **Commit** the staging directory to `{company}/{entry}/{version}` with an atomic move.
5. **Publish** (explicit action): check the version is approved, ensure its publication exists, and
   switch `exelearningPublishedVersion`. Widgets reference the document ID, so every page shows
   the new version immediately.
6. **Clean up** staging on any failure; prune cached versions that are neither published nor latest.

## Embedded editor message flow

```text
Liferay page (EditorBridge)                      Editor iframe (EmbeddingBridge, v4.0.5)
  create iframe /o/exelearning-editor/index.html
                                 ◀── EXELEARNING_READY {version, capabilities}   (stage 1)
                                 ◀── DOCUMENT_LOADED {projectId, pageCount}       (stage 2)
  GET package (REST, CSRF)
  OPEN_FILE {bytes, filename} ──▶
                                 ◀── OPEN_FILE_SUCCESS {requestId}
                                 ◀── EXELEARNING_EVENT PROJECT_DIRTY
  Save: REQUEST_SAVE ──────────▶
                                 ◀── SAVE_FILE {bytes} | REQUEST_SAVE_ERROR
        (on error) REQUEST_EXPORT {format: elpx} ──▶
                                 ◀── EXPORT_FILE {bytes}
  PUT package (If-Match: "1.0") → 200 new version | 409 conflict | 400 invalid
  "Saved as version 1.1" only after 200
```

The bridge accepts only messages whose `source` is the editor window and whose `origin` is the
editor origin, correlates responses by `requestId`, times out unanswered requests and never posts
to `*`. The editor accepts messages only from `trustedOrigins` (the portal origin plus
`allowedEditorParentOrigins`). v4.0.5's `REQUEST_SAVE` lacks the static build's exporter fallback,
hence the documented `REQUEST_EXPORT` fallback.

## Authentication and authorization

- Portal sessions (with CSRF token) or Basic authentication for the REST API; guests have no REST
  access. Service Access Policies stay enabled.
- `ExeResourceService` checks Documents and Media permissions on every call: VIEW (read), UPDATE
  (save, publish, unpublish, preview other versions), DELETE, PERMISSIONS; adding needs the
  eXeLearning application permission and Documents and Media *Add Document*.
- Invisible resources are reported as not found; resources must belong to the requested site and
  company; widgets only show resources of their own site.
- Signed content tokens carry the authorization decision to the sandboxed content, which has no
  session.

## Content isolation

See ADR-2575-03 and `SECURITY.md`: sandboxed iframe without `allow-same-origin`, CSP `sandbox` on
every response, `frame-ancestors 'self'`, `nosniff`, restrictive `Permissions-Policy`,
`strict-origin-when-cross-origin` referrer policy, signed expiring URLs, Range support for media.
