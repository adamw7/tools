# CLAUDE.md

See [AGENTS.md](AGENTS.md) for the full agent guide (module layout, build/test
commands, environment, and release process). The essentials are repeated below.

## Project

Java project built with Maven 3.9.X. Run `mvn install` from the root to build.

## Java version
Java 25.

## Maven
All versions and scopes are defined only in root pom.xml in dependency management.
All maven plugin versions are defined only in root pom.xml in plugin management.

## Principles for Java Development
Use SOLID Principles for all code.
Use clean code. No continue or break instructions. Short methods. Meaningful parameter names.

## Testing
Write unit tests for all new logic. Focus on behavior, edge cases, and error paths.

## Dependencies
Use the existing maven dependencies. Always ask before adding a new one.
