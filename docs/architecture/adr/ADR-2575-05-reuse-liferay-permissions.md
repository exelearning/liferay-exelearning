---
id: ADR-2575-05
title: "Reuse Documents and Media permissions and gate authoring with the application permission"
status: Proposed
date: 2026-10-19
tracking_issue: 2575
deciders:
  - "@erseco"
reviewers: []
related:
  prs: []
  changes: []
  adrs: ["ADR-2575-02", "ADR-2575-03"]
supersedes: []
superseded_by: []
---

# ADR-2575-05: Reuse Documents and Media permissions and gate authoring with the application permission

## Context

Resources are Documents and Media file entries (ADR-2575-02), so they already have per-entry
permissions, role-based defaults and an inheritance model. However, Liferay grants *Add Document*
to Site Member by default, which was verified on new folders in GA132. With Documents and Media
permissions alone, every site member could upload eXeLearning packages, and those packages can
contain arbitrary JavaScript.

## Decision

- All user operations go through `ExeResourceService`, which calls the permission-checking
  `DLAppService`:
  - VIEW to read, preview the published version and get a content token;
  - UPDATE to save, publish, unpublish and preview other versions;
  - DELETE to delete;
  - PERMISSIONS to manage permissions with Liferay's standard permissions dialog.
- Adding a resource also requires `ACCESS_IN_CONTROL_PANEL` on the eXeLearning admin portlet in
  that site, as well as *Add Document* on the folder.
- A resource the user cannot view is reported as not found. Requests are also checked against the
  site and the company of the resource. A widget can only show resources of its own site, and that
  is checked when its configuration is saved as well as when it renders.
- The REST API accepts only authenticated users, with Liferay's session plus a CSRF token or with
  Basic authentication. Service Access Policies stay enabled.
- The development seed creates two site roles: `eXeLearning Author` (add, and update own) and
  `eXeLearning Editor` (update any). These are examples, not a required model.

## Alternatives considered

- **Custom resource actions (`exelearning.add`, `exelearning.publish`) via `resource-actions`
  XML:** finer-grained, but it duplicates Documents and Media's model, and the permissions on
  Documents and Media's own UI would no longer match. Publish stays tied to UPDATE, as it is for
  web content.
- **Rely on Documents and Media alone:** this lets any site member upload executable content.
  Rejected.

## Consequences

- Administrators manage access with familiar tools: site roles, the application permission, and
  the per-resource *Permissions* action.
- Publishing cannot be granted separately from editing. If that is needed, a custom action can be
  added later without changing storage.
- Liferay workflow is respected: only approved versions can be published.

## Validation

- `ExeResourceServiceTest` covers every permission path, hidden resources and files outside the
  site's *eXeLearning* folder.
- The *authorization* integration tests cover viewers, authors, editors, guests, restricted
  sites and browser requests without the CSRF token.
- E2E-07 runs the same checks through the UI and with direct requests.

## References

- [SECURITY.md](../../../SECURITY.md) "Authentication, authorization and CSRF"
- [research.md](../research.md) §6
