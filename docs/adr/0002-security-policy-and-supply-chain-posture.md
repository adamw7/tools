# 2. Security policy and supply-chain posture

- **Status:** Accepted
- **Date:** 2026-07-16

## Context

`tools` is published to Maven Central and pulls a large tree of third-party
dependencies (Spring Boot, protobuf, DuckDB, log4j2, and the MCP SDK, among
others). As a library consumed by other projects, a vulnerability in `tools` or
in one of its transitive dependencies propagates downstream. The project needs a
stated, repeatable security posture rather than ad-hoc responses: a way for
people to report issues, a defined set of supported versions, automated detection
of vulnerable code and dependencies, and automated remediation.

This ADR is the umbrella record for the project's security posture. The concrete
mechanisms it relies on are recorded in their own ADRs:

- Transport security — [ADR 0003](0003-require-tls-1.3.md) (require TLS 1.3+).
- Static code scanning — [ADR 0004](0004-codeql-code-scanning.md) (CodeQL).
- Dependency version hygiene — [ADR 0005](0005-renovate-dependency-updates.md)
  (Renovate).
- Dependency vulnerability remediation —
  [ADR 0006](0006-dependabot-security-updates.md) (Dependabot security updates).

## Decision

The project adopts a defence-in-depth security posture built from four pillars:

1. **Coordinated disclosure.** Vulnerabilities are reported privately (see
   [SECURITY.md](../../SECURITY.md)), not through public issues. `SECURITY.md`
   also declares the **supported versions** that receive fixes, so reporters and
   consumers know what is maintained.
2. **Find vulnerable code early** with automated static analysis on a schedule and
   surfaced as GitHub code-scanning alerts (ADR 0004).
3. **Keep dependencies current** so the project rarely sits on a version with a
   known CVE, via automated version-bump pull requests (ADR 0005).
4. **Remediate known vulnerabilities fast** with automated security-only update
   pull requests that jump straight to a patched version (ADR 0006).

The two update mechanisms have **distinct, non-overlapping roles**: Renovate owns
routine version bumps, Dependabot owns security-alert remediation. This split is
deliberate — running both for all updates would produce redundant, noisy pull
requests. See the individual ADRs for the rationale.

All of the above is enforced through the existing CI and repository
configuration; the security posture is a property of the build and repo settings,
not a manual checklist.

## Consequences

**Positive**

- Reporters have a clear, private channel; consumers know which versions are
  supported.
- Vulnerabilities in both first-party code and dependencies are detected and
  remediated with minimal manual effort.
- The posture is composed of small, independently revisable decisions rather than
  one monolithic policy.

**Negative / trade-offs**

- Automated scanning and update bots generate pull-request and alert traffic that
  a maintainer must triage.
- The supported-versions table in `SECURITY.md` must be kept current as releases
  are cut — stale entries mislead consumers.
