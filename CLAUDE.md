# CLAUDE.md

See [AGENTS.md](AGENTS.md) for the canonical agent guide. This file summarises
the most important facts and is kept up to date with the current state of the
codebase.

---

## Project overview

`tools` is a multi-module Java library (GroupId `io.github.adamw7`,
version **1.6.0-SNAPSHOT**) with three main capabilities:

- **Code generation** (`code/protogen-maven-plugin`) — Maven plugin that
  generates protobuf builders detecting missing required fields at **compile
  time** (proto2 only).
- **Context engineering** (`code/context`) — fast regex-based class-dependency
  finder for assembling gen-AI context from Java source trees.
- **Data** (`data`) — data sources (CSV, JSON, YAML, TOON, GZIP, JDBC),
  uniqueness checking, an open-addressing `Map`, and an **MCP server** that
  exposes the uniqueness checker to AI assistants via stdio JSON-RPC.

---

## Module layout

```
tools (root pom, packaging=pom)
├── data                          # sources, uniqueness, structures, MCP server
├── code
│   ├── protogen-maven-plugin         # proto2 builder-generating Maven plugin
│   ├── protogen-maven-plugin-test    # integration tests / use cases for the plugin
│   └── context                       # regex-based class-usage context finder
├── grpc-example                  # end-to-end gRPC + protogen demo (test sources)
├── assembly                      # executable jar-with-dependencies
│                                 #   mainClass: io.github.adamw7.tools.data.SampleApp
└── data-test                     # standalone data-module tests (NOT in root <modules>)
```

Root reactor modules: `data`, `code`, `grpc-example`, `assembly`.
`data-test` is built separately.

Base packages: `io.github.adamw7.context` (context module),
`io.github.adamw7.tools.*` (everywhere else).

---

## Environment & toolchain

- **Java 25** — `maven.compiler.source/target = 25`. JDK 25 must be on `PATH`
  with `JAVA_HOME` set. The `.claude/hooks/session-start.sh` hook installs
  `openjdk-25-jdk`, sets `JAVA_HOME`, and runs `mvn dependency:go-offline`
  automatically in remote/web sessions.
- **Maven 3.9.x** — build from the repository root.
- **Spring Boot 3.5.14** parent — used for dependency BOM and Spring MCP server.

---

## Build, test, and run

```bash
# Full clean build + install to local repo
mvn clean install

# Faster incremental build (when no generated sources were deleted)
mvn install

# What CI runs
mvn -B package

# Single-module tests
mvn -pl data test
mvn -pl code/context test
mvn -pl grpc-example test

# Standalone data-test module (not in root reactor)
mvn -pl data-test -am test

# Integration tests (HTTP transport for MCP server)
mvn -B verify -P integration-tests

# Coverage report (JaCoCo, 80% INSTRUCTION threshold)
mvn -B verify -Pcoverage
```

**Always `mvn clean install` after removing any code-generation source.**
Stale generated output in `target/` can otherwise hide regressions.

---

## CI/CD workflows (`.github/workflows/`)

| Workflow | Trigger | Command |
|---|---|---|
| `maven.yml` | Push & PRs to `main` | `mvn -B package` (2-min limit) |
| `integration-tests.yml` | Daily 00:00 UTC | `mvn -B verify -P integration-tests` |
| `coverage.yml` | Weekly Sat 00:00 UTC | `mvn -B verify -Pcoverage` |
| `maven-publish.yml` | On GitHub release | Publish to GitHub Packages |
| `docker.yml` | Push & PRs | Docker build |
| `codeql.yml` | Push & PRs | Security scanning |

---

## Key dependencies

All versions are declared **only** in root `pom.xml` `<dependencyManagement>`.
Module poms never specify versions.

| Artifact | Version |
|---|---|
| Spring Boot parent | 3.5.14 |
| protobuf-java | 4.35.0 |
| grpc-* | 1.75.0 |
| Spring AI MCP SDK | 2.0.0-RC1 |
| jackson (BOM) | 2.21.3 |
| Log4j2 | 2.26.0 |
| Mockito | 5.23.0 |
| Apache Derby (test) | 10.17.1.0 |

---

## Maven conventions

- All dependency **versions and scopes** → root `pom.xml` `<dependencyManagement>`.
- All plugin **versions** → root `pom.xml` `<pluginManagement>`.
- **Never add a new dependency without asking first.**

---

## Code style & conventions

Hard requirements for all new or modified code:

- **SOLID principles** — every class.
- **Clean code**: short methods, meaningful parameter names.
- **No `continue` or `break`** statements.
- **Unit tests for all new logic** — behaviour, edge cases, error paths.
- **Match surrounding code** — naming, comment density, idiom.
- Comments only when the *why* is non-obvious; never narrate what the code does.

### Java module system

`data` is an **open module** (allows Spring reflection). `data-test` is a
regular module. All public-API packages are explicitly exported in
`module-info.java`; keep that file consistent when adding packages.

---

## Module highlights

### `data`

- Data sources: `IterableDataSource` (streaming), `InMemoryDataSource`
  (fully loaded). Both transparently decompress `.gz` files.
- Supported formats: CSV, JSON, YAML, TOON (compact LLM-friendly tabular),
  GZIP, JDBC (`InMemorySQLDataSource`, `IterableSQLDataSource`).
- Uniqueness checking: `InMemoryUniquenessCheck` / `NoMemoryUniquenessCheck`.
- MCP server entry point:
  `io.github.adamw7.tools.data.uniqueness.mcp.Main` (Spring Boot, stdio
  transport). Client configuration in
  [`data/src/main/java/io/github/adamw7/tools/data/uniqueness/mcp/MCP_USAGE.md`](data/src/main/java/io/github/adamw7/tools/data/uniqueness/mcp/MCP_USAGE.md).

### `code/protogen-maven-plugin`

- Goal: `code-generator`, phase: `generate-sources`.
- Generates builder interfaces + implementations into
  `target/generated-sources/` (or `target/generated-test-sources/`).
- Uses Eclipse JDT for Java formatting and unused-import removal.

### `code/context`

- `Context` interface + `Finder` implementation.
- `find(ClassContainer root, int depth)` returns the set of classes used
  transitively up to `depth` levels deep.

### `grpc-example`

- Demonstrates combining standard `protoc` generation with protogen builders.
- All code lives in **test sources** because builders are generated to
  `target/generated-test-sources/`.

### `data-test`

- Runs with `-Xmx16m` to exercise low-memory code paths.
- Build: `mvn -pl data-test -am test`.

---

## Custom Claude Code skills (`.claude/skills/`)

Three project-local skills are available via the Skill tool:

| Skill | Purpose |
|---|---|
| `git-commit` | Generates conventional commit messages for Java |
| `java-code-review` | Systematic review: null-safety, exceptions, concurrency, performance |
| `solid-principles` | SOLID checklist with Java examples |

---

## Releasing

1. Change the `revision` property in root `pom.xml` to the release version
   (e.g. `1.6.0`, currently `1.6.0-SNAPSHOT`).
2. Commit and push.
3. Confirm all builds pass.
4. Create a GitHub release and mark it as latest.

---

## Pull requests & commits

- Conventional commit messages; keep changes focused.
- Add or update tests alongside every code change.
- Do **not** open a pull request unless explicitly asked.
