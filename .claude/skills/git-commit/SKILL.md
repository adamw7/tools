---
name: git-commit
description: Generate conventional commit messages for the tools repo, using its real module scopes. Use when the user says "commit", "create commit", "commit changes", or after completing code changes that need to be committed.
---

# Git Commit Message Skill

Generate conventional, informative commit messages for the `tools` multi-module
Maven reactor.

## When to Use
- After making code changes
- User says "commit this" / "commit changes" / "create commit"
- Before creating a PR

## Format

[Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types
| Type | Use for |
|---|---|
| `feat` | New API / functionality |
| `fix` | Bug fix |
| `refactor` | Restructure, no behavior change |
| `test` | Add/update tests |
| `docs` | Docs only (README, AGENTS.md, javadoc) |
| `perf` | Performance improvement |
| `build` | Maven / pom / CI changes |
| `chore` | Maintenance (tooling, dependency bumps) |

### Scope — use this repo's real modules/components
`data`, `code`, `context`, `protogen-maven-plugin`, `adopt`, `mcp-common`,
`claude-code-enforcer`, `assembly`, `grpc-example`, `data-test`. Narrow to a
component when it's clearer (e.g. `source.db`, `uniqueness`, `mcp`, `builder`).

### Subject rules
- Imperative mood: "Add support", not "Added support"
- Lowercase after the type, no trailing period, ≤ ~50 chars

### Body (optional, recommended for non-trivial changes)
- Explain **what** and **why**, not how; wrap at ~72 chars, 2–3 lines is plenty
- Reference issues: `Fixes #123` / `Relates to #456`
- Breaking change: add a `BREAKING CHANGE:` footer (and `!` after the scope)

## Examples

```
feat(protogen-maven-plugin): enforce proto3 oneof discriminators at compile time

Generated builders now fail compilation when a required oneof branch is
left unset, matching the proto2 required-field guarantee.

Closes #123
```

```
fix(data): close JDBC statement when the result iterator is exhausted

IterativeDbSource leaked a Statement when callers stopped before the last
row. Wrap the cursor in try-with-resources so it closes on every path.

Fixes #456
```

```
refactor(context): extract project-tree assembly from the MCP adapter

Keeps the uniqueness/context core free of any MCP dependency, satisfying
the layering rule pinned by the architecture tests.
```

```
test(claude-code-enforcer): cover duplicate skill descriptions

Add cases for UniqueDescriptionsRule when two SKILL.md files share a
description.
```

```
build(deps): add the shellcheck-maven-plugin with embedded binary resolution
```

## Workflow
1. Inspect staged changes: `git diff --staged --stat`, then targeted diffs.
2. Pick the scope from the changed module(s); split unrelated changes into
   separate commits.
3. Choose the type from the nature of the change.
4. Write the message; commit with `git commit -m "..."` (or a file for the body).

## Anti-patterns
❌ "fix stuff" / "update code" / "WIP" (unless asked) / mixing unrelated changes
/ inventing scopes that aren't real modules.
✅ One logical change · clear searchable subject · real scope · references issues
when applicable.

## References
- [Conventional Commits](https://www.conventionalcommits.org/)
- `AGENTS.md` — module map and release process
