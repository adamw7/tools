# Architecture Decision Records

This directory holds the **Architecture Decision Records (ADRs)** for the `tools`
project. An ADR captures a single architecturally significant decision — the
context that forced it, the choice made, and the consequences that follow — so
the *why* behind the code survives long after the discussion that produced it.

We follow the lightweight format popularised by
[Michael Nygard](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions).
Each record is immutable once accepted: rather than editing an old decision, we
add a new ADR that supersedes it and cross-link the two.

## Conventions

- One decision per file, named `NNNN-short-title.md` (zero-padded, monotonically
  increasing, e.g. `0007-duckdb-parquet-data-source.md`).
- Number `0001` is reserved for the foundational record below. It is a deliberate
  exception to "one decision per file": it captures the cross-cutting decisions
  that shape the repository as a whole, each individually supersedable.
- Keep records short and factual. Link to `AGENTS.md`, `README.md`, and the
  `docs/` diagrams for the detail rather than duplicating them.

### Template

Every record opens with the title (`# N. Title`) followed by a metadata block and
the `Context` → `Decision` → `Consequences` sections:

```
- **Status:** <see below>
- **Date:** YYYY-MM-DD          (the date the decision was actually made)
- **Deciders:** <who>
- **Tags:** <comma-separated>
- **Supersedes:** <NNNN or —>
- **Superseded by:** <NNNN or —>
```

**Status vocabulary:**

- `Proposed` — decided in principle but **not yet in force** (config/settings not
  yet committed or enabled). Such records say so and name what must land before
  they flip to `Accepted`.
- `Accepted` — in force.
- `Superseded by NNNN` — replaced by a later record; cross-linked both ways.
- `Deprecated` — no longer in force and not replaced.

Records are immutable in intent once `Accepted`: rather than rewriting an accepted
decision, add a new ADR that supersedes it and set both records' `Supersedes` /
`Superseded by` fields.

## Index

| ADR | Title | Status | Date |
| --- | --- | --- | --- |
| [0001](0001-foundational-architecture.md) | Foundational architecture of the `tools` project | Accepted | 2026-07-16 |
| [0002](0002-security-policy-and-supply-chain-posture.md) | Security policy and supply-chain posture | Accepted | 2026-07-16 |
| [0003](0003-require-tls-1.3.md) | Require TLS 1.3+ for HTTPS transports | Accepted | 2026-07-16 |
| [0004](0004-codeql-code-scanning.md) | CodeQL static analysis for code scanning | Accepted | 2026-07-16 |
| [0005](0005-renovate-dependency-updates.md) | Renovate for routine dependency version updates | Proposed | 2026-07-16 |
| [0006](0006-dependabot-security-updates.md) | Dependabot for security-alert updates | Proposed | 2026-07-16 |
| [0007](0007-duckdb-parquet-data-source.md) | DuckDB (JDBC) as the Parquet read engine | Accepted | 2026-07-16 |
| [0008](0008-log4j2-logging.md) | log4j2 as the logging backend | Accepted | 2026-07-16 |
| [0009](0009-mcp-servers-on-spring-boot.md) | MCP servers built on Spring Boot | Accepted | 2026-07-16 |
| [0010](0010-documentation-as-enforced-contract.md) | Documentation as a build-enforced contract | Accepted | 2026-07-16 |

## Related documents

- [AGENTS.md](../../AGENTS.md) — single source of truth for the repository guide.
- [docs/c4-architecture.md](../c4-architecture.md) — C4 model of modules and MCP servers.
- [docs/compile-time-safe-builders.md](../compile-time-safe-builders.md) — how the
  generated builder chain shifts required-field validation to compile time.
