---
name: doc-contract
description: Keep CLAUDE.md, AGENTS.md and README.md satisfying the enforced documentation contract — required headings, the 32 KB context budget, the module map, and the facts pinned across documents. Use when adding a module, editing any of those files, bumping the Java version, or when the user says "the doc check fails", "moduleMapConsistency", or "context budget".
---

# Doc Contract Skill

In this repository the documentation is a build artifact: `CLAUDE.md`,
`AGENTS.md` and `README.md` are validated by the `claude-code-enforcer` rules,
and a `-DenforceClaudeMd` build fails when they drift. Edit them with the rules
in mind rather than discovering them in CI.

## When to Use
- Adding or removing a Maven module
- Editing `CLAUDE.md`, `AGENTS.md` or `README.md`
- Changing the Java version, or the protobuf major version
- The user says "the doc check fails" / "moduleMapConsistency" / "context budget"

## Who is the source of truth
`AGENTS.md` is the single source of truth. `CLAUDE.md` is a **quick-reference
summary** that defers to it — it is loaded into every session, so
`contextBudget` caps it at **32 KB**. New detail belongs in `AGENTS.md` or in a
skill, not in `CLAUDE.md`.

## Required structure

| Document | Title | Required `##` sections |
|---|---|---|
| `CLAUDE.md` | `# CLAUDE.md` | Project · Java version · Maven · Principles for Java Development · Testing · Dependencies |
| `AGENTS.md` | `# AGENTS.md` | Project overview · Module layout · Environment & toolchain · Build, test, and run · Code style & conventions · Releasing · Pull requests & commits |

Also checked: the title must be the **first non-blank line**; a required section
must have a **non-empty body** (a heading with nothing under it fails);
`CLAUDE.md` must reference `AGENTS.md`; and `memoryImports` validates the
`@`-import graph.

## The consistency rules — what must change together

- **`moduleMapConsistency`** — every `<module>` in the root `pom.xml` must be
  mentioned in **both** `CLAUDE.md` and `AGENTS.md`. Adding a module means
  editing three files in one commit: the pom, the module map in `CLAUDE.md`, and
  *Module layout* in `AGENTS.md`.
- **`crossDocConsistency`** — the `Java (\d+)` pattern must read the same in
  `CLAUDE.md` and `AGENTS.md`. A JDK bump touches both.
- **`readmeConsistency`** — the `proto(\d)` pattern must agree between
  `README.md` and `AGENTS.md`.
- **`contextBudget`** — `CLAUDE.md` ≤ 32 KB.
- **`noSecrets`** — no credentials in `.claude/settings.json`, `.mcp.json`, or
  `.claude/hooks`.

## Skills, settings and hooks are checked too
- `skillFilesExist` — each directory under `.claude/skills` needs a non-empty
  `SKILL.md` opening with YAML front matter carrying `name` and `description`.
  The `name` must be **lower-case kebab-case, ≤ 64 chars, and equal to the
  directory name**; an unknown front-matter key is reported (it catches
  `descripton`).
- `uniqueNames` / `uniqueDescriptions` — two definitions may not share a name or
  a description (compared case- and whitespace-insensitively). Claude routes by
  description, so duplicates make one skill shadow another. Write a description
  that says *what it covers* **and** *when to use it*, with the trigger phrases.
- `settingsJsonValid`, `permissionsFormat`, `hookCommandsValid`, `hooksFormat`,
  `localSettingsIgnored` — `.claude/settings.json`, the hook scripts, and the
  `.claude/settings.local.json` entry in `.gitignore` (removing that entry fails
  the build).

## Running the check
```bash
mvn -pl claude-code-enforcer -am install   # publish the rule locally first
mvn -N validate -DenforceClaudeMd          # root-only doc check, seconds
```
The check is opt-in; only `.github/workflows/maven.yml` runs it in CI
(`mvn -B package -DenforceClaudeMd`).

## Checklist for a doc change
1. Put the detail in `AGENTS.md`; summarise in `CLAUDE.md` only if a session
   needs it every time.
2. Adding a module? Update the root pom, the `CLAUDE.md` module map, and
   `AGENTS.md` *Module layout* together.
3. Changing a pinned fact (Java version, protobuf major)? Change it everywhere
   the pattern appears.
4. Adding a skill? Directory name = front-matter `name`; unique description.
5. Re-run the two commands above before committing.

## References
- `AGENTS.md` — *CLAUDE.md enforcement* (full rule catalogue)
- `docs/adr/0010-documentation-as-enforced-contract.md` — why this exists
- Root `pom.xml` — the `claude-md-enforce` profile's `<rules>` block
