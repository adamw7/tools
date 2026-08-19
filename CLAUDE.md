# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with
code in this repository.

[AGENTS.md](AGENTS.md) is the single source of truth for agent instructions —
repository overview, module layout, build and test commands, CI, environment, and
the release process. This file is a quick-reference summary of the essentials;
when the two disagree, AGENTS.md wins, and the build's `crossDocConsistency`
enforcer rule fails if a key fact (e.g. the Java version) drifts apart between
them.

Detail lives in the thirteen skills under `.claude/skills/` (see *Agent
configuration*). **Prefer loading the relevant skill over re-deriving a
convention.**

## Project

`tools` is a multi-module Maven library of Java tooling. Build with Maven 3.9.X:
run `mvn install` from the repository root.

- **Code generation** (`code/protogen-maven-plugin`) — a Maven plugin that
  generates protobuf builders enforcing missing required fields at **compile
  time** (proto2 `required`, proto3 presence-aware accessors, `oneof`
  discriminators). Skill: `protogen`.
- **Context engineering** (`code/context`) — a regex-based class-usage finder and
  project-tree builder that assembles context for gen-AI agents, an emitter for
  Google's Open Knowledge Format (OKF v0.2 bundles of markdown concept
  documents), plus an MCP server exposing `project_tree`, `find_context`,
  `estimate_tokens` and `okf_bundle`. Skill: `context-finder`.
- **Data** (`data`) — data sources (CSV, GZip, JDBC, Parquet via an in-process
  DuckDB engine, plus forward-only JSON/YAML/TOON), each in in-memory and
  iterative variants. Schema-aware sources implement the narrower
  `ColumnarDataSource` contract, so a caller needing the schema (e.g. the
  uniqueness check) cannot be handed a forward-only source. Also a
  column-uniqueness/key finder, open-addressing collections, and an MCP server
  exposing the uniqueness checker. Skill: `data-sources`.
- **Claude Code adoption** (`adopt`) — an ordered pipeline that adopts Claude
  Code into a GitHub repository: check the toolchain, clone, check the cloned
  project's own build tool, branch, trust the checkout, `claude init` and conform
  the generated `CLAUDE.md`, wire a build-tool-aware guard in, verify it, push,
  and open a pull request. The default branch is never written to, clone-URL
  credentials are masked in everything the run reports and are never left in the
  checkout's `.git/config`, `--dry-run` leaves out the push and the pull
  request entirely, a `git` or `gh` the network refused is tried again with a
  backoff (`--retries`) while every other failure is reported at once, and one
  run adopts a list of repositories without letting a failure stop the rest.
  Skill: `adopt-pipeline`.
- **Markdown reading** (`markdown-common`) — the dependency-free reader both the
  `claudeMdFormat` rule and `adopt`'s conformer parse a document with, so the
  checker and the rewriter cannot disagree about what is code, what is
  commented out, and what heading a line declares. Add no dependency to it.

Module map (root reactor: `markdown-common`, `claude-code-enforcer`,
`test-common`, `mcp-common`, `data`, `code`, `adopt`, `grpc-example`,
`assembly`; `data-test` is built separately):

```
tools (root pom, packaging=pom)
├── markdown-common        # shared Markdown reader (lines + code/comment masks), dependency-free
├── claude-code-enforcer   # custom maven-enforcer rules validating CLAUDE.md/AGENTS.md & agent config
├── test-common            # shared ArchUnit rule libraries and test assertions (test-jar)
├── mcp-common             # shared MCP server scaffolding
├── data                   # data sources, uniqueness checks, structures, MCP server
├── code
│   ├── protogen-maven-plugin       # compile-time-safe protobuf builder generator
│   ├── protogen-maven-plugin-test  # integration tests for the plugin
│   └── context                     # class-usage context finder + MCP server
├── adopt                  # adopts Claude Code into a GitHub repo
├── grpc-example           # end-to-end gRPC example
├── assembly               # runnable SampleApp distribution (launcher jar + lib/)
└── data-test              # standalone test module (not in root <modules>)
```

