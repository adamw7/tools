---
name: adopt-pipeline
description: Work on the adopt module — the ordered pipeline that adopts Claude Code into a GitHub repo (toolchain, clone, branch, trust, init, conform, enforcer, verify, push, PR), its step contract, CLI flags and credential masking. Use when adding or changing an adoption step, debugging a run, or when the user says "adopt", "adoption pipeline", "adopt_repo", or "dry run".
---

# Adopt Pipeline Skill

Change the `adopt` module — a pipeline of small, ordered steps that clones a
GitHub repository, wires Claude Code into it, and opens a pull request. The
module's rules are unusually strict because it shells out to `git`, `gh` and
`claude` against real repositories.

## When to Use
- Adding, reordering, or changing an `AdoptionStep`
- Changing what the CLI or the `adopt_repo` MCP tool accepts
- A run fails at a step and you need to know what that step does
- The user says "adopt" / "adoption pipeline" / "adopt_repo" / "dry run"

## The pipeline, in order
`GitHubRepoAdopter.defaultSteps(options)` assembles:

1. `ToolchainStep` — the pipeline's own tools: `git`, `claude`, `gh` (and `gh`
   is logged in). Fails before any expensive work.
2. `CloneStep` — clones into the workspace. **The only step allowed to read the
   credentialled URL.**
3. `BuildToolchainStep` — the *cloned project's* build tool, checked as soon as
   the clone reveals which one it is.
4. `BranchStep` — feature branch (`claude/adopt-claude-code` by default). The
   default branch is never written to.
5. `TrustStep` — marks the checkout trusted for Claude Code.
6. `ClaudeInitStep` → `ClaudeMdConformanceStep` → `CommitStep` — generate
   `CLAUDE.md`, conform it (plus a companion `AGENTS.md`) so it satisfies the
   guard about to be wired in, commit.
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
the report (e.g. `PullRequestStep` and the PR URL).

### Adding a step — the checklist ArchUnit enforces
- Class name **ends with `Step`** and lives in `…adopt.step`.
- **Never spawn a process yourself.** `ProcessBuilder`/`Runtime` are confined to
  the `command` package; a step shells out only through `CommandRunner`.
  `AbstractCommandStep` is the usual base.
- Fields are **immutable** (step state is final).
- A step must not depend on `GitHubRepoAdopter` — the pipeline knows its steps,
  not the other way round.
- Only `CloneStep` may touch `AdoptionContext#repositoryUrl()`; everything else
  logs `displayUrl()`. `Redaction` masks credentials in every log, message and
  report — keep it that way.
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
`--rule-version`, `--timeout <minutes>`, `--report <file>`, `--help`. They build
an `AdoptionOptions` (wrapping `PullRequestOptions`) that both entry points —
`Main` and the MCP `AdoptTool` — hand to the pipeline factory.

## Testing
- Step tests use a fake `CommandRunner`: **a test must never spawn a real
  process** (pinned by `TestConventionsArchitectureTest`).
- `MultiRepoAdoptionIT` clones real sample repositories to prove each gets its
  own checkout, and **stops at the branch step** — the `*IT`s never push and
  never open a pull request. Keep that invariant when extending them.
- Run them with `mvn -P integration-tests verify` (the profile is declared in
  `adopt`'s own pom).

## References
- `CLAUDE.md` / `AGENTS.md` — *Claude Code adoption* (source of truth)
- `adopt/.../GitHubRepoAdopter.java` — the assembled pipeline
- `adopt/.../architecture/AdoptArchitectureTest.java` — the rules above, as tests
- `adopt/.../mcp/MCP_USAGE.md` — the `adopt_repo` tool
