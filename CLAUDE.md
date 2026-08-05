# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with
code in this repository.

See [AGENTS.md](AGENTS.md) for the full agent guide (repository overview, module
layout, build/test commands, CI, environment, and release process). AGENTS.md is
the single source of truth; this file is a quick-reference summary of the
essentials. When the two ever disagree, AGENTS.md wins — and the build's
`crossDocConsistency` enforcer rule fails if key facts (e.g. the Java version)
drift apart between the two.

## Project

`tools` is a multi-module Maven library of Java tooling. Build with Maven 3.9.X:
run `mvn install` from the repository root. The main capabilities are:

- **Code generation** (`code/protogen-maven-plugin`) — a Maven plugin that
  generates protobuf builders enforcing missing required fields at **compile
  time** (proto2 `required`, proto3 presence-aware accessors, `oneof`
  discriminators).
- **Context engineering** (`code/context`) — a regex-based class-usage finder and
  project-tree builder that assembles context for gen-AI agents, an emitter for
  Google's Open Knowledge Format (OKF v0.2 bundles of markdown concept documents,
  in the `okf` package), plus an MCP server exposing `project_tree`,
  `find_context`, `estimate_tokens`, and `okf_bundle`.
- **Data** (`data`) — data sources (CSV, GZip, JDBC, Parquet via an in-process
  DuckDB engine, plus forward-only JSON/YAML/TOON), each in in-memory and
  iterative variants. Schema-aware sources implement the narrower
  `ColumnarDataSource` contract, so callers needing the schema (e.g. the
  uniqueness check) cannot be handed a forward-only source. Also a
  column-uniqueness/key finder, open-addressing map/set data structures
  (`OpenAddressingMap`, `OpenAddressingSet`, primitive-keyed
  `IntKeyOpenAddressingMap`), and an MCP server exposing the uniqueness checker.
- **Claude Code adoption** (`adopt`) — a pipeline that adopts Claude Code into a
  GitHub repo: check the required tools (`git`, `claude`, `gh`) are installed
  and that `gh` is logged in, clone, check the cloned project's own build tool is
  installed too, create a feature branch, mark the checkout trusted, `claude
  init` to generate `CLAUDE.md`, conform that file (and add a companion
  `AGENTS.md`) as far as the guard about to be wired in demands — only the Maven
  path wires in the format rule, so only it gets that rule's Java and Maven
  headings — commit it, wire a
  build-tool-aware `CLAUDE.md` guard into the repo (the `claude-code-enforcer`
  rule for Maven `pom.xml`, an `enforceClaudeMd` guard task for Gradle, and a
  GitHub Actions workflow plus `.github/claude-md-guard.sh` check as the
  build-tool-agnostic fallback) and commit that, verify the guard passes on the
  generated file, then push the branch and open a pull request (`gh pr create`)
  with metadata from `PullRequestOptions` (which, with the rest of a run's
  configuration, is grouped into an `AdoptionOptions` both entry points build and
  hand to the pipeline factory; exposed as CLI flags such as `--title`,
  `--reviewer`, `--draft`, and `--timeout <minutes>`); the default branch is never
  written to directly. `--dry-run` (`dry_run` on the MCP tool) assembles the
  pipeline without the push and pull-request steps — and so asks the toolchain
  check only for the `git` and `claude` it will really run, not the `gh` the pull
  request alone uses — so a run can be rehearsed into the workspace with no GitHub
  credentials at all, and `--help` answers with the usage
  line. One run adopts a list of repositories — repeatable `--repo <url>` or
  `--repos <file>` on the command line, `repository_urls` on the MCP tool — each
  with its own report, and a repository that fails does not stop the rest — a
  malformed URL included, since each checkout is claimed inside its own
  adoption. Clone-URL credentials are masked in every log, message, and report.
  An optional `--assets` flag commits starter Claude Code
  configuration assets (an `AGENTS.md` pointer, `.claude/settings.json`, a
  session-start hook stub, `.mcp.json`, and an `@claude`-mention workflow); each
  run returns an `AdoptionReport` with the pull request URL, written as JSON via
  `--report <file>`, and an MCP server exposes the pipeline as an `adopt_repo`
  tool.

Module map (root reactor: `claude-code-enforcer`, `test-common`, `mcp-common`,
`data`, `code`, `adopt`, `grpc-example`, `assembly`; `data-test` is built
separately):

