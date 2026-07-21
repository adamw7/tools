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
- **Data** (`data`) — CSV/GZip/JDBC/Parquet data sources (in-memory and iterative), a
  column-uniqueness/key finder, open-addressing map/set data structures, and an
  MCP server exposing the uniqueness checker.
- **Claude Code adoption** (`adopt`) — a pipeline that adopts Claude Code into a
  GitHub repo: check the required tools (`git`, `claude`, `gh`) are installed,
  clone, create a feature branch, `claude init` to generate
  `CLAUDE.md` and commit it, wire a build-tool-aware `CLAUDE.md` guard into the
  repo (the `claude-code-enforcer` rule for Maven `pom.xml`, an `enforceClaudeMd`
  guard task for Gradle, and a GitHub Actions workflow plus
  `.github/claude-md-guard.sh` check as the build-tool-agnostic fallback) and
  commit that, verify the guard passes on the
  generated file, then push the branch and open a pull request (`gh pr create`)
  with metadata from `PullRequestOptions`; the default branch is never written to
  directly.

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
├── adopt                  # adopts Claude Code into a GitHub repo (clone, branch, trust, init, enforcer, verify, push, PR)
├── grpc-example           # end-to-end gRPC example
├── assembly               # executable jar-with-dependencies (SampleApp)
└── data-test              # standalone test module (not in root <modules>)
```

Base Java package: `io.github.adamw7` (`io.github.adamw7.context` for the context
module, `io.github.adamw7.tools.*` elsewhere).

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
mvn -P integration-tests verify   # MCP integration tests (*IT)
mvn -Pcoverage verify             # JaCoCo coverage (fails under 80%)
mvn -Ppitest test                 # PIT mutation testing
```

Use `clean` after removing a code-generation source, so stale generated builders
in `target/` cannot mask the change.

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
  a **5-second per-test timeout** (configured on the surefire plugin in the root
  `pom.xml`) — above the cold-fork warmup, which stretches under the parallel
  `-T1C` build's CPU contention, but low enough to catch a test doing real work —
  so keep unit tests fast; a genuinely heavier test opts out with an explicit
  `@Timeout` and a comment explaining why. A looser
  **10-second lifecycle-method timeout** (15 s under coverage) covers heavier
  shared setup like `@BeforeAll`, and surefire's **300-second
  `forkedProcessTimeoutInSeconds`** kills a fork that hangs outright.
- **Unit tests run with the network off.** The `data` module's
  `NetworkOffExtension` engages the `Switch` kill-switch before any test runs, so
  a unit test can never open an outbound connection; the failsafe `*IT` tests are
  unaffected. See *Testing* in AGENTS.md.
- **Architecture tests** (ArchUnit) live in each module's `.architecture` test
  package and enforce package layering and coding rules — data-source contracts
  must not depend on their implementations, the uniqueness core must not depend
  on its MCP adapter, JDBC stays confined to the `source.db` package, loggers are
  `private static final`, mutable static state is `volatile`, fields are never
  `Optional`, date/time uses `java.time` (not the legacy `Date`/`Calendar` API),
  production code logs through log4j2 (no `System.out`/`err`,
  `java.lang.System.Logger`, `printStackTrace`, or `System.exit`), and packages
  stay free of cycles. A companion `TestConventionsArchitectureTest` pins
  conventions on the tests themselves — test methods must sit in `*Test`/`*IT`
  classes, no `@Disabled`, JUnit 5 only, no `System.out`/`err`, and no
  `Thread.sleep`. Keep new code within these rules.
- **MCP integration tests** (`*IT`) are gated behind the `integration-tests`
  profile.

## Dependencies

Use the existing Maven dependencies. **Always ask before adding a new one.**
