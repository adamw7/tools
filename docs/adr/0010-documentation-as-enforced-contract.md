# 10. Documentation as a build-enforced contract

- **Status:** Accepted
- **Date:** 2026-07-16
- **Deciders:** Project maintainers
- **Tags:** docs, build, tooling
- **Supersedes:** —
- **Superseded by:** —

Justifies and details the doc-contract mechanism named in
[ADR 0001](0001-foundational-architecture.md).

## Context

The repository maintains overlapping guidance for different audiences: `AGENTS.md`
(the single source of truth for agents), `CLAUDE.md` (a quick-reference that
defers to it), and `README.md` (the same capabilities for humans). Overlapping
docs rot independently — the Java version is bumped in one file but not another, a
new capability is added to the README but not AGENTS.md, a required heading is
dropped in a refactor. When agent-facing docs drift, agents act on stale
instructions, so ordinary "keep the docs updated" discipline is not enough: the
docs need to fail the build when they disagree.

## Decision

Enforce documentation consistency with a **custom maven-enforcer rule module**,
`claude-code-enforcer`, wired into the reactor build. Its rules include:

- **Format rules** that pin required headings/structure in `CLAUDE.md` and
  `AGENTS.md` (`ClaudeMdFormatRule`, `AgentsMdFormatRule`).
- **`crossDocConsistency`**, which keeps mirrored facts (e.g. the Java version)
  identical between `CLAUDE.md` and `AGENTS.md`.
- **README consistency** (`ReadmeConsistencyRule`), which catches a capability
  documented in one place but missing from another.
- Rules validating agent/skill/command **definition files** and `settings.json`
  hook configuration, so the agent tooling config cannot silently break.

Because the checks run as enforcer rules, drift fails `mvn install` like any other
build error rather than relying on a reviewer to notice. The enforcer module is
itself covered by ArchUnit and unit tests
([ADR 0001](0001-foundational-architecture.md)).

## Consequences

**Positive**

- Agent-facing docs stay trustworthy: contradictions between `AGENTS.md`,
  `CLAUDE.md`, and `README.md` become build failures, not latent bugs.
- The "single source of truth" claim for `AGENTS.md` is mechanically true, not
  aspirational.
- Agent, skill, command, and settings config is validated, so a malformed
  definition is caught before it ships.

**Negative / trade-offs**

- The enforcer rules are project-specific code that must be maintained and kept in
  sync when the doc structure legitimately changes — a deliberate cost paid to
  prevent drift.
- A legitimate doc change can require touching multiple files at once to satisfy
  the consistency rules; this is the intended friction, but it is friction.
- The rules encode assumptions about heading structure; large doc reorganisations
  must update the rules alongside the docs.