```
tools (root pom, packaging=pom)
├── claude-code-enforcer   # custom maven-enforcer rules validating CLAUDE.md/AGENTS.md & agent config
├── test-common            # shared ArchUnit rule libraries (test-jar), reused by every module's architecture tests
├── mcp-common             # shared MCP server scaffolding
├── data                   # data sources, uniqueness checks, structures, MCP server
├── code
│   ├── protogen-maven-plugin       # compile-time-safe protobuf builder generator
│   ├── protogen-maven-plugin-test  # integration tests for the plugin
│   └── context                     # class-usage context finder + MCP server
├── adopt                  # adopts Claude Code into a GitHub repo (clone, build-tool check, branch, trust, init, conform, enforcer, verify, push, PR)
├── grpc-example           # end-to-end gRPC example
├── assembly               # runnable SampleApp distribution (launcher jar + lib/)
└── data-test              # standalone test module (not in root <modules>)
```

Base Java package: `io.github.adamw7` (`io.github.adamw7.context` for the context
module, `io.github.adamw7.tools.*` elsewhere).

Three MCP servers ship here (`data` uniqueness, `code/context`, `adopt`), each a
Spring Boot app with a `Main.java` entry point supporting stdio (default),
streamable HTTP, or stateless HTTP; each has its own `MCP_USAGE.md`
next to its `mcp` package.

Further reading: [README.md](README.md) for worked examples,
[docs/c4-architecture.md](docs/c4-architecture.md) for the C4 model,
[docs/compile-time-safe-builders.md](docs/compile-time-safe-builders.md) for the
builder walkthrough, and [docs/adr](docs/adr) for the architecture decision
records behind the standing choices (DuckDB, log4j2, MCP on Spring Boot,
documentation as an enforced contract, and the security posture).
[k8s/README.md](k8s/README.md) covers running `SampleApp` on minikube as a
run-to-completion Job, and [SECURITY.md](SECURITY.md) the private disclosure
process.

## Java version

Java 25. A JDK 25 must be on the `PATH` with `JAVA_HOME` set. In Claude Code
web/remote sessions the `.claude/hooks/session-start.sh` hook installs
`openjdk-25-jdk` and pre-fetches dependencies.

## Maven

All dependency versions and scopes are defined only in the root `pom.xml` under
`<dependencyManagement>`. All Maven plugin versions are defined only in the root
`pom.xml` under `<pluginManagement>`. Module poms reference dependencies and
plugins without versions.

Common commands (run from the repository root):

```bash
mvn clean install                 # full clean build + install to local repo
mvn install                       # faster incremental build
mvn -pl data -am install          # one module plus the modules it depends on
mvn -pl data -am test             # tests for a single module
mvn -B package                    # build without installing (what CI runs)
mvn -P integration-tests verify   # integration tests (*IT): MCP servers, real-GitHub adoption, enforcer builds
mvn -Pcoverage verify             # JaCoCo coverage (fails under 80% instruction or branch)
mvn -Ppitest install              # PIT mutation testing (fails under the module's
                                  # pitest.mutationThreshold; needs a phase past package)
```

**Always pair `-pl` with `-am`.** `mvn -pl data test` fails before compiling:
the root pom's `ReactorModuleConvergence` rule rejects a reactor whose module
parents are absent from it, and sibling `-SNAPSHOT`s (e.g. `mcp-common`) are not
resolvable from the local repo until installed. `-am` pulls the parent and the
upstream modules back into the reactor.

Running a single test class or method needs one of these, because `-Dtest`
applies to *every* module in the reactor and surefire fails the upstream ones
where nothing matches:

```bash
cd data && mvn test -Dtest='KeyFinderTest#repeatedRowIsADuplicate'   # simplest
mvn -pl data -am test -Dtest=KeyFinderTest -Dsurefire.failIfNoSpecifiedTests=false
```

The first form needs the parent and siblings installed once (`mvn install
-DskipTests`), since running from the module directory resolves them from the
local repo rather than the reactor.

Maven 3.9.X is enforced, not just recommended: the root pom's `enforce`
execution pins `requireMavenVersion [3.9,4.0)` and `requireJavaVersion 25`, so a
Maven 4 or older-JDK build fails at `validate` rather than midway.

Use `clean` after removing a code-generation source, so stale generated builders
in `target/` cannot mask the change.

`.mvn/maven.config` passes `--no-transfer-progress` and `-T1C`, so every build
from the repo root is quiet and runs one thread per core; override with `-T1`
for a serial build.

The root pom lints `scripts/**/*.sh` with
`dev.dimlight:shellcheck-maven-plugin` using
`<binaryResolutionMethod>embedded</binaryResolutionMethod>`: the `shellcheck`
binary ships inside the plugin jar (resolved from Maven Central), so the build
never fetches it from GitHub and needs no `shellcheck` installed, working
offline. The plugin default instead downloads the binary from GitHub releases,
which fails where that host is blocked. Skip the lint with `mvn install
-Dskip.shellcheck=true`. See *Build, test, and run* in AGENTS.md.

