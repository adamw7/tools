# 5. Renovate for routine dependency version updates

- **Status:** Proposed (config committed; pending Renovate App enablement)
- **Date:** 2026-07-16
- **Deciders:** Project maintainers
- **Tags:** dependencies, automation, ci
- **Supersedes:** —
- **Superseded by:** —

Refines the "keep dependencies current" pillar of
[ADR 0002](0002-security-policy-and-supply-chain-posture.md). The Renovate
configuration has now landed (see *Implementation status* below); this record
stays `Proposed` only until the Renovate GitHub App is enabled on the
repository, at which point it flips to `Accepted`.

## Context

All dependency and plugin versions are centralised in the root pom
([ADR 0001](0001-foundational-architecture.md)), which makes upgrades a
single-file change but does nothing to tell the maintainer *when* a newer version
exists. Left to manual tracking (or the periodic `generate-maven-update-reports`
script), the project drifts onto stale versions and only discovers a needed
upgrade when something breaks or a CVE is announced. The project wants automated,
continuous version hygiene delivered as reviewable pull requests.

## Decision

Adopt **Renovate** to own **routine dependency and Maven-plugin version bumps**.
Renovate opens pull requests that raise versions in the root pom's
`<dependencyManagement>` / `<pluginManagement>`, where every version lives, so its
PRs are naturally scoped to one place and run through the normal CI (build, tests,
enforcer, ArchUnit) before merge.

Renovate is chosen over Dependabot for this role because it handles a Maven
mono-repo's centralised version management well, groups related updates, supports
scheduling to batch noise, and (once trusted) can auto-merge low-risk updates.

**Division of labour:** Renovate handles *routine version currency*; it does **not**
own security remediation — that is Dependabot's role
([ADR 0006](0006-dependabot-security-updates.md)). This split is deliberate; see
[ADR 0002](0002-security-policy-and-supply-chain-posture.md). Running both bots for
*all* updates was rejected as redundant and noisy.

### Implementation status

The Renovate configuration is now committed at
[`renovate.json`](../../renovate.json): it extends `config:recommended`, runs on
a weekly schedule, groups non-major Maven bumps into a single PR (every version
lives in the root pom, so a grouped PR touches one file), and disables Renovate's
own vulnerability alerts so security remediation stays solely with Dependabot
([ADR 0006](0006-dependabot-security-updates.md)). The one remaining step is
enabling the Renovate GitHub App on the repository — a settings action that
cannot be committed. Until the app is enabled the config is inert, so this ADR
stays `Proposed`; it flips to `Accepted` once the app is installed and opening
PRs.

## Consequences

**Positive**

- Dependencies and plugins stay current with minimal manual effort; upgrades arrive
  as CI-verified pull requests.
- Centralised versions mean each Renovate PR touches one file and is easy to review.
- Scheduling/grouping keeps PR volume manageable.

**Negative / trade-offs**

- Adds a bot and a config file to maintain; misconfiguration can produce noise.
- Requires the Renovate App to be installed and configured on the repository.
- Care is needed so Renovate and Dependabot do not both open a PR for the same
  security-driven bump; the role split in ADR 0002 exists to prevent that.