Base Java package: `io.github.adamw7` (`io.github.adamw7.context` for the context
module, `io.github.adamw7.tools.*` elsewhere).

Three MCP servers ship here (`data` uniqueness, `code/context`, `adopt`), each a
Spring Boot app with a `Main.java` entry point supporting stdio (default),
streamable HTTP, or stateless HTTP; each has its own `MCP_USAGE.md` next to its
`mcp` package.

Further reading: [README.md](README.md) for worked examples,
[docs/c4-architecture.md](docs/c4-architecture.md) for the C4 model,
[docs/compile-time-safe-builders.md](docs/compile-time-safe-builders.md) for the
builder walkthrough, [docs/adr](docs/adr) for the architecture decision records,
[k8s/README.md](k8s/README.md) for running `SampleApp` on minikube, and
[SECURITY.md](SECURITY.md) for the private disclosure process.

## Java version

Java 25. A JDK 25 must be on the `PATH` with `JAVA_HOME` set. In Claude Code
web/remote sessions the `.claude/hooks/session-start.sh` hook installs
`openjdk-25-jdk` and pre-fetches dependencies.

## Maven

All dependency versions and scopes are defined only in the root `pom.xml` under
`<dependencyManagement>`, and all plugin versions only under `<pluginManagement>`.
Module poms reference both without versions. Skill: `maven-conventions`.

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

**Always pair `-pl` with `-am`.** `mvn -pl data test` fails before compiling: the
root pom's `ReactorModuleConvergence` rule rejects a reactor whose module parents
are absent from it, and sibling `-SNAPSHOT`s (e.g. `mcp-common`) do not resolve
from the local repo until installed.

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

Other things worth knowing:

- Maven 3.9.X is enforced, not just recommended: the root `enforce` execution
  pins `requireMavenVersion [3.9,4.0)` and `requireJavaVersion 25`, so a Maven 4
  or older-JDK build fails at `validate` rather than midway.
- Use `clean` after removing a code-generation source, so stale generated
  builders in `target/` cannot mask the change.
- `.mvn/maven.config` passes `--no-transfer-progress` and `-T1C`, so every build
  from the repo root is quiet and runs one thread per core; use `-T1` for a
  serial build.
- The root pom lints `scripts/**/*.sh` and `.claude/hooks/**/*.sh` with
  `shellcheck-maven-plugin` using the binary embedded in the plugin jar, so the
  lint needs no installed `shellcheck` and works offline, and a finding **fails
  the build** (`failBuildIfWarnings`) rather than passing as a warning. Skip it
  with `-Dskip.shellcheck=true`. The `scripts` ones are developer-environment
  helpers outside the build, kept as parallel `scripts/linux` and
  `scripts/windows` variants.

## Principles for Java Development

- **SOLID principles** for all code.
- **Clean code**: short methods, meaningful parameter names.
- **No `continue` or `break`** statements.
- **Match the surrounding code** — naming, comment density, and idiom.

## Testing

Write unit tests for all new logic — behavior, edge cases, and error paths.
Skill: `testing-conventions`.

- **Unit tests** run in the normal `test`/`package` lifecycle under a **5-second
  per-test timeout** (8 s under coverage) configured on surefire in the root
  `pom.xml`: above the cold-fork warmup under the parallel `-T1C` build, but low
  enough to catch a test doing real work. A genuinely heavier test opts out with
  an explicit `@Timeout` and a comment saying why. A looser **10-second**
  lifecycle-method timeout (15 s under coverage) covers shared setup like
  `@BeforeAll`, and surefire's **300-second** `forkedProcessTimeoutInSeconds`
  kills a fork that hangs outright.
- **Unit tests run with the network off.** The `data` module's
  `NetworkOffExtension` engages the `Switch` kill-switch before any test runs, so
  a unit test can never open an outbound connection; the failsafe `*IT`s are
  unaffected. A single test opts in explicitly with `@NetworkOff`, which engages
  the switch even without the surefire guard property (e.g. from an IDE).
