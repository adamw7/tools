# 8. log4j2 as the logging backend

- **Status:** Accepted
- **Date:** 2026-07-16
- **Deciders:** Project maintainers
- **Tags:** logging, observability
- **Supersedes:** —
- **Superseded by:** —

Records a convention that [ADR 0001](0001-foundational-architecture.md) enforces
via ArchUnit but does not itself justify.

## Context

Production code needs a single, consistent logging story. Left unstated, different
modules drift toward `System.out`/`System.err`, `printStackTrace()`, or a mix of
logging facades, which makes output impossible to configure centrally and hides
errors from any log aggregation. The project already fails the build on ad-hoc
console output through its ArchUnit rules ([ADR 0001](0001-foundational-architecture.md)),
so it needs a named backend those rules can point contributors at.

## Decision

Log through **Apache log4j2** (`log4j-api` for call sites, `log4j-core` as the
runtime), version-managed in the root pom. Conventions:

- Loggers are declared `private static final` and obtained per class.
- Production code logs **only** through the log4j2 API — no `System.out`/`err`, no
  `printStackTrace()`, and no `System.exit()`. These prohibitions are enforced by
  ArchUnit tests in each module's `...architecture` package, not left to review.
- Application entry points and MCP servers configure log4j2; library modules
  depend only on `log4j-api` at their call sites.

log4j2 is chosen for its mature configuration model, performance, and ubiquity in
the Java ecosystem; standardising on one backend is what matters more than the
specific choice among mature options.

## Consequences

**Positive**

- One configurable logging pipeline across every module; output can be routed,
  filtered, and formatted centrally.
- The "no console output / no `printStackTrace`" rules have a concrete, enforced
  destination, so errors are captured rather than dropped to stderr.

**Negative / trade-offs**

- log4j2's history (e.g. Log4Shell) is a reminder that the logging backend is a
  security-relevant dependency; keeping it current is covered by the
  dependency-update posture ([ADR 0002](0002-security-policy-and-supply-chain-posture.md)).
- Contributors used to SLF4J or plain `System.out` must adopt the project's API
  and the `private static final` logger convention; the ArchUnit rules make the
  requirement explicit at build time.
