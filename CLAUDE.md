# CLAUDE.md

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
  project-tree builder that assembles context for gen-AI agents, plus an MCP
  server exposing `project_tree`, `find_context`, and `estimate_tokens`.
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
  `AGENTS.md`) so it satisfies the guard about to be wired in, commit it, wire a
  build-tool-aware `CLAUDE.md` guard into the repo (the `claude-code-enforcer`
  rule for Maven `pom.xml`, an `enforceClaudeMd` guard task for Gradle, and a
  GitHub Actions workflow plus `.github/claude-md-guard.sh` check as the
  build-tool-agnostic fallback) and commit that, verify the guard passes on the
  generated file, then push the branch and open a pull request (`gh pr create`)
  with metadata from `PullRequestOptions` (exposed as CLI flags such as
  `--title`, `--reviewer`, and `--draft`); the default branch is never written to
  directly. One run adopts a list of repositories — repeatable `--repo <url>` or
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

Module map (root reactor: `claude-code-enforcer`, `mcp-common`, `data`, `code`,
`adopt`, `grpc-example`, `assembly`; `data-test` is built separately):

```
tools (root pom, packaging=pom)
├── claude-code-enforcer   # custom maven-enforcer rules validating CLAUDE.md/AGENTS.md & agent config
├── mcp-common             # shared MCP server scaffolding
├── data                   # data sources, uniqueness checks, structures, MCP server
├── code
│   ├── protogen-maven-plugin       # compile-time-safe protobuf builder generator
│   ├── protogen-maven-plugin-test  # integration tests for the plugin
│   └── context                     # class-usage context finder + MCP server
├── adopt                  # adopts Claude Code into a GitHub repo (clone, build-tool check, branch, trust, init, conform, enforcer, verify, push, PR)
├── grpc-example           # end-to-end gRPC example
├── assembly               # executable jar-with-dependencies (SampleApp)
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
mvn -pl data test                 # tests for a single module
mvn -B package                    # build without installing (what CI runs)
mvn -P integration-tests verify   # integration tests (*IT): MCP servers, real-GitHub adoption
mvn -Pcoverage verify             # JaCoCo coverage (fails under 80% instruction or branch)
mvn -Ppitest install              # PIT mutation testing (needs a phase past package)
```

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
  adoption spawns processes only in its `command` package and only its clone
  step reads the credentialled clone URL, loggers are
  `private static final`, abstract types carry an `Abstract` prefix, public
  fields are `final`, mutable static state is `volatile`, fields are never
  `Optional`, date/time uses `java.time` (not the legacy `Date`/`Calendar` API),
  production code logs through log4j2 (no `System.out`/`err`,
  `java.lang.System.Logger`, `printStackTrace`, or `System.exit`), and packages
  stay free of cycles. A companion `TestConventionsArchitectureTest` pins
  conventions on the tests themselves — test methods must sit in `*Test`/`*IT`
  classes, no `@Disabled`, JUnit 5 only, no `System.out`/`err`, and no
  `Thread.sleep`; in `adopt` it also pins that step tests never spawn a real
  process and the `*IT`s never push or open a pull request. Keep new code within
  these rules.
- **Integration tests** (`*IT`) are gated behind the `integration-tests` profile
  and cover what needs something real: the MCP servers over HTTP, and `adopt`'s
  `MultiRepoAdoptionIT`, which clones real GitHub sample repositories to prove a
  batch gives each repository its own checkout. It stops at the branch step, so
  it never pushes or opens a pull request. See *Testing* in AGENTS.md.

## Agent configuration

- `.claude/skills/` holds seven project skills that carry the detail this file
  only summarises: `data-sources`, `git-commit`, `java-code-review`,
  `maven-conventions`, `protogen`, `solid-principles`, and
  `testing-conventions`. Prefer loading the relevant skill over re-deriving a
  convention.
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
- `memoryImports`, `noSecrets`, `skillFilesExist`, `uniqueNames`,
  `uniqueDescriptions`, `settingsJsonValid`, `permissionsFormat`,
  `hookCommandsValid`, `hooksFormat`, `mcpServersValid`, and `mcpConfigFormat`
  cover the rest of the agent configuration.

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
