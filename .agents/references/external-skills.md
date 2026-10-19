# External skills: provenance and selection

Reviewed 2026-10-19. Vendored skills are kept verbatim, with the `metadata.github-*`
provenance that `gh skills` writes into each `SKILL.md`; local rules in `AGENTS.md` and the
local skills take precedence and survive updates. Licenses are in `../licenses/`.

## Vendored

| Skill | Upstream (path, ref, tree SHA) | License |
|---|---|---|
| `github-actions-hardening` | [github/awesome-copilot](https://github.com/github/awesome-copilot) `skills/github-actions-hardening`, `refs/heads/main`, `25d75a622f5f8c034be5b25bf6585850d6f7fcd7` | MIT, `github-awesome-copilot-MIT.txt` |
| `security-audit` | [cloudflare/security-audit-skill](https://github.com/cloudflare/security-audit-skill) `skills/security-audit`, `refs/heads/main`, `ccbc33ed119b28c3ef7b44a7e2081ab191ad63af` | MIT, `cloudflare-security-audit-MIT.txt` |
| `playwright-cli` | [microsoft/playwright-cli](https://github.com/microsoft/playwright-cli) `skills/playwright-cli`, `refs/tags/v0.1.22`, `6b746b43751be277378e0ee04fe0db69f24da045` | Apache-2.0, `microsoft-playwright-cli-Apache-2.0.txt` |

Copied byte-for-byte from the reviewed copies in the sibling WordPress, Omeka S and
Moodle plugins; license texts match upstream. Local limits:

- `github-actions-hardening`: the eXeLearning plugins use action version tags, not SHA
  pins (see `AGENTS.md`). Apply the rest: least-privilege `permissions`, untrusted values
  through `env:`, `persist-credentials: false` where third-party code runs.
- `security-audit`: only for requested security reviews or audits; routine edits do not
  need the full workflow. Its validators are self-contained Node scripts
  (`node --test .agents/skills/security-audit/*.test.cjs`); they do not follow this repo's
  Biome style, so keep `.agents/skills/` out of Biome rather than reformatting them.
- `playwright-cli`: exploration of the running portal only. The repository's
  `@playwright/test` already provides `npx playwright cli`; do not install a global CLI
  or add dependencies. Tests stay in `tests/` with the existing Playwright config.

## Local skills

`verify-change`, `embedded-editor`, `elpx-package`, `liferay-osgi-module`,
`release-preflight` and `changelog` have no GitHub provenance, so the updater skips them.
All but `liferay-osgi-module` are adapted from the sibling plugins' local skills (Moodle `verify-change`,
`embedded-editor`, `elpx-package`, `release-preflight`, `changelog`; Omeka S `verify`,
`release`, `elpx-editor-boundaries`) and rewritten for this repository's code and commands.
`liferay-osgi-module` was written for this repository; its Source Formatter rules were
checked by running `checkSourceFormatting` and `formatSource` on a probe file.

## Evaluated and not installed

- [liferay/liferay-workspace-skills](https://github.com/liferay/liferay-workspace-skills)
  (official, `2bebcdfed4336df71798e3181adb94f74f98ba7c`): a Claude Code plugin for DXP
  workspaces (Docker/source setup with activation keys, hotfixes, a `liferay-portal`
  source search, Jira-ticketed PR review). It assumes `.liferay-workspace.json`, a portal
  source checkout and Liferay's internal process. The README says MIT but the repository
  has no license file, so nothing is copied. Useful as reading material only.
- [github/awesome-copilot](https://github.com/github/awesome-copilot) Java skills:
  `java-junit` targets JUnit 5 (this repo uses JUnit 4); `java-upgrade` pushes newer
  language features (javac targets `--release 11` here); `javax-to-jakarta-migration`
  would break Liferay 7.4, which uses `javax.portlet`/`javax.servlet`; `java-docs`,
  `java-springboot` and the refactoring skills add nothing over the Source Formatter and
  local guidance.
- `apache/sling-whiteboard` `osgi-scr-migrator`: migrates Felix SCR annotations; this
  repository already uses OSGi DS annotations.
- [anthropics/skills](https://github.com/anthropics/skills): no Java, OSGi or Liferay skills.
- Platform skills of the siblings (Moodle, WordPress, Omeka S, Playground blueprints) do not
  apply to Liferay.

No reputable, permissively licensed Liferay or OSGi development skill was found. Use
https://learn.liferay.com, the Liferay 7.4 GA132 sources and Context7 for API questions.

## Maintenance

```bash
gh skills install github/awesome-copilot skills/github-actions-hardening --dir .agents/skills
gh skills install cloudflare/security-audit-skill skills/security-audit --dir .agents/skills
gh skills install microsoft/playwright-cli skills/playwright-cli --dir .agents/skills
gh skills update --all --dir .agents/skills
```

`.github/workflows/update-agent-skills.yml` runs the update weekly or on dispatch and
opens a review PR; it never merges. Review prompt changes as behavior changes and check
licenses. PRs opened with the default `GITHUB_TOKEN` do not trigger CI automatically.
Expose each skill to Claude Code with a relative symlink
`.claude/skills/<name> -> ../../.agents/skills/<name>`; do not copy prompts.
