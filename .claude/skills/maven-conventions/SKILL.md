---
name: maven-conventions
description: Follow this repo's Maven rules — versions only in the root pom, module poms version-free, the build profiles, and clean-after-codegen. Use when editing a pom.xml, adding a dependency or plugin, choosing a build command, or when the user says "add a dependency", "bump a version", or "the build fails".
---

# Maven Conventions Skill

Keep `pom.xml` edits and build commands consistent with how the `tools`
multi-module reactor is wired. Getting versions in the wrong place, or missing a
profile, is the most common source of avoidable build friction here.

## When to Use
- Editing any `pom.xml`
- Adding or upgrading a dependency or a Maven plugin
- Choosing which `mvn` command / profile to run
- The user says "add a dependency" / "bump a version" / "the build fails"

## Hard rules

### Versions live in exactly one place
- **Dependency versions and scopes**: only in the **root** `pom.xml` under
  `<dependencyManagement>`.
- **Plugin versions**: only in the **root** `pom.xml` under
  `<pluginManagement>`.
- **Module poms reference dependencies and plugins WITHOUT versions.** Never add
  a `<version>` to a module pom — add or change it in the root instead.

### Ask before adding a dependency
- Use the existing Maven dependencies. **Always ask the user before adding a new
  one.** If approved, declare it (with version) in root `<dependencyManagement>`,
  then reference it version-free in the module.

### Clean after removing a code-generation source
- Run `clean` after deleting a codegen input, so stale generated builders in
  `target/` cannot mask the change.

## Build commands (run from the repo root)

| Command | Purpose |
|---|---|
| `mvn clean install` | Full clean build + install to local repo |
| `mvn install` | Faster incremental build |
| `mvn -pl <module> test` | Tests for a single module |
| `mvn -P integration-tests verify` | MCP integration tests (`*IT`) |
| `mvn -Pcoverage verify` | JaCoCo coverage (fails under 80%) |
| `mvn -Ppitest test` | PIT mutation testing |
| `mvn install -Dskip.shellcheck=true` | Skip the shellcheck lint |

## Notes that save time
- **Shellcheck runs embedded.** `dev.dimlight:shellcheck-maven-plugin` uses
  `<binaryResolutionMethod>embedded</binaryResolutionMethod>` — the binary ships
  in the plugin jar (from Maven Central), so the build needs no installed
  `shellcheck` and works offline. Skip it with `-Dskip.shellcheck=true` when you
  just want a fast loop.
- **Java 25** is required: JDK 25 on `PATH` with `JAVA_HOME` set.
- The root reactor modules are: `claude-code-enforcer`, `mcp-common`, `data`,
  `code`, `adopt`, `grpc-example`, `assembly`. `data-test` is built separately
  (not in the root `<modules>`).

## Workflow for a dependency/plugin change
1. Confirm it isn't already managed in the root pom.
2. If it's a *new* dependency, **ask the user first**.
3. Add/adjust the version in root `<dependencyManagement>` or
   `<pluginManagement>`.
4. Reference it version-free in the module pom.
5. Build: `mvn install` (add `clean` if you removed a codegen source).

## References
- `CLAUDE.md` / `AGENTS.md` — *Maven* section (source of truth)
- Root `pom.xml` — `<dependencyManagement>`, `<pluginManagement>`
