---
id: ADR-2575-06
title: "Version every save, publish explicitly and reference resources by document ID"
status: Proposed
date: 2026-10-19
tracking_issue: 2575
deciders:
  - "@erseco"
reviewers: []
related:
  prs: []
  changes: []
  adrs: ["ADR-2575-02"]
supersedes: []
superseded_by: []
---

# ADR-2575-06: Version every save, publish explicitly and reference resources by document ID

## Context

Authors save work in progress that should not appear on live pages. One resource may be embedded
on many pages. Saves from two editors can conflict. Extracting a package takes time and can fail,
and a failed update must never break what visitors see.

## Decision

- **Versions:** every upload or editor save adds a Documents and Media version (`1.0`, `1.1`,
  ...). Saves send the edited version in `If-Match`. A save without it gets `428`, and a stale
  version gets `409`. A cluster lock (`LockManager`) around each save prevents concurrent saves of
  the same resource.
- **Publication pointer:** `exelearningPublishedVersion` names the version visitors see. It changes
  only with an explicit *Publish* or *Unpublish*. Saving never changes it.
- **Order:** validate → extract into a staging directory and verify `index.html` → persist the
  Documents and Media version → move the staging directory to `{company}/{entry}/{version}` →
  switch the pointer when publishing → prune versions that are neither published nor latest. Any
  failure discards the staging directory and leaves the previous publication untouched.
- **Stable references:** widgets store the file entry ID, never a version or a URL. Every widget
  therefore shows the currently published version without reconfiguration, and signed URLs are
  generated when the page renders.

## Alternatives considered

- **Publish on save:** simpler, but it exposes unfinished edits and is incompatible with workflow.
- **Store the version in each widget:** this needs a bulk update of every page on each
  publication, and pages drift apart.
- **Last-writer-wins saves:** this silently loses edits when two people edit the same resource.

## Consequences

- Visitors never see a partially extracted package. An extraction failure is reported to the
  author and leaves the site unchanged.
- Old versions stay in Documents and Media. Their extracted cache is pruned and rebuilt on demand
  for previews.
- Reverting means publishing an older version. There is no separate rollback mechanism.

## Validation

- `PublicationStoreTest` covers staging, atomic commit, cleanup after failures and pruning.
- `ExeResourceServiceTest` covers stale editors, concurrent saves, approved-only publication and
  storage failures; `ExeLearningRestApplicationTest` covers `If-Match` parsing.
- Integration tests cover versions and the `409`/`428` responses.
- E2E-04 covers reuse on two pages, E2E-05/06 covers saving as 1.1 and then republishing, and
  E2E-10 covers failed saves and publications keeping the published version online.

## References

- [overview.md](../overview.md) "Revision lifecycle"
