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
  builds the tree of classes used by a given class, to assemble context for
  gen-AI agents working with Java code.
- **Data** (`data`) — data sources (CSV, GZip, JDBC; in-memory and iterative
  loading), a uniqueness-checking tool (finds whether a subset of columns can
  serve as a key, and searches for a smaller key), data structures (an
  open-addressing `Map` implementation), and an **MCP server** exposing the
  uniqueness checker as a tool for AI assistants.

See [README.md](README.md) for worked code examples of each capability.

## Module layout

```
tools (root pom, packaging=pom)
├── data                        # data sources, uniqueness checks, structures, MCP server
├── code
│   ├── protogen-maven-plugin       # the proto2 builder-generating Maven plugin
│   ├── protogen-maven-plugin-test  # integration tests / use cases for the plugin
│   └── context                     # regex-based class-usage context finder
├── assembly                    # builds an executable jar-with-dependencies
│                               #   (mainClass: io.github.adamw7.tools.data.SampleApp)
└── data-test                   # standalone test module for the data module
```

Root reactor modules are `data`, `code`, and `assembly`. The `data-test` module
is built separately (it is not in the root `<modules>` list).

Base Java package: `io.github.adamw7` (`io.github.adamw7.context` for the
context module, `io.github.adamw7.tools.*` elsewhere).

The MCP server lives in
`data/src/main/java/io/github/adamw7/tools/data/uniqueness/mcp/`. Its entry
point is `Main.java`; see
[MCP_USAGE.md](data/src/main/java/io/github/adamw7/tools/data/uniqueness/mcp/MCP_USAGE.md)
for client configuration (Claude Desktop, Cline, etc.).

## Environment & toolchain

- **Java 25** (`maven.compiler.source`/`target` = 25). A JDK 25 must be on the
  `PATH` with `JAVA_HOME` set. In Claude Code web/remote sessions the
  `.claude/hooks/session-start.sh` hook installs `openjdk-25-jdk`, exports
  `JAVA_HOME`, and pre-fetches dependencies (`mvn dependency:go-offline`).
- **Maven 3.9.x.** Build from the repository root.

## Build, test, and run

```bash
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

CI (`.github/workflows/maven.yml`) runs `mvn -B package` on JDK 25 (Temurin)
for every push and for pull requests targeting `main`.

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
   a `-SNAPSHOT`, e.g. `1.6.0-SNAPSHOT`).
2. Commit and push.
3. Confirm all builds pass.
4. Release and mark as latest in GitHub.

## Pull requests & commits

- Use clear, descriptive, conventional commit messages.
- Keep changes focused; add or update tests alongside the code.
- Do **not** open a pull request unless explicitly asked.
