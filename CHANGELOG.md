# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/); releases follow the version of the bundled
eXeLearning editor, as the other eXeLearning plugins do.

## [Unreleased]

## [4.0.5] - 2026-10-20

First release, bundling eXeLearning editor v4.0.5. Implements
[exelearning/exelearning#2575](https://github.com/exelearning/exelearning/issues/2575).

### Added

- `.elpx` import with server-side validation of untrusted packages (path rules, forbidden file
  types, duplicates, size, entry count and compression-ratio limits, hardened XML parsing).
- Storage in Documents and Media with versions, metadata as hidden custom fields and Liferay
  permissions; per-site eXeLearning folder.
- Site Administration › Content & Data › eXeLearning: Documents and Media style cards with the
  package screenshot, list view, search, upload, resource page with preview and versions,
  publish/unpublish, download, permissions and delete.
- Atomic publication of a chosen version and a reusable *eXeLearning* widget with stable references.
- Sandboxed content delivery through signed, expiring URLs (`/o/exelearning-content`).
- Embedded eXeLearning editor v4.0.5 (`/o/exelearning-editor`) with the official postMessage
  protocol, optimistic concurrency and server-confirmed saves.
- REST API `/o/exelearning/v1`, system settings, English and Spanish translations.
- Docker Compose development stack (PostgreSQL 18), Makefile, demo seed, CI with 90 % coverage
  gates, Playwright integration and E2E suites, release packaging.
