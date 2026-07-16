# 7. DuckDB (JDBC) as the Parquet read engine

- **Status:** Accepted
- **Date:** 2026-07-16
- **Deciders:** Project maintainers
- **Tags:** data, dependencies
- **Supersedes:** —
- **Superseded by:** —

Expands on the data-toolkit capability sketched in
[ADR 0001](0001-foundational-architecture.md).

## Context

The `data` module exposes CSV, GZip, JDBC, and Parquet data sources behind a
common contract, in both in-memory and iterative flavours. Parquet is a columnar
binary format with no usable pure-Java reader in the project's existing
dependency set; reading it requires an engine that can open a Parquet file and
project rows. The obvious JVM option — the Apache Parquet + Hadoop stack — pulls a
very large, Hadoop-flavoured transitive tree that is heavy for a general-purpose
tooling library and awkward to keep on a current, CVE-free version.

## Decision

Read Parquet through **DuckDB via its JDBC driver** (`org.duckdb:duckdb_jdbc`,
version-managed in the root pom like every other dependency —
[ADR 0001](0001-foundational-architecture.md)). The Parquet data sources
(`IterableParquetDataSource`, `InMemoryParquetDataSource`) delegate to DuckDB
(`DuckDbParquet`), which reads the file with a SQL `SELECT` over DuckDB's native
Parquet support and surfaces rows through the same data-source contract as the
other formats.

DuckDB is chosen because it ships a single self-contained native library behind a
standard JDBC interface, has first-class Parquet support, and avoids the Hadoop
dependency tree entirely. Reusing the JDBC path also means the Parquet sources sit
naturally alongside the module's existing JDBC data source rather than introducing
a second, unrelated I/O stack.

## Consequences

**Positive**

- Parquet support with a small, self-contained dependency and no Hadoop tree.
- Parquet reads reuse the JDBC-shaped data-source contract, keeping the module's
  I/O surface uniform.
- DuckDB's SQL engine makes column projection and filtering cheap to express if
  the sources grow richer query needs later.

**Negative / trade-offs**

- DuckDB bundles a platform-specific native library; the dependency is larger than
  a pure-Java jar and ties Parquet reads to the platforms DuckDB publishes
  binaries for.
- The project takes on DuckDB's release cadence for security and correctness fixes
  — mitigated by centralised version management and the dependency-update posture
  ([ADR 0002](0002-security-policy-and-supply-chain-posture.md)).
- Routing Parquet through an embedded SQL engine is heavier than a direct reader
  for the simplest "scan every row" case; accepted for the smaller dependency
  footprint and the query headroom it buys.