Those scripts are developer-environment helpers outside the Maven build, kept as
parallel `scripts/linux` (`*.sh`) and `scripts/windows` (`*.bat`/`*.ps1`)
variants: JDK 25 install, dependency/plugin update reports, and Claude Code and
git updates. See *Helper scripts* in AGENTS.md.

## Principles for Java Development

- **SOLID principles** for all code.
- **Clean code**: short methods, meaningful parameter names.
- **No `continue` or `break`** statements.
- **Match the surrounding code** — naming, comment density, and idiom.

## Testing

Write unit tests for all new logic. Focus on behavior, edge cases, and error
paths.

- **Unit tests** run in the normal `test`/`package` lifecycle. Surefire enforces
  a **5-second per-test timeout** (8 s under coverage) configured on the surefire
  plugin in the root `pom.xml` — above the cold-fork warmup, which stretches under
  the parallel `-T1C` build's CPU contention, but low enough to catch a test doing
  real work —
  so keep unit tests fast; a genuinely heavier test opts out with an explicit
  `@Timeout` and a comment explaining why. A looser
  **10-second lifecycle-method timeout** (15 s under coverage) covers heavier
  shared setup like `@BeforeAll`, and surefire's **300-second
  `forkedProcessTimeoutInSeconds`** kills a fork that hangs outright.
- **Unit tests run with the network off.** The `data` module's
  `NetworkOffExtension` engages the `Switch` kill-switch before any test runs, so
  a unit test can never open an outbound connection; the failsafe `*IT` tests are
  unaffected. A single test can opt in explicitly with the `@NetworkOff`
  annotation, which engages the kill-switch even without the surefire guard
  property (e.g. when run from an IDE). See *Testing* in AGENTS.md.
- **Architecture tests** (ArchUnit) live in each module's `.architecture` test
  package and enforce package layering and coding rules — data-source contracts
  must not depend on their implementations, the uniqueness core must not depend
  on its MCP adapter, JDBC stays confined to the `source.db` package, the
  adoption spawns processes only in its `command` package, only its clone step
  reads the credentialled clone URL and only `GitHubRepoAdopter` assembles the
  pipeline, every enforcer rule is a public `@Named` type whose configured name
  follows its class name and which neither spawns a process, reaches the network,
  nor writes outside the report, the baseline and the front-matter fix, loggers
  are
  `private static final`, abstract types carry an `Abstract` prefix, public
  fields are `final`, mutable static state is `volatile`, fields are never
  `Optional`, date/time uses `java.time` (not the legacy `Date`/`Calendar` API),
  production code logs through log4j2 (no `System.out`/`err`,
  `java.lang.System.Logger`, `printStackTrace`, or `System.exit`), and packages
  stay free of cycles. A companion `TestConventionsArchitectureTest` pins
  conventions on the tests themselves — test methods must sit in `*Test`/`*IT`
  classes, no `@Disabled`, JUnit 5 only, no `System.out`/`err`, and no
  `Thread.sleep`; in `adopt` it also pins that step tests never spawn a real
  process and the `*IT`s never push or open a pull request, and in
  `claude-code-enforcer` that only the `e2e` package forks a real Maven build,
  and only from an `*IT`. The rules that apply
  to every module live once in `test-common` (`CommonCodingConventions`,
  `CommonNamingConventions`, `CommonTestConventions`) and are pulled in with
  `ArchTests.in(...)`, so a module's own test states only what is specific to it.
  Keep new code within these rules.
- **Integration tests** (`*IT`) are gated behind the `integration-tests` profile
  and cover what needs something real: the MCP servers over HTTP; `adopt`'s
  `MultiRepoAdoptionIT`, which clones real GitHub sample repositories to prove a
  batch gives each repository its own checkout (it stops at the branch step, so
  it never pushes or opens a pull request); and `claude-code-enforcer`'s
  `EnforcerRuleBuildIT`/`RepositoryEnforcementIT`, which run real Maven builds so
  the pom-to-rule seam a unit test skips — artifact resolution, `@Named`
  discovery, parameter binding — is exercised, and this repository's own wired
  configuration is proved to catch a regression. The profile is declared per
  module — in `data`, `code/context`, `adopt`, and `claude-code-enforcer`, each
  wiring `maven-failsafe-plugin` — not in the root pom, so a new module's `*IT`s
  stay unrun until that module gets its own copy. See *Testing* in AGENTS.md.

## Continuous integration and commits

One workflow gates pull requests to `main`: `maven.yml` bootstraps the enforcer
rule and then runs `mvn -B package -DenforceClaudeMd` — the only workflow that
runs the doc checks. Its `package` step is capped at **120 seconds**
(`timeout-minutes: 2` plus a wall-clock check that fails with a `::error::`
annotation), so a change that slows the build down red-flags the PR; profile it
rather than raising the cap. Everything else runs on a schedule or a release, so
a green PR is not proof the whole matrix passes:

