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

- **`schedule` (Monday before 06:00 UTC) with `prConcurrentLimit: 5`** — batches
  the noise the *Consequences* section anticipates into one weekly window, capped
  at five open pull requests. The hourly limit is deliberately off
  (`prHourlyLimit: 0`): Renovate scans the repository about once a day, so only a
  single scan falls inside the weekly window, and an hourly cap of two meant that
  one scan opened two pull requests and rate-limited the rest until the *next*
  week. A grouped update that keeps losing that race is never reviewed at all —
  the GitHub Actions group sat rate-limited behind two Maven bumps — so the
  concurrent limit alone now sets the ceiling.
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
- **`pinDigests` off for github-actions, on for dockerfile.** The workflows
  reference each action by its major tag (`actions/setup-java@v6`), so the tag is
  what moves and there is no digest for Renovate to refresh; what it raises is
  the major bump, `@v6` to `@v7`, in the grouped actions pull request — the one
  place release notes are worth reading. The Dockerfile is the opposite case and
  stays pinned: an image tag is re-pushed in place by whoever owns it, and those
  base images end up inside the released artifacts.
  `aquasecurity/trivy-action` publishes no major tag at all — every one of its 75
  tags is an exact `v0.x.y` release — so it is referenced exactly and Renovate
  raises its minor bumps like any other dependency.

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
- Referencing actions by a major tag rather than a commit SHA means a tag that is
  re-pointed — by a compromised account or by its owner — changes what CI runs
  without a commit in this repository. That is accepted knowingly: it is what
  lets an action's own patch and security releases reach CI unattended, the
  actions in use are first-party GitHub and Docker ones plus Trivy, and the
  artifacts a release actually ships are protected at the layer that survives
  this — `assembly/Dockerfile`'s digest-pinned base images. Revisit it if an
  action in use changes hands.
