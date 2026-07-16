# 1. Foundational architecture of the `tools` project

- **Status:** Accepted
- **Date:** 2026-07-16
- **Deciders:** Project maintainers
- **Tags:** foundational, build, architecture
- **Supersedes:** —
- **Superseded by:** —

> **Scope note — intentional exception to "one decision per file."** The repo
> convention (see [README](README.md)) is one decision per record. This
> foundational ADR is a deliberate exception: it captures the cross-cutting
> decisions that shape the whole repository as a single baseline. Each numbered
> section below is individually revisable — a later ADR supersedes just that
> section and links back here. Capability-specific decisions that expand on the
> rationale sketched here live in their own records (e.g.
> [ADR 0007](0007-duckdb-parquet-data-source.md),
> [ADR 0008](0008-log4j2-logging.md),
> [ADR 0009](0009-mcp-servers-on-spring-boot.md),
> [ADR 0010](0010-documentation-as-enforced-contract.md)), and the security
> posture is recorded under [ADR 0002](0002-security-policy-and-supply-chain-posture.md).

## Context

`tools` is a library of general-purpose Java tooling that grew several distinct
capabilities — compile-time-safe protobuf code generation, context engineering
for gen-AI agents, and a data toolkit — alongside two MCP servers and an
agent-facing documentation contract. These capabilities share build
infrastructure, dependency management, testing discipline, and coding
conventions, but each has its own domain and release surface.

This record captures the cross-cutting architectural decisions that shape the
repository as a whole. Decisions local to a single capability that later need
revisiting should be recorded in their own ADRs, which may supersede parts of
this one. Detailed, evolving explanations live in
[AGENTS.md](../../AGENTS.md) (the single source of truth),
[README.md](../../README.md), and the diagrams under [docs/](..); this ADR
records the *decisions* and their rationale, not the how-to.

## Decision

### 1. Multi-module Maven reactor with centralised version management

The project is a single Maven reactor (`packaging=pom`) split into focused
modules — `claude-code-enforcer`, `mcp-common`, `data`, `code`
(`protogen-maven-plugin`, `protogen-maven-plugin-test`, `context`),
`grpc-example`, and `assembly` — with `data-test` built standalone outside the
root `<modules>` list. Each capability lives in its own module so it can be built,
tested, and depended on in isolation.

All dependency **versions and scopes** are declared only in the root pom's
`<dependencyManagement>`, and all plugin versions only in `<pluginManagement>`.
Module poms reference dependencies and plugins **without versions**. This keeps
versions consistent across modules and makes upgrades a single-file change.

### 2. Java 25 as the baseline toolchain

The whole project targets **Java 25** (`maven.compiler.source`/`target = 25`),
built with **Maven 3.9.x**. Adopting a current LTS-track JDK lets the code use
modern language and JDK features and keeps the toolchain requirement uniform
across every module. Web/remote agent sessions provision the JDK through the
`.claude/hooks/session-start.sh` hook.

### 3. Shift-left required-field validation via generated builders

The `protogen-maven-plugin` generates protobuf builders that make missing
**required fields a compile-time error** instead of a runtime failure — proto2
`required` fields are enforced through the builder chain, proto3 gets
presence-aware accessors, and `oneof` groups get discriminator/clear support.
Moving this validation left is the defining design bet of the code-generation
capability; see
[docs/compile-time-safe-builders.md](../compile-time-safe-builders.md).

### 4. MCP servers as the agent integration surface

Capabilities that are useful to AI assistants are exposed through **MCP servers**
(Spring Boot apps) rather than bespoke integrations: the uniqueness checker in
`data`, and `project_tree` / `find_context` / `estimate_tokens` in `code/context`.
Shared server scaffolding — transport wiring and the tool SPI — is factored into
`mcp-common`, and each server supports stdio, streamable HTTP, stateless HTTP, and
HTTP+SSE transports. This keeps the tool logic decoupled from transport concerns
and consistent across servers. The choice of Spring Boot as the server runtime is
recorded and justified in [ADR 0009](0009-mcp-servers-on-spring-boot.md).

### 5. Documentation as an enforced contract

`AGENTS.md` is the **single source of truth** for the repository guide; `CLAUDE.md`
is a quick-reference that defers to it, and `README.md` documents the same
capabilities for humans. A **custom maven-enforcer rule** (`claude-code-enforcer`)
fails the build when these documents drift — format rules pin required headings,
`crossDocConsistency` keeps mirrored facts (e.g. the Java version) aligned between
CLAUDE.md and AGENTS.md, and a README-consistency rule catches capabilities
described in one place but missing from another. Treating the docs as a
build-checked contract keeps agent guidance trustworthy. The rationale for this
unusual "documentation as a build-failing contract" mechanism, and the rules it
enforces, are recorded in [ADR 0010](0010-documentation-as-enforced-contract.md).

### 6. Layering and convention enforcement via ArchUnit

Package layering and coding conventions are pinned with **ArchUnit** tests in each
module's `...architecture` test package: data-source contracts must not depend on
their implementations, the uniqueness core must not depend on its MCP adapter,
loggers are `private static final`, production code logs through log4j2 (no
`System.out`/`err`, `printStackTrace`, or `System.exit`), and packages stay free
of cycles. A companion test pins conventions on the tests themselves. Encoding the
architecture as executable tests stops it from rotting.

### 7. Fast, hermetic, self-contained builds and tests

Several decisions keep the build reproducible and safe to run anywhere,
including networks that can only reach Maven Central:

- **Unit tests run with the network off** — the `data` module engages a
  `Switch` kill-switch before any unit test runs, so a unit test can never open an
  outbound connection; failsafe `*IT` tests are unaffected.
- **Tight test timeouts** — a 900 ms per-test Surefire timeout keeps unit tests
  fast, with looser lifecycle and fork-level timeouts for legitimately heavier
  setup.
- **Offline shellcheck** — script linting uses the embedded shellcheck binary from
  the plugin jar rather than downloading it from GitHub, so the build works where
  GitHub is blocked.
- **Integration tests, coverage, and mutation testing** are opt-in profiles
  (`integration-tests`, `coverage` with an 80% floor, `pitest`).

### 8. SOLID, clean-code style rules

All code follows **SOLID principles** and clean-code conventions: short methods,
meaningful names, no `continue`/`break`, and matching the surrounding style. New
dependencies are added only after discussion. These rules are documented in
`AGENTS.md`/`CLAUDE.md` and partly enforced by the ArchUnit and enforcer rules
above.

## Consequences

**Positive**

- New capabilities have an obvious home (a new module) and inherit dependency
  management, testing discipline, and conventions for free.
- Errors surface early: missing required fields at compile time, doc drift and
  architecture violations at build time, accidental network use during unit tests.
- Builds are reproducible and runnable in restricted networks and ephemeral agent
  environments.
- Agent-facing docs stay accurate because the build enforces them.

**Negative / trade-offs**

- The Java 25 baseline requires contributors and CI to provision a current JDK.
- Centralised version management means module poms cannot pin a divergent version
  without changing the root pom.
- The enforcer and ArchUnit rules add build steps that must be kept in sync when
  conventions legitimately change — a deliberate cost paid to prevent drift.

## Superseding

Later decisions that revisit any point above should be recorded as their own
numbered ADRs and reference this record. This ADR remains `Accepted` until such a
successor supersedes a specific section.