- **Architecture tests** (ArchUnit) live in each module's `.architecture` test
  package and pin package layering and coding rules: loggers are `private static
  final`, abstract types carry an `Abstract` prefix, public fields are `final`,
  mutable static state is `volatile`, fields are never `Optional`, date/time uses
  `java.time`, production code logs through log4j2 (no `System.out`/`err`,
  `printStackTrace` or `System.exit`), and packages stay free of cycles — plus
  per-module layering (data-source contracts must not depend on their
  implementations, the uniqueness core must not depend on its MCP adapter, JDBC
  stays in `source.db`, the adoption spawns processes only in its `command`
  package, every enforcer rule is a public `@Named` type that neither spawns a
  process nor reaches the network). A companion `TestConventionsArchitectureTest`
  pins the tests themselves: methods only in `*Test`/`*IT` classes, no
  `@Disabled`, JUnit 5 only, no `System.out`/`err`, no `Thread.sleep`. The
  repo-wide rules live once in `test-common` and are imported with
  `ArchTests.in(...)`.
- **Integration tests** (`*IT`) are gated behind the `integration-tests` profile
  and cover what needs something real: the MCP servers over HTTP, `adopt`'s
  multi-repository adoption against real GitHub URLs (it stops at the branch
  step, so it never pushes or opens a pull request), and `claude-code-enforcer`'s
  real Maven builds, which exercise the pom-to-rule seam a unit test skips. The
  profile is declared per module — `data`, `code/context`, `adopt`,
  `claude-code-enforcer` — not in the root pom. Both modules also meet real
  repositories cloned from GitHub — eight for the enforcer, seven for `adopt`:
  the enforcer points every shipped rule at them, to prove a project nobody
  prepared for the rules gets an actionable verdict rather than a build broken on
  the rules' own account, and `adopt` adopts them, to prove the guard it wires
  into a build file somebody else wrote is an addition to it and nothing more,
  and that a file the project already keeps where a starter asset goes is left
  exactly as it was cloned.

## Continuous integration and commits

One workflow gates pull requests to `main`: `maven.yml` bootstraps the enforcer
rule and then runs `mvn -B package -DenforceClaudeMd` — the only workflow that
runs the doc checks. Its `package` step is capped at **120 seconds**, so a change
that slows the build down red-flags the PR; profile it rather than raising the
cap. Everything else runs on a schedule or a release, so a green PR is not proof
the whole matrix passes:

- **Scheduled** — `integration-tests.yml` daily; `codeql.yml`, `coverage.yml` and
  `docker.yml` Saturdays; `pitest.yml` and `maven-windows.yml` Sundays. Keep
  path, line-ending and file-locking assumptions platform-neutral for the Windows
  build, and dispatch `docker.yml` by hand after changing the assembly or the
  Dockerfile rather than waiting for the weekly run.
- **On a GitHub release** — `docker.yml` again, pushing the multi-arch image to
  GHCR; `maven-publish.yml` (GitHub Packages); `central-publish.yml` (Maven
  Central).

Every workflow builds on JDK 25 (Temurin). Use clear, conventional commit
messages — the `git-commit` skill writes them with this repository's real module
scopes — keep changes focused, and add or update tests alongside the code.
**Do not open a pull request unless explicitly asked.**

## Agent configuration

- `.claude/skills/` holds thirteen skills carrying the detail this file only
  summarises — per module: `data-sources`, `context-finder`, `protogen`,
  `adopt-pipeline`, `mcp-server`, `enforcer-rules`; across the repo:
  `doc-contract`, `git-commit`, `java-code-review`, `maven-conventions`,
  `solid-principles`, `testing-conventions`, `text-parsers`.
- `.claude/agents/` holds two sub-agents for the jobs that read far more than
  they answer: `build-verifier` (runs the right Maven command and reports the
  verdict plus only the failing output) and `doc-drift-auditor` (reports what the
  documents disagree on that no rule pins).
