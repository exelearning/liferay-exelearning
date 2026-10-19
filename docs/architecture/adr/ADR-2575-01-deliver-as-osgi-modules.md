---
id: ADR-2575-01
title: "Deliver the integration as OSGi modules instead of a client extension"
status: Proposed
date: 2026-10-19
tracking_issue: 2575
deciders:
  - "@erseco"
reviewers: []
related:
  prs: []
  changes: []
  adrs: ["ADR-2575-02", "ADR-2575-03", "ADR-2575-04"]
supersedes: []
superseded_by: []
---

# ADR-2575-01: Deliver the integration as OSGi modules instead of a client extension

## Context

Liferay recommends client extensions for new customizations, and they also work on Liferay SaaS.
The integration must validate untrusted ZIP files, store them with versions and permissions, serve
the extracted HTML under a sandbox policy and only behind permission checks, and host a 58 MB
static editor. It must run on Liferay Portal CE 7.4 GA132, where some client extension types are
not available and none can run Java code inside the portal.

## Decision

Deliver two OSGi bundles built with Liferay Workspace against `release.portal.api` 7.4.3.132:

- `org.exelearning.liferay.web`: DS components, a JAX-RS whiteboard application
  (`/o/exelearning/v1`), HTTP whiteboard servlets for content (`/o/exelearning-content`) and the
  editor (`/o/exelearning-editor`), MVC portlets (admin application and widget), a panel app, a
  `ConfigurationAction`, and the compiled TypeScript frontend.
- `org.exelearning.liferay.editor`: the static editor files only (ADR-2575-04).

There is no external service, no client extension and no core modification.

## Alternatives considered

- **Custom Element client extension with a remote microservice:** this keeps Liferay clean, but
  it adds a second runtime to deploy, secure and scale. Packages would be stored outside Documents
  and Media, or the extension would need OAuth2 headless calls to reach it. Permission checks
  would also have to be duplicated. A Custom Element was verified to deploy on GA132, but it
  cannot serve protected content by itself.
- **Client extension only (frontend plus headless Documents and Media):** this cannot set the
  sandbox CSP on the served content, cannot validate archives on the server and cannot extract
  them, so it fails the security requirements.
- **Service Builder module with custom tables:** rejected in ADR-2575-02.

## Consequences

- Installation is "copy two JARs into `deploy`" and works offline. Upgrades follow normal OSGi
  bundle replacement.
- The code depends on Liferay's Java APIs. They are stable within 7.4, but a Liferay major upgrade
  may need recompilation, and Liferay SaaS is not supported.
- Server-side Java is fully unit testable (JUnit, Mockito, JaCoCo) and runs inside Liferay's
  permission and transaction context.

## Validation

- Integration test *deployment* checks that the bundles, the REST API and the editor are
  active. E2E-01 checks the frontend, the editor, the widget, the admin application and the
  custom elements in a browser.
- `make package` verifies both manifests (`Bundle-SymbolicName`, `Bundle-Version`).

## References

- <https://learn.liferay.com/w/dxp/development/client-extensions>
- <https://learn.liferay.com/w/dxp/integration/jax-rs>
- [research.md](../research.md) §2
