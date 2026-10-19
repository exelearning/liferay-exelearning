---
id: ADR-2575-02
title: "Store packages in Documents and Media with hidden Expando metadata and no custom tables"
status: Proposed
date: 2026-10-19
tracking_issue: 2575
deciders:
  - "@erseco"
reviewers: []
related:
  prs: []
  changes: []
  adrs: ["ADR-2575-05", "ADR-2575-06"]
supersedes: []
superseded_by: []
---

# ADR-2575-02: Store packages in Documents and Media with hidden Expando metadata and no custom tables

## Context

Each eXeLearning resource is one `.elpx` file. It has versions, permissions and site scope, plus a
few fields read from `content.xml`: author, license, language and eXeLearning version. Each
resource also needs a pointer to its published version. Liferay already offers versioned,
permissioned and site-scoped file storage that works with the trash, export/import, staging and
the configured store (filesystem, S3, database).

## Decision

- Store each resource as a Documents and Media file entry in a per-site folder named
  *eXeLearning*. That folder is created with Liferay's default folder permissions (owner only, no
  site-member or guest permissions added). Uploads and saves create Documents and Media versions.
- Keep the extracted metadata (`exelearningAuthor`, `exelearningLicense`, `exelearningLanguage`,
  `exelearningVersion`, `exelearningPublishable`) and `exelearningPublishedVersion` in Expando
  attributes. These are marked hidden, so Liferay's UI does not show them as editable custom
  fields.
- Keep extracted HTML in a filesystem cache, `${liferay.home}/data/exelearning/publications`,
  which can always be rebuilt from Documents and Media. Only the published and latest versions are
  kept.

## Alternatives considered

- **Service Builder entity:** this would duplicate what Documents and Media already provides
  (versions, permissions, trash, staging). It would also need upgrade steps and cleanup on
  uninstall, plus a separate permission model.
- **Liferay Objects:** this is not well suited to binary versioning on 7.4 CE. Its attachment
  fields do not keep a version history.
- **Extract into Documents and Media folders:** this creates thousands of file entries per
  package, makes atomic publication impossible, and exposes the extracted files through the normal
  Documents and Media URLs without the sandbox.

## Consequences

- No database schema and no upgrade processes. Uninstalling leaves ordinary documents and Expando
  columns behind.
- Documents remain visible in Documents and Media. Their lifecycle (trash, permissions) is
  Liferay's own, and the integration reads the workflow status before publishing.
- The extraction cache is node-local. In a cluster, each node rebuilds missing versions on first
  request, which costs one extraction per node.
- Expando reads and writes bypass permission checks. They are only called after
  `ExeResourceService` has checked the file entry permission.

## Validation

- `ExeResourceServiceTest` and `PublicationStoreTest` cover storage, metadata, pruning and the
  rebuilding of the cache.
- E2E-09 restarts the portal and checks that resources, published versions and widget references
  persist.
- Integration test *resources of a restricted site are invisible to non-members* asserts that
  Site Member gets only `VIEW` on the folder.

## References

- [overview.md](../overview.md) "Persistence"
- [research.md](../research.md) §7
