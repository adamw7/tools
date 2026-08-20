# 12. Path confinement scoped to the data source, not the JVM

- **Status:** Accepted
- **Date:** 2026-08-20
- **Deciders:** Project maintainers
- **Tags:** data, security, api
- **Supersedes:** —
- **Superseded by:** —

## Context

Every file-backed data source in the `data` module canonicalises the path it is
given and refuses one that climbs out of its directory with `..` (CWE-22). It
could additionally be held inside a base directory, and the uniqueness MCP
server relies on that: a client names the file to check, so without a boundary
it could name `/etc/passwd`.

That boundary was one `static volatile Path` on `PathValidator`, written by
`setAllowedBaseDir` and wiped by `clearAllowedBaseDir`. One field for the whole
JVM has three consequences:

- **One boundary per process.** A host embedding the library cannot confine two
  sources to two roots — the second `setAllowedBaseDir` moves the first one's
  boundary without saying so.
- **Order-dependent security.** Any code in the process can call
  `clearAllowedBaseDir()` and lift the restriction for everything else.
- **It reached into the test suite.** It was one of the two reasons `@Isolated`
  exists here: a test pointing the boundary at a `@TempDir` denied every
  concurrently running test the resource it read, so the classes touching it
  could not join the class-parallel run.

The `code/context` module had already solved the same problem the other way,
with per-server `context.allowed-roots` configuration rather than a static.

## Decision

Make the boundary a value, held by the thing it confines.

`AllowedPaths` is immutable, built either as `AllowedPaths.under(baseDir)` or
`AllowedPaths.anywhere()`, and carries the path check itself. Every file source
gains a constructor taking one, and passes it nothing else; the uniqueness MCP
server builds one from `data.allowed-base-dir` and hands it to the tool it
registers, so the confinement travels with the tool instead of with the process.

`PathValidator`'s three static entry points remain for one release as
`@Deprecated(forRemoval = true)` delegates over a shared `AllowedPaths`, and the
source constructors that take no boundary keep validating against it. A host
that has not migrated therefore stays exactly as confined as it was — the
alternative, defaulting those constructors to unconfined, would have quietly
removed a restriction somebody was relying on.

## Consequences

- Two sources can be confined to two directories in one JVM, and no unrelated
  code can widen or clear a boundary it did not set.
- `AllowedPathsTest` and `McpConfigurationTest` run in the class-parallel unit
  suite. Only the class covering the deprecated statics still carries
  `@Isolated`, and the ArchUnit rule requiring it retires with them.
- The public API grows one constructor per file source, and `UniquenessTool`'s
  constructor now takes its boundary — a breaking change for anyone who
  constructed that tool directly, which only the server wiring does.
- Symlink resolution, the traversal check and the messages are unchanged, so
  what was refused before is still refused — with one deliberate exception: a
  path element is now compared to `..` rather than searched for as a substring,
  so a legitimate file named `..name` is no longer mistaken for an escape.