- **Scheduled** — `integration-tests.yml` daily; `codeql.yml`, `coverage.yml`
  and `docker.yml` Saturdays; `pitest.yml` and `maven-windows.yml` Sundays.
  `maven-windows.yml` runs `mvn install` on `windows-latest`, so keep path,
  line-ending, and file-locking assumptions platform-neutral. `docker.yml`
  builds the image, runs it against a sample CSV and scans it, pushing nothing
  outside a release — run it by hand (`workflow_dispatch`) after changing the
  assembly or the Dockerfile rather than waiting for the weekly run.
- **On a GitHub release** — `docker.yml` again, this time pushing the multi-arch
  image to GHCR; `maven-publish.yml` (GitHub Packages); and
  `central-publish.yml` (Maven Central).

Every workflow builds on JDK 25 (Temurin). See *Continuous integration* and
*Releasing* in AGENTS.md.

Use clear, conventional commit messages — the `git-commit` skill writes them
with this repository's real module scopes — keep changes focused, and add or
update tests alongside the code. **Do not open a pull request unless explicitly
asked.**

## Agent configuration

- `.claude/skills/` holds twelve project skills that carry the detail this file
  only summarises — per module: `data-sources`, `context-finder`, `protogen`,
  `adopt-pipeline`, `mcp-server`, `enforcer-rules`; across the repo:
  `doc-contract`, `git-commit`, `java-code-review`, `maven-conventions`,
  `solid-principles`, and `testing-conventions`. Prefer loading the relevant
  skill over re-deriving a convention.
- `.claude/settings.json` allows the `mvn` and archive-inspection Bash commands
  and wires the `SessionStart` hook; `.claude/hooks/session-start.sh` provisions
  the JDK and warms the Maven cache in web/remote sessions.
- Personal overrides belong in `.claude/settings.local.json`, which is
  gitignored — the `localSettingsIgnored` rule fails the build if that entry
  disappears.

## CLAUDE.md enforcement

The `claude-code-enforcer` module is a set of custom `maven-enforcer-plugin`
rules that fail the build when this file, `AGENTS.md`, `README.md`, or the
`.claude` configuration is missing, malformed, or inconsistent. Relevant when
editing them:

- This file must keep the `# CLAUDE.md` title, reference `AGENTS.md`, and keep
  every required heading (`## Project`, `## Java version`, `## Maven`,
  `## Principles for Java Development`, `## Testing`, `## Dependencies`).
- `contextBudget` caps this file at **32 KB** — it is loaded into every session,
  so put new detail in AGENTS.md or a skill rather than here.
- `moduleMapConsistency` requires every `<module>` of the root pom to be
  mentioned in both CLAUDE.md and AGENTS.md; `crossDocConsistency` pins facts
  shared with AGENTS.md (the Java version) and `readmeConsistency` pins the
  README against AGENTS.md (the protobuf major version).
- `agentsMdFormat` applies the same title/required-heading checks to AGENTS.md,
  and `memoryImports`, `noSecrets`, `skillFilesExist`, `uniqueNames`,
  `uniqueDescriptions`, `settingsJsonValid`, `permissionsFormat`,
  `hookCommandsValid`, `hooksFormat`, `localSettingsIgnored`, `mcpServersValid`,
  `mcpConfigFormat`, `pluginFormat`, and `okfBundleFormat` cover the rest of the
  agent configuration. The last four validate `.mcp.json`,
  `.claude-plugin/plugin.json` and an Open Knowledge Format bundle, none of which
  this repository ships — they pass on the absent file and start enforcing the
  moment one is added.
- Two more rules ship but are **not** wired here: `subAgentFormat`
  (`.claude/agents`) and `commandFormat` (`.claude/commands`). Unlike
  `pluginFormat`, a *configured* definition directory must exist — an absent one
  counts as a build-setup mistake — so they can only be wired once the
  directory does. Add the directory and the rule together.
- Every rule extends a common base offering `severity` (`error`, the default, or
  `warn` to log without failing), an optional `reportFile` for a self-contained
  HTML report, and an optional `baselineFile` that suppresses already-accepted
  violations so a new rule can be gated without clearing the backlog first. See
  *CLAUDE.md enforcement* in AGENTS.md before adding or configuring a rule.

The check is opt-in via the `claude-md-enforce` profile and needs a two-phase
build, since a maven-enforcer rule must be resolvable as a JAR before the build
runs:

```bash
mvn -pl claude-code-enforcer -am install   # 1. publish the rule locally
mvn -N validate -DenforceClaudeMd          # 2. quick root-only doc check
```

`.github/workflows/maven.yml` is the only CI workflow that opts in
(`mvn -B package -DenforceClaudeMd`); ordinary builds are unaffected.

## Dependencies

Use the existing Maven dependencies. **Always ask before adding a new one.**
