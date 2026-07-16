# 6. Dependabot for security-alert updates

- **Status:** Proposed (config committed; pending repo security-settings enablement)
- **Date:** 2026-07-16
- **Deciders:** Project maintainers
- **Tags:** security, dependencies, automation
- **Supersedes:** —
- **Superseded by:** —

Refines the "remediate known vulnerabilities fast" pillar of
[ADR 0002](0002-security-policy-and-supply-chain-posture.md). The
`.github/dependabot.yml` configuration has now landed (see *Implementation
status* below); this record stays `Proposed` only until Dependabot alerts and
security updates are switched on in the repository's security settings, at which
point it flips to `Accepted`.

## Context

Renovate keeps dependencies on recent versions
([ADR 0005](0005-renovate-dependency-updates.md)), but routine version currency and
*security* remediation are different problems. When a CVE is published against a
dependency (directly or transitively), the project needs a fast, unambiguous path
to the patched version — ideally opened automatically the moment GitHub's advisory
database learns of the issue, and clearly labelled as a security fix so it is
prioritised over ordinary bumps.

## Decision

Use **GitHub Dependabot security updates** to own **vulnerability remediation**.
Dependabot watches the repository's dependency graph against the GitHub Advisory
Database and opens **security-only** pull requests that move an affected dependency
to the minimum patched version, tied to the corresponding Dependabot alert.

Dependabot is chosen for this role because it is native to GitHub, driven directly
by the advisory database, and integrates with the repository's Dependabot alerts —
so a security PR carries the alert context and severity. Its native integration
makes it the better fit for security remediation, while Renovate's richer
scheduling/grouping makes it the better fit for routine bumps.

**Division of labour:** Dependabot is scoped to **security updates only**;
routine version bumps are Renovate's job ([ADR 0005](0005-renovate-dependency-updates.md)).
Keeping Dependabot's version-update stream disabled avoids duplicate PRs with
Renovate. See [ADR 0002](0002-security-policy-and-supply-chain-posture.md) for the
overall rationale.

### Implementation status

Step 2 below — the config file — is now done;
[`.github/dependabot.yml`](../../.github/dependabot.yml) declares the `maven`
ecosystem with `open-pull-requests-limit: 0`, which disables Dependabot's
version-update stream (Renovate's job, [ADR 0005](0005-renovate-dependency-updates.md))
while leaving the ecosystem configuration — schedule and labels — to apply to the
security updates governed by the repository setting. What remains is step 1, a
settings action that cannot be committed:

1. Turning on **Dependabot alerts** and **Dependabot security updates** in the
   repository's security settings (GitHub UI) — **still pending**, and
2. Committing `.github/dependabot.yml` with the `maven` ecosystem configured and
   version-updates left off — **done**.

Until the settings are enabled, security updates do not run, so this ADR stays
`Proposed`. The status flips to `Accepted` when Dependabot security updates are
switched on in the repository's security settings.

## Consequences

**Positive**

- Known-vulnerable dependencies are remediated automatically, fast, with the
  advisory/severity context attached.
- Security PRs are visually distinct from routine Renovate PRs, so they can be
  prioritised.
- No third-party service beyond GitHub is required.

**Negative / trade-offs**

- Two update bots run against one repository; the security-only scoping in this ADR
  and the version-only scoping in ADR 0005 are what keep them from colliding.
- Dependabot's remediation is limited to what the GitHub Advisory Database knows;
  it complements, but does not replace, CodeQL ([ADR 0004](0004-codeql-code-scanning.md))
  and keeping versions current (ADR 0005).