- `.claude/commands/` holds three slash commands for the procedures whose omitted
  step fails late: `/doc-check`, `/module-build`, `/new-enforcer-rule`.
- `.claude/settings.json` allows the `mvn` and archive-inspection Bash commands
  and wires the `SessionStart` hook; `.claude/hooks/session-start.sh` provisions
  the JDK and warms the Maven cache in web/remote sessions.
- Personal overrides belong in `.claude/settings.local.json`, which is
  gitignored — the `localSettingsIgnored` rule fails the build if that entry
  disappears.
- See *Agent configuration* in AGENTS.md for what each skill covers and which
  agent files this repository deliberately does not ship.

## CLAUDE.md enforcement

The `claude-code-enforcer` module fails the build when this file, `AGENTS.md`,
`README.md` or the `.claude` configuration is missing, malformed, or
inconsistent. Skills: `doc-contract` (editing the docs), `enforcer-rules`
(changing a rule). What matters when editing this file:

- It must keep the `# CLAUDE.md` title, reference `AGENTS.md`, and keep every
  required heading (`## Project`, `## Java version`, `## Maven`, `## Principles
  for Java Development`, `## Testing`, `## Dependencies`).
- `contextBudget` caps it at **32 KB** — it is loaded into every session, so put
  new detail in AGENTS.md or a skill rather than here.
- `moduleMapConsistency` requires every `<module>` of the root pom to be
  mentioned in both CLAUDE.md and AGENTS.md; `crossDocConsistency` pins facts
  shared with AGENTS.md (the Java version) and `readmeConsistency` pins the
  README against AGENTS.md (the protobuf major version).
- The remaining rules — `agentsMdFormat`, `memoryImports`, `noSecrets`,
  `skillFilesExist`, `subAgentFormat`, `commandFormat`, `uniqueNames`,
  `uniqueDescriptions`, `settingsJsonValid`, `permissionsFormat`,
  `hookCommandsValid`, `hooksFormat`, `localSettingsIgnored`, `mcpServersValid`,
  `mcpConfigFormat`, `pluginFormat`, `okfBundleFormat` — cover AGENTS.md and the
  agent configuration. The last four target files this repository does not ship:
  they pass on the absent file and start enforcing the moment one is added. The
  three definition rules are the opposite — a *configured* directory must exist,
  so `.claude/skills`, `.claude/agents` and `.claude/commands` were each added
  together with the rule reading them.
- `claudeCodeProject` runs all of the above from a `projectDir` alone, finding
  each input by convention and skipping the parts a project has nothing for. It
  is what another project wires; this repository keeps the rules listed
  individually, because its profile is the worked example and two of its rules
  take patterns no convention supplies.
- Every rule offers `severity` (`error` by default, or `warn` to log without
  failing — anything else is refused), an optional `reportFile` for an HTML
  report, and an optional `baselineFile` that suppresses already-accepted
  violations. All three have build-wide defaults: `-Dclaude.enforcer.severity`,
  `-Dclaude.enforcer.reportDir`, `-Dclaude.enforcer.baselineDir`.
- `claudeMdFormat` and `agentsMdFormat` take `autoFix`, which repairs the
  structural problems they would otherwise only report.

The check is opt-in via the `claude-md-enforce` profile and needs a two-phase
build, since a maven-enforcer rule must be resolvable as a JAR before the build
runs:

```bash
mvn -pl claude-code-enforcer -am install   # 1. publish the rule locally
mvn -N validate -DenforceClaudeMd          # 2. quick root-only doc check
```

`.github/workflows/maven.yml` is the only CI workflow that opts in; ordinary
builds are unaffected. The same rules run without Maven at all —
`java -cp tools.claude-code-enforcer.jar:enforcer-api.jar
io.github.adamw7.tools.enforcer.cli.Main <project-directory>` — which is the path
for a pre-commit hook or a project built with something else.

## Dependencies

Use the existing Maven dependencies. **Always ask before adding a new one.**
