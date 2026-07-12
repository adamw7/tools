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

Module map (root reactor: `claude-code-enforcer`, `mcp-common`, `data`, `code`,
`grpc-example`, `assembly`; `data-test` is built separately):

```
tools (root pom, packaging=pom)
├── claude-code-enforcer   # custom maven-enforcer rules validating CLAUDE.md/AGENTS.md & agent config
├── mcp-common             # shared MCP server scaffolding
├── data                   # data sources, uniqueness checks, structures, MCP server
├── code
│   ├── protogen-maven-plugin       # compile-time-safe protobuf builder generator
│   ├── protogen-maven-plugin-test  # integration tests for the plugin
│   └── context                     # class-usage context finder + MCP server
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

## Principles for Java Development

- **SOLID principles** for all code.
- **Clean code**: short methods, meaningful parameter names.
- **No `continue` or `break`** statements.
- **Match the surrounding code** — naming, comment density, and idiom.

## Testing

Write unit tests for all new logic. Focus on behavior, edge cases, and error
paths.

- **Unit tests** run in the normal `test`/`package` lifecycle. Surefire enforces
  a **900-millisecond per-test timeout** (configured on the surefire plugin in the
  root `pom.xml`) — above the one-time cold-JVM warmup but low enough to catch a
  test doing real work — so keep unit tests fast; a genuinely heavier test opts
  out with an explicit `@Timeout` and a comment explaining why.
- **Architecture tests** (ArchUnit) live in each module's `.architecture` test
  package and enforce package layering and coding rules — data-source contracts
  must not depend on their implementations, the uniqueness core must not depend
  on its MCP adapter, loggers are `private static final`, production code logs
  through log4j2 (no `System.out`/`err`, `printStackTrace`, or `System.exit`),
  and packages stay free of cycles. Keep new code within these rules.
- **MCP integration tests** (`*IT`) are gated behind the `integration-tests`
  profile.

## Dependencies

Use the existing Maven dependencies. **Always ask before adding a new one.**
