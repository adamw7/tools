---
name: testing-conventions
description: Write tests that pass this repo's enforced testing rules (Surefire 5s timeout, network-off unit tests, ArchUnit conventions, JUnit 5 only). Use when adding or changing tests, when a test times out or opens a network connection, or when the user says "write a test", "add tests", or "fix the failing test".
---

# Testing Conventions Skill

Write unit and integration tests that satisfy the `tools` repo's enforced
testing rules the first time. These rules are enforced by Surefire config,
JUnit extensions, and ArchUnit architecture tests — a test that ignores them
fails the build, not just review.

## When to Use
- Adding a unit test for new logic (all new logic needs one)
- A test fails with a timeout, or a unit test tries to open a socket
- The user says "write a test" / "add tests" / "fix the failing test"
- Reviewing tests before a commit or PR

## Hard rules (build fails otherwise)

### Timeouts
- **5 s per unit test.** Surefire enforces a 5-second per-test timeout (root
  `pom.xml`). The bound is generous because the reactor builds in parallel
  (`-T1C`) and contending test forks stretch the cold-fork warmup; it is *not* a
  budget to spend. Keep unit tests fast — no real I/O, no sleeps, no heavy loops.
  A genuinely heavier test opts out with an explicit `@Timeout` **and a comment
  explaining why**.
- Heavy shared setup (`@BeforeAll` etc.) has a looser 10-second limit (15 s
  under coverage). A fork that hangs outright is killed at 300 s
  (`forkedProcessTimeoutInSeconds`).

### Network is off for unit tests
- The `data` module's `NetworkOffExtension` engages the `Switch` kill-switch
  before any test runs, so a unit test **cannot** open an outbound connection.
- Anything needing the network is an integration test (`*IT`), gated behind the
  `integration-tests` profile and run by Failsafe — not Surefire.

### Test conventions pinned by `TestConventionsArchitectureTest`
- Test methods live only in `*Test` / `*IT` classes.
- **JUnit 5 only** (`org.junit.jupiter`). No JUnit 4.
- No `@Disabled`.
- No `System.out` / `System.err` in tests.
- No `Thread.sleep` in tests.

### Coding rules that also apply to tests
- **No `continue` or `break`** statements.
- Loggers, when present, are `private static final`.
- Date/time uses `java.time`, never `Date` / `Calendar`.

## Naming
- Unit test class: `SomethingTest` (runs in `test` / `package` lifecycle).
- Integration test class: `SomethingIT` (runs under `-P integration-tests verify`).
- Architecture tests live in each module's `.architecture` test package.

## Workflow
1. Decide unit vs. integration: does it touch the network or a live resource?
   If yes → `*IT`. If no → `*Test`.
2. Put the class in the right package (`.architecture` for ArchUnit rules).
3. Write focused, fast assertions — behavior, edge cases, and error paths.
4. Run it: `mvn -pl <module> test` (unit) or
   `mvn -P integration-tests verify` (integration).
5. If a unit test legitimately needs more than 5 s, add `@Timeout(...)`
   with a one-line comment justifying it.

## Quick reference

| Concern | Rule |
|---|---|
| Per-unit-test time | 5 s (opt out with commented `@Timeout`) |
| `@BeforeAll` etc. | 10 s (15 s under coverage) |
| Network in unit test | Blocked by `NetworkOffExtension` — use `*IT` |
| Test framework | JUnit 5 only |
| Disabled tests | Not allowed (`@Disabled` banned) |
| `Thread.sleep` in test | Banned |
| `System.out`/`err` in test | Banned |
| `continue` / `break` | Banned (all code) |

## References
- `CLAUDE.md` / `AGENTS.md` — *Testing* section (source of truth)
- `data` module `NetworkOffExtension`, `Switch`
- `TestConventionsArchitectureTest`
