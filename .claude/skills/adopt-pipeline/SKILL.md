---
name: adopt-pipeline
description: Work on the adopt module — the ordered pipeline that adopts Claude Code into a GitHub repo (toolchain, clone, branch, trust, init, conform, enforcer, verify, push, PR), its step contract, CLI flags and credential masking. Use when adding or changing an adoption step, debugging a run, or when the user says "adopt", "adoption pipeline", "adopt_repo", or "dry run".
---

# Adopt Pipeline Skill

Change the `adopt` module — a pipeline of small, ordered steps that clones a
GitHub repository, wires Claude Code into it, and opens a pull request. The
module's rules are unusually strict because it shells out to `git`, `gh` and
`claude` against real repositories.

## The pipeline, in order
`GitHubRepoAdopter.defaultSteps(options)` assembles:

1. `ToolchainStep` — the pipeline's own tools: `git`, `claude`, `gh` (and `gh`
   is logged in). Fails before any expensive work. A dry run is checked with
   `ToolchainStep.forDryRun()` — `git` and `claude` only, since `gh` is used by
   the pull request the run leaves out.
2. `CloneStep` — clones into the workspace, then rewrites the checkout's `origin`
   to `checkoutUrl()`, the same URL without its credentials, so the token git
   wrote into `.git/config` does not outlive the run.
3. `BuildToolchainStep` — the *cloned project's* build tool, checked as soon as
   the clone reveals which one it is.
4. `BranchStep` — feature branch (`claude/adopt-claude-code` by default). The
   default branch is never written to.
5. `TrustStep` — marks the checkout trusted for Claude Code.
6. `ClaudeInitStep` → `ClaudeMdConformanceStep` → `CommitStep` — generate
   `CLAUDE.md`, conform it (plus a companion `AGENTS.md`) as far as the guard
   about to be wired in demands — `BuildSystem.requiredClaudeMdSections()`, so
   only the Maven path adds the format rule's Java/Maven headings — commit.
7. `EnforcerStep` → `CommitStep` — wire the build-tool-aware guard in and commit.
8. *(optional)* `AssetsStep` → `CommitStep` — starter config assets, on `--assets`.
9. `VerifyStep` — proves the guard passes on the generated file.
10. `PushStep` → `PullRequestStep` — **omitted entirely on a dry run**, so the
    report says what a dry run really did rather than listing steps that
    pretended to run.

The three build-system-aware steps (`BuildToolchainStep`, `EnforcerStep`,
`VerifyStep`) share one `BuildSystems.defaults(...)` list, so the guard that is
wired in is the guard that is verified with the tool that was probed
(`MavenBuildSystem`, `GradleBuildSystem`, `FallbackBuildSystem`).

## The step contract
```java
public interface AdoptionStep {
    String name();
    void execute(AdoptionContext context, CommandRunner runner);
    default void execute(AdoptionContext context, CommandRunner runner, AdoptionReport report) {
        execute(context, runner);   // most steps have nothing to report
    }
}
```
Implement the three-argument variant only when the step contributes a fact to
the report: `CloneStep` records the checkout directory, `PullRequestStep` the
pull request's URL.

### Adding a step — the checklist ArchUnit enforces
- Class name **ends with `Step`** and lives in `…adopt.step`.
- **Never spawn a process yourself.** `ProcessBuilder`/`Runtime` are confined to
  the `command` package; a step shells out only through `CommandRunner`.
  `AbstractCommandStep` is the usual base.
- Fields are **immutable** (step state is final).
- A step must not depend on `GitHubRepoAdopter` — the pipeline knows its steps,
  not the other way round.
- Only `CloneStep` and `PushStep` may touch `AdoptionContext#repositoryUrl()` —
  the two commands that authenticate to the remote. Everything else logs
  `displayUrl()` or, where a working URL is needed, `checkoutUrl()`. `PushStep`
  passes the credentials as a `-c remote.origin.pushurl` override, which git
  applies to that one invocation and writes nowhere. `Redaction` masks
  credentials in every log, message and report — keep it that way.
- Register the step in `GitHubRepoAdopter.defaultSteps` (or the optional list it
  belongs to), and unit-test it with a fake `CommandRunner`.

## Batch runs and failure isolation
One run adopts a list of repositories: repeatable `--repo <url>`, `--repos
<file>`, or `repository_urls` on the MCP tool. Each gets its own checkout
(claimed inside its own adoption via `Checkouts`) and its own `AdoptionReport`,
and **a repository that fails does not stop the rest** — a malformed URL
included. Preserve that when changing `BatchAdoption` or `Failures`.

