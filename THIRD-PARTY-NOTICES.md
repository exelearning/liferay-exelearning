# Third-party notices

## eXeLearning editor (bundled in release packages)

- **Bundle:** `org.exelearning.liferay.editor` (`static/`), built from
  [exelearning/exelearning](https://github.com/exelearning/exelearning) at the tag in
  `.editor-version` (v4.0.5, commit `63748a3d87c8c3a757ef5f3479945db871ddb4c2` for 0.1.0) with
  `bun run build:static`. The exact ref and commit are recorded in the bundle at
  `META-INF/exelearning-editor/VERSION` and in each release's `VERSION` file.
- **License:** GNU Affero General Public License v3.0 or later. The upstream `LICENSE` and
  `THIRD-PARTY-NOTICES.md` are shipped in the bundle (`META-INF/exelearning-editor/`) and in the
  release archive (`EXELEARNING-EDITOR-*`).
- The static distribution contains third-party libraries (for example jQuery, Bootstrap, TinyMCE,
  MathJax) with their original license headers; see the upstream repository for their licenses.
- The editor is not modified. Its source code is available from the upstream repository at the
  recorded commit.

## Test fixtures

Packages in `tests/fixtures/valid/` are used for tests and the development demo only and are not
part of release packages. See `tests/fixtures/README.md` for their origin and licenses.

## Build and test tooling

Development dependencies (Gradle, the Liferay Workspace plugin, JUnit, Mockito, JaCoCo, Apache CXF,
TypeScript, esbuild, Biome, Vitest, happy-dom, Playwright) are used to build and test the project
and are not distributed in release packages. The OSGi bundles embed no third-party Java libraries;
they use the APIs provided by Liferay at runtime.

## Agent skills

Vendored agent skills in `.agents/skills/` keep their upstream licenses in `.agents/licenses/`
(see `.agents/references/external-skills.md`). They are not part of release packages.
