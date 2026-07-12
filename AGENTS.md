# AGENTS.md

Guidance for AI coding agents working in this repository. Human contributors
may find it useful too. This file follows the [agents.md](https://agents.md)
convention and is the single source of truth for agent instructions;
`CLAUDE.md` defers to it.

## Project overview

`tools` is a library of Java tooling for various purposes. It is a multi-module
Maven project. The notable capabilities are:

- **Code generation** (`code/protogen-maven-plugin`) — a Maven plugin that
  generates protobuf builders which detect missing required fields at
  **compile time** instead of runtime (shift-left). Supports both proto2 and
  proto3: proto2 `required` fields are enforced by the builder chain, while
  proto3 (which has no required fields) generates all-optional builders with
  presence-aware `hasXxx()` accessors (only for message fields and explicit
  `optional` fields). `oneof` groups get a `getXxxCase()` discriminator and a
  `clearXxx()` for the whole group, so the selected member is inspectable and
  resettable through the builder chain.
- **Context engineering** (`code/context`) — a fast, regex-based finder that
  builds the tree of classes used by a given class, plus a `ProjectTreeBuilder`
  that scans a whole Java project into a tree of folders, files and
  dependencies, to assemble context for gen-AI agents working with Java code. An
  **MCP server** (in the `io.github.adamw7.context.mcp` package) exposes the
  project-tree and context-finder tools over stdio, streamable HTTP, stateless
  HTTP or HTTP+SSE.
- **Data** (`data`) — data sources (CSV, GZip, JDBC, Parquet; in-memory and
  iterative loading). Parquet files are read through an in-process DuckDB engine,
  so they expose their columns and rows like any other JDBC-backed source.
  Schema-aware sources that know their columns up front implement
  `ColumnarDataSource` (a narrower contract than `IterableDataSource`), so
  callers that need the schema — such as the uniqueness check — cannot be handed
  a forward-only source (JSON/YAML/TOON) that would only answer with `null`.
  Also a uniqueness-checking tool (finds whether a subset of columns can
  serve as a key, and searches for a smaller key), data structures
  (`OpenAddressingMap`, an `OpenAddressingSet`, and the primitive `int`-keyed
  `IntKeyOpenAddressingMap`), and an **MCP server** exposing the
  uniqueness checker as a tool for AI assistants.

See [README.md](README.md) for worked code examples of each capability, and
[docs/c4-architecture.md](docs/c4-architecture.md) for a C4 model
(System Context → Containers → Components, as Mermaid diagrams) of how the
modules and MCP servers fit together.

## Module layout

```
tools (root pom, packaging=pom)
├── claude-code-enforcer        # custom maven-enforcer rule validating CLAUDE.md
├── mcp-common                  # shared MCP server scaffolding (transport wiring, tool SPI)
├── data                        # data sources, uniqueness checks, structures, MCP server
├── code
│   ├── protogen-maven-plugin       # the proto2 builder-generating Maven plugin
│   ├── protogen-maven-plugin-test  # integration tests / use cases for the plugin
│   └── context                     # regex-based class-usage context finder
├── grpc-example                # end-to-end gRPC example with compile-time-safe builders
├── assembly                    # builds an executable jar-with-dependencies
│                               #   (mainClass: io.github.adamw7.tools.data.SampleApp)
└── data-test                   # standalone test module for the data module
```

Root reactor modules are `claude-code-enforcer`, `mcp-common`, `data`, `code`,
and `assembly`.
The `data-test` module is built separately (it is not in the root `<modules>`
list).

Base Java package: `io.github.adamw7` (`io.github.adamw7.context` for the
context module, `io.github.adamw7.tools.*` elsewhere).

The repository ships two MCP servers, each a Spring Boot app whose entry point
is `Main.java` and which supports stdio (default), streamable HTTP
(`--transport.mode=streamable-http`, served at `/mcp`), stateless HTTP
(`--transport.mode=stateless-http`, session-less, also served at `/mcp`), or the
legacy HTTP+SSE transport (`--transport.mode=sse`, event stream at `/sse`,
messages at `/mcp/message`):

- The uniqueness-checker server in
  `data/src/main/java/io/github/adamw7/tools/data/uniqueness/mcp/`; see its
  [MCP_USAGE.md](data/src/main/java/io/github/adamw7/tools/data/uniqueness/mcp/MCP_USAGE.md).
- The context-engineering server in
  `code/context/src/main/java/io/github/adamw7/context/mcp/`, exposing the
  `project_tree`, `find_context` and `estimate_tokens` tools; see its
  [MCP_USAGE.md](code/context/src/main/java/io/github/adamw7/context/mcp/MCP_USAGE.md).

## Environment & toolchain

- **Java 25** (`maven.compiler.source`/`target` = 25). A JDK 25 must be on the
  `PATH` with `JAVA_HOME` set. In Claude Code web/remote sessions the
  `.claude/hooks/session-start.sh` hook installs `openjdk-25-jdk`, exports
  `JAVA_HOME`, and pre-fetches dependencies (`mvn dependency:go-offline`).
- **Maven 3.9.x.** Build from the repository root.

## Helper scripts

`scripts/` holds developer-environment conveniences (not part of the Maven
build), with parallel `linux/` (`*.sh`) and `windows/` (`*.bat`/`*.ps1`)
variants:

- `install-jdk-25` — downloads and installs Eclipse Temurin JDK 25 (the
  toolchain the build requires); skips the download when JDK 25 is already
  present.
- `generate-maven-update-reports` — runs the `versions-maven-plugin`
  (`plugin-updates-aggregate-report` + `dependency-updates-aggregate-report`)
  to report available plugin and dependency updates.
- `update-claude-code` — runs `claude update` to update the Claude Code CLI.
- `update-git-client` — upgrades the system `git` via the host package manager.
- `update-git-repos-async` — `git pull`s every git repository in the script's
  parent directory in parallel.

## Build, test, and run

```bash
# Install the custom enforcer rule into the local repo. Only needed if you want
# to run the CLAUDE.md check locally (see "CLAUDE.md enforcement" below).
mvn -pl claude-code-enforcer -am install

# Full clean build + install to local repo (use clean — see note below)
mvn clean install

# Faster incremental build when you have NOT removed any generation sources
mvn install

# Build without installing (what CI runs)
mvn -B package

# Run the tests for a single module
mvn -pl data test

# Build the standalone data-test module
mvn -pl data-test -am test
```

**Always use `clean` after removing a source of code generation.** The build
generates protobuf builders into `target/`; stale generated output from a
previous build can otherwise linger and mask the change. If you have not
removed anything, plain `mvn install` is fine and faster.

**Quiet builds.** `.mvn/maven.config` passes `--no-transfer-progress` to every
`mvn` invocation from the repo root, so builds never print the
`Downloading from.../Downloaded from...` artifact-transfer noise — locally or in
CI. The workflows also pass `-ntp` explicitly on each `mvn` command so the
quiet behavior does not depend on the checkout picking up `.mvn/maven.config`.

CI (`.github/workflows/maven.yml`) installs the enforcer rule
(`mvn -B -pl claude-code-enforcer -am install -DskipTests`) and then runs
`mvn -B package -DenforceClaudeMd` on JDK 25 (Temurin) for every push and for
pull requests targeting `main`. The bootstrap step skips tests because it only
needs to publish the enforcer JAR for the plugin to load; the module's own test
suite still runs in the `package` step. It is the only workflow that runs the
CLAUDE.md check; the other workflows build normally and are unaffected.

## Testing, coverage & mutation testing

- **Unit tests** run in the normal `test`/`package` lifecycle
  (`mvn -pl <module> test`). Write tests for all new logic — behavior, edge
  cases, and error paths. Surefire enforces a **900-millisecond per-test
  timeout** (the `junit.jupiter.execution.timeout.testable.method.default` JUnit
  config parameter set on the surefire plugin in the root `pom.xml`), so a unit
  test that runs 900 ms or longer fails the build. The limit sits above the
  one-time JVM/class-loading warmup a cold fork pays (~0.7s at most, and only
  for the first test to touch a heavy dependency) so it stays green when a
  single class is run in isolation, while still catching a test that does real
  work. To keep that warmup below the limit, the one module that uses Mockito
  (`protogen-maven-plugin`) pre-loads it as a `-javaagent` in surefire, so the
  byte-buddy self-attach happens at JVM startup rather than inside the first
  timed test method. Keep unit tests fast; a genuinely heavier test (e.g. one
  that shells out to `protoc` or streams a large data set) opts out with an
  explicit class- or method-level `@Timeout` carrying a comment that says why.
  Failsafe integration tests (`*IT`) do not inherit this limit, and ArchUnit
  tests run on a separate engine that the JUnit timeout does not apply to.
- **Architecture tests** (ArchUnit) run in every module as ordinary JUnit tests,
  under an `...architecture` test package (e.g.
  `io.github.adamw7.tools.data.architecture.DataArchitectureTest`). They analyse
  only production classes and pin the package layering and coding rules so they
  cannot rot: data-source contracts in `source.interfaces` must not depend on
  their `source.db`/`source.file` implementations; the uniqueness core must not
  depend on its MCP adapter; the `structure` collections stay decoupled from data
  sources; loggers are `private static final`; abstract types carry an `Abstract`
  prefix; public fields are `final`; and production code logs through log4j2
  (never `System.out`/`err`, `java.util.logging`, `printStackTrace`, or
  `System.exit`), with packages kept free of cycles. New code must satisfy these
  rules or the module's test suite fails.
- **MCP integration tests** are gated behind the `integration-tests` profile
  (defined in `data` and `code/context`) and exercise the MCP servers over
  streamable HTTP: `mvn -P integration-tests verify`. Test classes ending in
  `IT` belong to this profile.
- **Coverage** is the opt-in `coverage` profile (JaCoCo): `mvn -Pcoverage verify`
  produces reports at `**/target/site/jacoco/` and **fails the build** if bundle
  instruction coverage drops below **80%** (`COVEREDRATIO >= 0.80`).
- **Mutation testing** is the opt-in `pitest` profile (PIT + JUnit 5):
  `mvn -Ppitest test` writes HTML/XML reports to `**/target/pit-reports/`. It
  excludes `*IT` integration tests and does not fail when a class has no
  mutations.

## Continuous integration

Workflows live in `.github/workflows/`. Only the first three below gate pull
requests to `main`; the rest run on a schedule (or manually).

| Workflow | Trigger | What it runs |
| --- | --- | --- |
| `maven.yml` | push, PR → `main` | Installs the enforcer rule, then `mvn -B package -DenforceClaudeMd` on JDK 25 — the **only** workflow that runs the CLAUDE.md/AGENTS.md checks. |
| `docker.yml` | push, PR → `main` | `mvn -B package`, then builds the Docker image from `assembly/Dockerfile`. |
| `codeql.yml` | push, PR → `main`; weekly | CodeQL security/static analysis for Java (autobuild). |
| `integration-tests.yml` | daily | `mvn -P integration-tests verify` (MCP streamable-HTTP integration tests). |
| `coverage.yml` | weekly | `mvn verify -Pcoverage`, uploads JaCoCo reports as an artifact. |
| `pitest.yml` | weekly; manual | `mvn test -Ppitest`, uploads PIT mutation reports as an artifact. |
| `maven-publish.yml` | on GitHub release | Deploys to **GitHub Packages** (`-P github-packages`). See "Releasing". |
| `central-publish.yml` | on GitHub release; manual dispatch | Deploys to **Maven Central** (`-P release`), or a staged-only dry run on manual dispatch. See "Releasing". |

Every workflow builds on JDK 25 (Temurin) and passes `-ntp` to keep the log
free of artifact-transfer noise.

## CLAUDE.md enforcement

The `claude-code-enforcer` module is a set of custom `maven-enforcer-plugin` rules
that **fail the build** when the repository's agent files are missing or
malformed. The rules run at the **root** only, in the `claude-md-enforce`
profile:

- `ClaudeMdFormatRule` (`claudeMdFormat`) checks that `CLAUDE.md` exists and is
  non-empty, starts with the `# CLAUDE.md` title (a leading UTF-8 BOM is
  tolerated), references `AGENTS.md`, and contains every required section
  heading (`## Project`, `## Java version`, `## Maven`,
  `## Principles for Java Development`, `## Testing`, `## Dependencies`).
- `AgentsMdFormatRule` (`agentsMdFormat`) applies the same structural checks to
  `AGENTS.md`: it must start with the `# AGENTS.md` title and contain every
  required section heading (`## Project overview`, `## Module layout`,
  `## Environment & toolchain`, `## Build, test, and run`,
  `## Code style & conventions`, `## Releasing`, `## Pull requests & commits`).
- `SkillFilesExistRule` (`skillFilesExist`) checks that every skill directory
  under `.claude/skills` contains a non-empty `SKILL.md` whose YAML front matter
  declares every required key (`name`, `description` by default, overridable via
  `requiredKeys`). The `name` must be lower-case kebab-case, at most 64
  characters, and match the skill's directory name; the `description` must be
  non-empty and within `maxDescriptionLength`. Setting `allowedFrontMatterKeys`
  also reports unknown keys, catching typos such as `descripton`.
- `SubAgentFormatRule` (`subAgentFormat`) applies the same front-matter checks to
  every `*.md` sub-agent definition under a configured `agentsDir`; the `name`
  must match the file name, and an optional `allowedModels` list rejects an
  unknown `model`.
- `CommandFormatRule` (`commandFormat`) treats every `*.md` file under a
  configured `commandsDir` (e.g. `.claude/commands`) as a custom slash command:
  it must be non-empty and its file name must be lower-case kebab-case (the
  command's name comes from the file name). Front matter is optional, but a
  present `description` must be non-empty, a present `model` must be in an
  optional `allowedModels` list, and `allowedFrontMatterKeys` reports unknown
  keys.
- `SettingsJsonValidRule` (`settingsJsonValid`) checks that `.claude/settings.json`
  exists and is valid JSON, and can assert `requiredPermissions` and
  `forbiddenPermissions` against the `permissions.allow` list.
- `HookCommandsValidRule` (`hookCommandsValid`) validates the `hooks` section of
  `.claude/settings.json`: every event maps to an array of groups, each group
  carries a `hooks` array, and every hook declares a non-blank `type` (a
  `command` hook also a non-blank `command`). A `$CLAUDE_PROJECT_DIR`-rooted
  script command is resolved against `projectDir` and must exist on disk. An
  optional `allowedEvents` list rejects mistyped events and
  `validateScriptReferences` toggles the script-existence check.
- `McpServersValidRule` (`mcpServersValid`) validates the project's `.mcp.json`.
  The file is optional, so an absent one passes; when present it must be
  non-empty valid JSON, and every entry under `mcpServers` must be a JSON object
  with a well-formed transport (a `stdio` server needs a `command`; an `sse` or
  `http` server needs a `url`). An explicit `type` outside `allowedTypes`
  (`stdio`, `sse`, `http` by default) is rejected, and `requiredServers` /
  `forbiddenServers` assert which servers must or must not be declared.
- `UniqueNamesRule` (`uniqueNames`) gathers the names of every command,
  sub-agent, and skill from the configured `commandsDir`, `agentsDir`, and
  `skillsDir` (file name for commands and sub-agents, directory name for skills)
  and fails when a name is used more than once, naming every source that uses it.
  At least one directory must be configured, any configured directory must
  exist, and uniqueness is checked across all of them at once, so a command that
  clashes with a skill is caught just like two clashing commands.
- `CrossDocConsistencyRule` (`crossDocConsistency`) keeps `CLAUDE.md` and
  `AGENTS.md` from contradicting each other: each configured `consistentPatterns`
  regex (one capturing group) must capture the same value in both files, e.g.
  `Java (\d+)` pins the Java version.
- `ReadmeConsistencyRule` (`readmeConsistency`) keeps `README.md` from drifting
  away from the agent docs (`AGENTS.md`): each configured `consistentPatterns`
  regex (one capturing group) must capture the same value in both, e.g.
  `proto(\d)` pins the supported protobuf major version. Unlike
  `crossDocConsistency`, a fact the README simply does not repeat is ignored — the
  README is allowed to document a curated subset — so only a value present in both
  files that disagrees fails the build. Both rules share a `DocumentConsistency`
  helper that owns the pattern validation and capture logic.

The `claudeMdFormat` and `agentsMdFormat` rules share a `MarkdownFormatRule`
base class that performs the file-existence, BOM, title, and section checks, and
offer optional checks switched on from configuration: `forbiddenTokens`,
`enforceSectionOrder`, `maxLineLength`, and `validateFileReferences` (Markdown
links to local files must resolve on disk). Every rule supports a `severity` of
`error` (default, fails the build) or `warn` (logs the same violations), so a new
rule can be adopted gradually.

The check is **opt-in**: the `claude-md-enforce` profile activates only when the
`enforceClaudeMd` property is set (`-DenforceClaudeMd`). This keeps every other
Maven build — the other CI workflows and ordinary local builds — unaffected and
free of any bootstrap requirement. Only `.github/workflows/maven.yml` opts in.

A maven-enforcer rule must be a JAR resolvable from a repository before the
build runs, and Maven resolves plugin dependencies from repositories (not the
reactor), so the rule cannot be produced and consumed in the same build. To run
the check (in CI or locally) use a **two-phase build**:

1. **Install the rule** into the local repo:
   `mvn -pl claude-code-enforcer -am install`. The module's pom is flattened
   (flatten-maven-plugin) so the installed pom has no unresolved `${revision}`
   and is resolvable as a plugin dependency.
2. **Build with the check on**: `mvn package -DenforceClaudeMd` (or
   `mvn -N validate -DenforceClaudeMd` for a quick check of CLAUDE.md alone).

Without `-DenforceClaudeMd`, the rule is neither resolved nor run, so no
bootstrap is needed for normal builds.

## Code style & conventions

These are hard requirements for any code you add or modify:

- **SOLID principles** for all code.
- **Clean code**: short methods, meaningful parameter names.
- **No `continue` or `break`** statements.
- **Write unit tests for all new logic.** Focus on behavior, edge cases, and
  error paths.
- **Match the surrounding code** — naming, comment density, and idiom.

### Maven conventions

- All dependency **versions and scopes** are declared only in the root
  `pom.xml` under `<dependencyManagement>`. Module poms reference dependencies
  without versions.
- All Maven **plugin versions** are declared only in the root `pom.xml` under
  `<pluginManagement>`.
- **Do not add a new dependency without asking first.** Prefer the existing
  Maven dependencies.

## Releasing

To release version `X`:

1. Change the `revision` property in the root `pom.xml` to `X` (it is currently
   a `-SNAPSHOT`, e.g. `2.5.0-SNAPSHOT`).
2. Commit and push.
3. Confirm all builds pass.
4. Release and mark as latest in GitHub.

Creating the GitHub release fires two separate workflows:

- `maven-publish.yml` deploys to **GitHub Packages**
  (`mvn deploy -P github-packages`, using the `distributionManagement`
  repository). The `github-packages` profile attaches the javadoc jar so the
  published coordinates ship javadoc alongside the main jar; the default
  `mvn deploy` would otherwise publish the main jar only.
- `central-publish.yml` deploys to **Maven Central** via the Sonatype Central
  Portal. It runs `mvn -P release deploy` (excluding the `assembly`,
  `grpc-example`, and `protogen-maven-plugin-test` modules, which are not
  reusable libraries — the last is an integration-test harness with no main
  sources, so it would also fail Central validation with an empty
  `-sources.jar`); the `release`
  profile attaches the sources and javadoc jars, GPG-signs every artifact, and
  hands the bundle to the `central-publishing-maven-plugin` (`autoPublish=true`).

Maven Central publishing is **opt-in** through the `release` profile, and the
`central-publishing-maven-plugin` is bound to the `deploy` phase, so ordinary
and CI builds (`mvn install`) never publish and never need GPG keys or Central
credentials. The release job requires four repository secrets:
`MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD` (a Central Portal user
token), plus `MAVEN_GPG_PRIVATE_KEY` and `MAVEN_GPG_PASSPHRASE` (the signing
key). Every reactor module is published to Central except `assembly`,
`grpc-example`, and `protogen-maven-plugin-test`, which are excluded as noted
above.

To publish from a workstation: `mvn -P release deploy` with the same `central`
server credentials in `~/.m2/settings.xml` and a GPG key on the keyring.

### Staged-only dry run (validate without releasing)

`central-publish.yml` also accepts a manual `workflow_dispatch` trigger that runs
the `central` job as a **staged-only dry run**: it signs and uploads the bundle
so the Central Portal validates it, but overrides the plugin with
`-Dcentral.autoPublish=false -Dcentral.waitUntil=validated`, leaving the
deployment staged (not released). Drop or publish it manually in the portal. Use
it to confirm the bundle passes Central's checks before a real release. Central
rejects `-SNAPSHOT` versions, so run it from a commit whose `revision` is a real
version, or supply a non-`SNAPSHOT` `revision` input. Locally, the same staged
check is `mvn -P release deploy -Dcentral.autoPublish=false -Dcentral.waitUntil=validated`.
The `central.autoPublish` (default `true`) and `central.waitUntil` (default
`published`) properties drive this; the defaults keep a real release publishing.

## Containers & Kubernetes

The repository ships two Dockerfiles with different purposes:

- `assembly/Dockerfile` packages the `jar-with-dependencies` built by the
  `assembly` module. CI (`docker.yml`) builds this image on every push and PR
  to `main` but never runs it. **Caveat:** the `data` jar is repackaged with
  `spring-boot:repackage` (nested `BOOT-INF/` layout), so this image cannot
  launch `SampleApp` with `java -jar` — see `k8s/README.md` for the full
  explanation.
- `k8s/Dockerfile` is a dedicated deployment image that expands the fat jar into
  a flat `classes/` + `lib/` classpath and adds a console `log4j2.properties`, so
  `SampleApp`'s result reaches stdout (and thus `kubectl logs`).

The `k8s/` directory runs `SampleApp` (the CSV column-uniqueness checker) on a
local minikube cluster as a run-to-completion **Job** (not a Deployment):
`configmap-sample-data.yaml` mounts a sample CSV, `job-uniqueness-check.yaml`
runs the check, and `run-on-minikube.sh` / `run-on-minikube.ps1` drive the whole
flow (build → image → minikube → load → apply → logs). Pick the column to check
with the `COLUMN` env var (Linux/macOS) or `-Column` parameter (Windows). See
[k8s/README.md](k8s/README.md) for quick-start, manual steps, and the sandbox
limitations that prevent running a minikube control plane in this repo's
automated environment.

## Security

- Report vulnerabilities privately by email to the address in
  [SECURITY.md](SECURITY.md); do not open a public issue for them.
- [SECURITY.md](SECURITY.md) also lists which released versions currently
  receive security fixes (only the latest line is supported).
- `codeql.yml` runs GitHub CodeQL analysis on every push and PR to `main` and on
  a weekly schedule; keep new code free of the issues it flags.

## Pull requests & commits

- Use clear, descriptive, conventional commit messages.
- Keep changes focused; add or update tests alongside the code.
- Do **not** open a pull request unless explicitly asked.
