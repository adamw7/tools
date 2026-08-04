# 5. Renovate for routine dependency version updates

- **Status:** Accepted
- **Date:** 2026-07-16
- **Deciders:** Project maintainers
- **Tags:** dependencies, automation, ci
- **Supersedes:** —
- **Superseded by:** —

Refines the "keep dependencies current" pillar of
[ADR 0002](0002-security-policy-and-supply-chain-posture.md). The decision is in
force: the app is enabled and `.github/renovate.json` is committed (see
*Implementation status* below).

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

In force. The Renovate GitHub App is enabled on the repository and has been
opening pull requests against the root pom for some time; the configuration now
lives in [`.github/renovate.json`](../../.github/renovate.json). Until that file
landed the app ran on its own defaults, which is why its earlier pull requests
arrived ungrouped and unscheduled.

The committed configuration extends `config:recommended` and adds only what this
repository's shape calls for:

- **`schedule` (Monday before 06:00 UTC) with concurrency limits** — batches the
  noise the *Consequences* section anticipates into one weekly window.
- **`vulnerabilityAlerts: { enabled: false }` and `osvVulnerabilityAlerts: false`**
  — the division of labour above, expressed as configuration rather than
  convention: Renovate declines security-driven bumps so Dependabot
  ([ADR 0006](0006-dependabot-security-updates.md)) owns them without a duplicate
  pull request.
- **Grouping by what moves together** — Maven plugins, the coverage and mutation
  tooling, the test libraries, and the protobuf toolchain each land as one pull
  request, since every version they touch lives in the same two root-pom blocks.
- **`dependencyDashboardApproval` on two major bumps** — the Maven API
  (deliberately a Maven 4 artifact while the build is pinned to Maven 3.9.x) and
  Spring Boot, which hosts the three MCP servers. Both stay visible on the
  dashboard rather than arriving unannounced.
- **This project's own `io.github.adamw7` modules disabled** — they resolve inside
  the reactor at `${revision}`, so there is no release for Renovate to raise.

Validate a change to that file with
`npx --package renovate renovate-config-validator` before committing it.

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
