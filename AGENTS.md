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
  **compile time** instead of runtime (shift-left). Supports proto2 only,
  because proto3 has no concept of required fields.
- **Context engineering** (`code/context`) — a fast, regex-based finder that
  builds the tree of classes used by a given class, plus a `ProjectTreeBuilder`
  that scans a whole Java project into a tree of folders, files and
  dependencies, to assemble context for gen-AI agents working with Java code. An
  **MCP server** (in the `io.github.adamw7.context.mcp` package) exposes the
  project-tree and context-finder tools over stdio, streamable HTTP or HTTP+SSE.
- **Data** (`data`) — data sources (CSV, GZip, JDBC; in-memory and iterative
  loading), a uniqueness-checking tool (finds whether a subset of columns can
  serve as a key, and searches for a smaller key), data structures (an
  open-addressing `Map` implementation), and an **MCP server** exposing the
  uniqueness checker as a tool for AI assistants.

See [README.md](README.md) for worked code examples of each capability.

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
(`--transport.mode=streamable-http`, served at `/mcp`), or the legacy HTTP+SSE
transport (`--transport.mode=sse`, event stream at `/sse`, messages at
`/mcp/message`):

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

CI (`.github/workflows/maven.yml`) installs the enforcer rule
(`mvn -B -pl claude-code-enforcer -am install`) and then runs
`mvn -B package -DenforceClaudeMd` on JDK 25 (Temurin) for every push and for
pull requests targeting `main`. It is the only workflow that runs the CLAUDE.md
check; the other workflows build normally and are unaffected.

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
   a `-SNAPSHOT`, e.g. `2.2.0-SNAPSHOT`).
2. Commit and push.
3. Confirm all builds pass.
4. Release and mark as latest in GitHub.

Creating the GitHub release fires `maven-publish.yml`, which runs two jobs:

- `github-packages` deploys to **GitHub Packages** (the default `mvn deploy`,
  using the `distributionManagement` repository).
- `central` deploys to **Maven Central** via the Sonatype Central Portal. It
  runs `mvn -P release deploy`; the `release` profile attaches the sources and
  javadoc jars, GPG-signs every artifact, and hands the bundle to the
  `central-publishing-maven-plugin` (`autoPublish=true`).

Maven Central publishing is **opt-in** through the `release` profile, and the
`central-publishing-maven-plugin` is bound to the `deploy` phase, so ordinary
and CI builds (`mvn install`) never publish and never need GPG keys or Central
credentials. The release job requires four repository secrets:
`MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD` (a Central Portal user
token), plus `MAVEN_GPG_PRIVATE_KEY` and `MAVEN_GPG_PASSPHRASE` (the signing
key). The reusable library modules are published to Central; the `assembly`
module is an executable `jar-with-dependencies` bundle, which Central rejects,
so it sets the central-publishing plugin's `skipPublishing` property and is left
out of the Central bundle while still deploying to GitHub Packages.

To publish from a workstation: `mvn -P release deploy` with the same `central`
server credentials in `~/.m2/settings.xml` and a GPG key on the keyring.

### Staged-only dry run (validate without releasing)

`maven-publish.yml` also accepts a manual `workflow_dispatch` trigger that runs
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

## Pull requests & commits

- Use clear, descriptive, conventional commit messages.
- Keep changes focused; add or update tests alongside the code.
- Do **not** open a pull request unless explicitly asked.
