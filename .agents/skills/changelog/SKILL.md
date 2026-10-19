---
name: changelog
description: Draft or update liferay-exelearning release notes in CHANGELOG.md from merged GitHub pull requests, keeping the existing style and published entries.
---

# Changelog draft

`CHANGELOG.md` ships in the release ZIP. Describe outcomes for site administrators,
content editors and viewers, not implementation details. This prepares a reviewable
draft; it does not publish a release. Adapted from the changelog workflow of the sibling
eXeLearning plugins.

Use the mode, cutoff and target version already given. Ask only for what cannot be
established reliably:

- **Update draft:** keep the top block and add PRs merged after the last PR already
  included. If that cutoff is unknown, ask; do not guess by matching prose to PR titles.
- **New block:** requested version and today's date; the cutoff is the latest published
  GitHub release. Never invent a version.

```bash
gh release view --repo exelearning/liferay-exelearning --json tagName,publishedAt
gh pr list --repo exelearning/liferay-exelearning --state merged --search 'merged:>TIMESTAMP' \
  --json number,title,body,labels,mergedAt --limit 200
gh pr view NUMBER --repo exelearning/liferay-exelearning --json number,title,body,mergedAt
```

Read each PR body in merge order and paginate past the limit. Issues live in
`exelearning/exelearning` (label `liferay`); follow the links actually referenced. Split
mixed PRs into separate user-facing changes; skip test/CI/tooling-only work, but keep
behavior fixes hidden in test-titled PRs. Unmerged work is not released.

Sections in order, empty ones omitted: `Added`, `Changed`, `Fixed`, `Upgraded`, `Removed`.
Match the existing heading format. Bullets: one sentence, initial capital, no final full
stop, optional area prefix (`Embedded editor:`, `Viewer widget:`, `Documents and Media:`).
Upgrades use `name: OLD → NEW`; the editor version comes from `.editor-version`.
Do not rewrite published blocks.

Report which PRs produced entries and which were skipped and why. Mark the result as a
draft for editorial review.