## CLI flags
`--repo`, `--repos`, `--workspace`, `--branch`, `--title`, `--body`,
`--reviewer`, `--label`, `--assignee`, `--draft`, `--assets`, `--dry-run`,
`--rule-version`, `--timeout <minutes>`, `--retries <count>`, `--report <file>`,
`--help`. They build an `AdoptionOptions` (wrapping `PullRequestOptions`) that
both entry points — `Main` and the MCP `AdoptTool` — hand to the pipeline factory.

## Retrying what the network refused
Both entry points assemble their toolchain with `CommandRunners.forRun(options)`,
which wraps `ProcessCommandRunner` in a `RetryingCommandRunner`, so a
`git` or `gh` the network refused is run again — twice by default, after 2s and
4s (doubling, capped at 30s), and `--retries 0` turns it off. An unattended batch
otherwise lost a whole repository, `claude init` included, to one connection
reset.

`TransientFailures` is the single place that decides, and it is deliberately
narrow on two axes: the program must be `git` or `gh` — re-runnable, and unlike
`claude` or a build tool not liable to *discuss* a connection reset in a
transcript — and the wording must be a transport-level refusal. Everything else
still fails on the first attempt: a 403 or 404, a rejected non-fast-forward, a
rate limit that wants minutes rather than seconds, and the git queries whose
answer *is* a non-zero exit (`rev-parse --verify`, `diff --cached --quiet`).
Nothing that throws is retried — an unstartable program and a timeout both buy
only another wait. Add a wording to that list rather than teaching a step to
retry for itself.

## Debugging a run: where the logging goes
Two channels, from `adopt/src/main/resources/log4j2.properties`. The **console**
(standard error, threshold-filtered to `info`) is progress: `Repository 2 of 5`,
`Step 4/12: branch`, what each step cost, and the closing count of repositories
that landed. **`logs/adopt.log`** adds the adoption's own `debug` —
`io.github.adamw7.tools.adopt` is set to it while the root stays at `info`, so the
embedded Spring Boot MCP server does not bury the trace — carrying every
`git`/`claude`/`gh` invocation with its working directory, exit code and duration,
plus the redacted transcript of the ones that failed.

Reach for that file first when a run "did nothing": `runTolerating` swallows a
non-zero exit by design, so the command trace is the only place a tolerated
refusal is recorded. Durations come from `Elapsed`; log through it rather than
formatting a `Duration` yourself. Anything reporting a command or its output goes
through `CommandResult.describe()` / `redactedOutput()`, never the raw values — a
clone URL's credentials come back in the tool's own error text.

## Testing
- Step tests use a fake `CommandRunner`: **a test must never spawn a real
  process** (pinned by `TestConventionsArchitectureTest`).
- `MultiRepoAdoptionIT` clones real sample repositories to prove each gets its
  own checkout, and **stops at the branch step** — the `*IT`s never push and
  never open a pull request. Keep that invariant when extending them.
- `ForeignRepositoryAdoptionIT` adopts five real repositories nobody prepared —
  `google/gson` (multi-module Maven), `square/okhttp` (Gradle, Kotlin DSL),
  `anthropics/anthropic-quickstarts` (a real `CLAUDE.md`),
  `anthropics/claude-code` (a real `.claude`) and `github/gitignore` (a very
  large flat tree). A step that reads or writes the checkout has to survive them:
  the guard is only ever an *addition* to a build file somebody else wrote, the
  commit carries only `AdoptionAssets.WRITTEN_PATHS`, the working tree is left
  clean, the default branch and the remote are untouched, and a second adoption
  changes nothing. Its Maven guard is pinned to a released `--rule-version`,
  because the installer refuses to wire a `-SNAPSHOT` into another project's POM.
- Run them with `mvn -P integration-tests verify` (the profile is declared in
  `adopt`'s own pom).

## References
- `CLAUDE.md` / `AGENTS.md` — *Claude Code adoption* (source of truth)
- `adopt/.../GitHubRepoAdopter.java` — the assembled pipeline
- `adopt/.../architecture/AdoptArchitectureTest.java` — the rules above, as tests
- `adopt/.../ForeignRepositoryAdoptionIT.java` — the pipeline against five real
  repositories that never adopted it
- `adopt/.../mcp/MCP_USAGE.md` — the `adopt_repo` tool
