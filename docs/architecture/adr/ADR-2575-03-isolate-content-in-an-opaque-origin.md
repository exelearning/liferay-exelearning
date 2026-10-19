---
id: ADR-2575-03
title: "Serve published content from an opaque origin behind signed URLs"
status: Proposed
date: 2026-10-19
tracking_issue: 2575
deciders:
  - "@erseco"
reviewers: []
related:
  prs: []
  changes: []
  adrs: ["ADR-2575-05"]
supersedes: []
superseded_by: []
---

# ADR-2575-03: Serve published content from an opaque origin behind signed URLs

## Context

Packages contain arbitrary HTML and JavaScript written by authors. If that content ran in the
portal origin, it could read Liferay's cookies, storage, CSRF token and DOM, and act as any user
who views it. The other eXeLearning integrations (WordPress, Omeka S, Moodle) use iframes with
`allow-same-origin`. The eXeLearning core's public viewer
(ADR-348-01-public-view-opaque-origin-isolation) drops it and has shown that eXeLearning's exported
HTML, navigation and iDevices work in an opaque origin. Iframe requests carry no CSRF token or
custom headers, and with an opaque origin they carry no cookies either.

## Decision

- Serve extracted content only through `ContentServlet` at
  `/o/exelearning-content/{token}/{path}`.
- Every response sends `Content-Security-Policy: sandbox allow-scripts allow-popups allow-forms
  allow-downloads allow-popups-to-escape-sandbox; frame-ancestors 'self'; object-src 'none';
  base-uri 'none'`. It also sends `X-Content-Type-Options: nosniff`, a restrictive
  `Permissions-Policy`, and `Referrer-Policy: strict-origin-when-cross-origin`. The widget iframe
  repeats the same `sandbox` attribute (`ContentServlet.SANDBOX`). `allow-same-origin` is never
  granted.
- The token is `fileEntryId-version-expires-HMAC`. The HMAC is SHA-256 over the company, the
  resource, the version and the expiry, keyed with the virtual instance's key. It is issued only
  after a Documents and Media permission check. Expiry is `contentURLLifetimeMinutes` (8 hours),
  rounded up to the hour so that pages stay cacheable.
- A token without a path downloads the package, which is how guests get the download option.

## Alternatives considered

- **`allow-same-origin` like the sibling plugins:** simplest, but any package can take over the
  viewer's session. Rejected.
- **Separate content domain:** strongest isolation, but it needs DNS, TLS and virtual host setup
  that a Liferay site administrator often cannot change. The opaque origin gives equivalent
  isolation on a single host. A separate domain remains possible later with the same token format.
- **Session-authenticated content URLs:** these cannot work, because an opaque origin sends no
  cookies, and adding cookies would require `allow-same-origin`.

## Consequences

- Content cannot use `localStorage`, `IndexedDB` or cookies. eXeLearning's export degrades
  gracefully (activity state is not persisted between visits), as in the core's public viewer.
- Anyone holding a token URL can read that one version until the token expires. This is
  equivalent to the access the viewer already had, and the token cannot be reused for other
  versions, resources or instances.
- Teacher mode (`?exe-teacher=1`) is presentation only and is never access control.

## Validation

- `ContentTokenTest`, `ContentServletTest` and `ContentResponsesTest` cover forged, expired and
  foreign tokens, the sandbox headers, missing versions and Range requests;
  `PublicationStoreTest` covers path resolution inside a publication.
- E2E-08 runs a package that tries to read `parent.document`, `document.cookie` and
  `localStorage`, and asserts an opaque origin. Integration tests assert the response headers and
  that tampered tokens get `403`.

## References

- eXeLearning core `doc/architecture/adr/ADR-348-01-public-view-opaque-origin-isolation.md`
- <https://html.spec.whatwg.org/multipage/iframe-embed-object.html#attr-iframe-sandbox>
- <https://www.w3.org/TR/CSP3/#directive-sandbox>
- [SECURITY.md](../../../SECURITY.md)
