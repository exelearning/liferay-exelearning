---
name: release-preflight
description: Audit a liferay-exelearning release candidate and build its ZIP with make package, without tagging or publishing unless the user asked for that.
---

# Release preflight

Sources: `scripts/package.sh`, the `package` target in the `Makefile`,
`.github/workflows/release.yml`, `CHANGELOG.md` and `docs/installation.md`. Obtain the target
version from the request; ask only if it cannot be determined. An audit does not authorize
tags, releases or merges; honor authorization already given in the session.

1. Version metadata: `Bundle-Version` in `modules/exelearning-web/bnd.bnd` and
   `modules/exelearning-editor/bnd.bnd` equals the version without pre-release suffix, and
   `package.json` `version` equals it exactly. `package.sh` refuses the build otherwise.
2. Editor: `.editor-version` is an upstream release tag (not a branch), and the editor is
   built from it (`make build-editor`; first line of `build/exelearning-editor/VERSION` is
   `tag:<tag>`). Do not change `.editor-version` just to test a workflow.
3. Quality gates on the candidate commit: `make lint`, `make test-coverage`, and with the
   stack up `make test-integration` and `make test-e2e`. Old or unexecuted results do not count.
4. Notes: draft or check the `CHANGELOG.md` block with the `changelog` skill.
5. Build in a clean checkout: `make package VERSION=X.Y.Z` (the version passed as one
   quoted argument). It verifies both bundle manifests, the frontend and `Language_es`
   in the web jar, `static/index.html` and the editor license in the editor jar, and rejects
   development files.
6. Inspect `dist/liferay-exelearning-X.Y.Z.zip`: two jars, `LICENSE`, `CHANGELOG.md`,
   `THIRD-PARTY-NOTICES.md`, `SECURITY.md`, `INSTALL.md`, editor license/notices,
   `VERSION` (version, editor ref and revision, Liferay product, source revision) and
   `SHA256SUMS`; no `.agents`, `.claude`, `.env`, tests, fixtures or `node_modules`. Run
   `(cd dist && shasum -a 256 -c liferay-exelearning-X.Y.Z.zip.sha256)` and confirm
   `git status` shows no changes to tracked files.

Report PASS / FAIL / PENDING per item with the evidence, version and commit. Declare the
release ready only when every applicable item passes.
