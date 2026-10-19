# Security Policy

## Supported versions

Only the latest release receives security fixes.

| Version | Supported |
| --- | --- |
| Latest | Yes |
| Older | No |

## Reporting a vulnerability

Please report vulnerabilities privately:

- **Preferred:** [GitHub private vulnerability reporting](https://github.com/exelearning/liferay-exelearning/security/advisories/new)
  for this repository.
- **Alternative:** e-mail `ate.educacion@gobiernodecanarias.org`.

Do not disclose details in public issues. You will receive an acknowledgment within 3 working days;
confirmed issues are fixed as a priority and announced in the release notes.

## Security model

eXeLearning packages are authored content and may contain arbitrary HTML and JavaScript. The
integration treats every uploaded or edited package as **untrusted**.

### Package validation (server)

Before a package is stored, `ElpxArchive` inspects the ZIP central directory and rejects the whole
package on the first problem:

- entry names that are absolute, contain `..`, backslashes, drive letters or URL schemes, control
  characters or excessive length;
- duplicates, including case-insensitive ones;
- server configuration or server-executable files (`.htaccess`, `web.config`, `.php*`, `.jsp*`,
  `.asp*`, `.cgi`, `.pl`, `.py`, ...), following the WordPress and Omeka S integrations;
- more files, more decompressed bytes or a higher compression ratio than configured; extraction
  counts the bytes actually inflated, so forged size headers cannot bypass the limits;
- missing `content.xml`, legacy `contentv3.xml` projects and non-ZIP files.

`content.xml` is read with a streaming StAX parser with DTDs and external entities disabled.
Extraction writes regular files only (never symlinks) into a private staging directory outside the
web application, verifies it and moves it into place atomically; failed attempts leave nothing
behind.

### Content isolation (browser)

Published content is served by `/o/exelearning-content/{token}/…`:

- The iframe uses `sandbox="allow-scripts allow-popups allow-forms allow-downloads
  allow-popups-to-escape-sandbox"` **without** `allow-same-origin`, the same policy as
  eXeLearning's public viewer (ADR-348-01 in the eXeLearning repository).
- Every response repeats the sandbox in `Content-Security-Policy`, so the content keeps an opaque
  origin even when its URL is opened directly. It also sets `frame-ancestors 'self'`,
  `object-src 'none'`, `base-uri 'none'`, `X-Content-Type-Options: nosniff`, a restrictive
  `Permissions-Policy` and `Referrer-Policy: strict-origin-when-cross-origin`.
- Content cannot read the portal's cookies, storage or DOM, and its requests carry no session.
- The token is an HMAC-SHA256 signature (keyed with the virtual instance's secret key) over the
  company, resource, version and expiry. It is issued only after a Liferay permission check, expires
  after `contentURLLifetimeMinutes` (8 hours by default) and cannot be reused for other versions,
  resources or instances.

### Authentication, authorization and CSRF

- The REST API requires an authenticated user. Browser sessions must send Liferay's CSRF token
  (`x-csrf-token` or `p_auth`); Basic authentication is accepted for automation. Guests get
  published content only through signed content URLs.
- Every operation goes through the permission-checking Documents and Media services: VIEW to read,
  UPDATE to edit, publish, unpublish or preview other versions, DELETE to delete, PERMISSIONS to
  manage permissions. Adding resources also requires the eXeLearning application's
  *Access in Site Administration* permission because Documents and Media lets every site member add
  documents by default.
- Resources a user cannot view are reported as not found, so identifiers cannot be probed. Site and
  virtual instance boundaries are checked on every call.
- Saves send the edited version in `If-Match`; stale editors get `409 Conflict`, and a cluster-wide
  lock prevents concurrent saves of the same resource.

### Teacher mode is not access control

The widget can append `?exe-teacher=1` for users who can edit the resource, which only shows
eXeLearning's teacher-mode toggle. Teacher-only content is still delivered to every viewer of the
package; never put confidential answers in it.

### The embedded editor

The editor is trusted, version-pinned code bundled in the release; nothing is downloaded at
runtime. It needs the portal origin (IndexedDB, Service Worker), so while editing, a package's
content is processed by the editor in the portal origin, with eXeLearning's own sanitizer. Only
users with UPDATE permission can open it. For stricter isolation, serve the editor from a separate
host and list the portal origin in *Allowed Editor Parent Origins* (see
[docs/configuration.md](docs/configuration.md)).

### Deployment

- No credentials are hard-coded; the development stack reads them from `.env`, which is ignored by
  Git. The demo users and the `LPS-178052` feature flag exist only in the development stack.
- Release archives are checked for development files, carry SHA-256 checksums and build provenance
  attestations.
