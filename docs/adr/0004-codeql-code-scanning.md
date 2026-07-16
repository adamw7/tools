# 4. CodeQL static analysis for code scanning

- **Status:** Accepted
- **Date:** 2026-07-16

## Context

First-party code can contain security-relevant defects — injection sinks, unsafe
deserialization, path traversal, and similar — that unit and architecture tests do
not target. The project wants automated static analysis that understands Java,
integrates with GitHub's security tab, and requires no third-party service beyond
GitHub itself.

## Decision

Use **GitHub CodeQL** for static security analysis of the Java code, wired as a
GitHub Actions workflow at
[`.github/workflows/codeql.yml`](../../.github/workflows/codeql.yml).

- **Language:** `java` (covers Java/Kotlin). The build is discovered via CodeQL
  `autobuild` on JDK 25 (Temurin), matching the project's toolchain
  ([ADR 0001](0001-foundational-architecture.md)).
- **Cadence:** scheduled weekly (`cron: '23 21 * * 6'`, Saturdays) rather than on
  every push, to catch newly published query updates without adding latency to the
  normal CI path. Findings are uploaded as **code-scanning alerts**
  (`security-events: write`).
- **Permissions** follow least privilege: `actions: read`, `contents: read`,
  `security-events: write`.

Running scans separately from the `maven.yml` build keeps the fast feedback loop
(compile/test/enforce) independent of the slower security scan.

## Consequences

**Positive**

- Security defects in first-party code surface as GitHub alerts with data-flow
  context, at no extra infrastructure cost.
- The scan can adopt `security-extended` / `security-and-quality` query packs later
  by uncommenting the `queries:` line, without structural change.

**Negative / trade-offs**

- A weekly cadence means a defect introduced just after a scan can sit up to a week
  before CodeQL flags it. Moving to `on: [push, pull_request]` would shorten that
  window at the cost of longer PR turnaround and more Actions minutes; this is a
  deliberate trade-off that can be revisited.
- `autobuild` must be able to build the reactor; if it ever fails, the workflow
  falls back to explicit build steps (documented inline in the workflow).
