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
  increasing, e.g. `0002-adopt-duckdb-for-parquet.md`).
- Number `0001` is reserved for the foundational record below, which captures the
  cross-cutting decisions that shape the repository as a whole.
- Status is one of `Proposed`, `Accepted`, `Superseded by NNNN`, or `Deprecated`.
- Keep records short and factual. Link to `AGENTS.md`, `README.md`, and the
  `docs/` diagrams for the detail rather than duplicating them.

## Index

| ADR | Title | Status |
| --- | --- | --- |
| [0001](0001-foundational-architecture.md) | Foundational architecture of the `tools` project | Accepted |

## Related documents

- [AGENTS.md](../../AGENTS.md) — single source of truth for the repository guide.
- [docs/c4-architecture.md](../c4-architecture.md) — C4 model of modules and MCP servers.
- [docs/compile-time-safe-builders.md](../compile-time-safe-builders.md) — how the
  generated builder chain shifts required-field validation to compile time.
