# A recorded adoption run

This is a **recording of the [`adopt`](../adopt) pipeline actually running**, and
the script that reproduces it. It is the fastest way to see what adopting Claude
Code into a repository does, step by step, without adopting anything: the run
below is a `--dry-run`, so it clones, branches, generates a `CLAUDE.md`, wires the
build guard in and verifies it — and pushes nothing, opening no pull request.

For what each step *is*, see [Claude Code adoption](../README.md#claude-code-adoption)
in the README, and the `adopt-pipeline` skill for the step contract.

## Running the demo

```bash
./scripts/linux/adopt-demo.sh                      # the default repository, into target/adopt-demo
./scripts/linux/adopt-demo.sh <repo-url> <out-dir> # any repository, anywhere
```

```powershell
.\scripts\windows\adopt-demo.ps1
.\scripts\windows\adopt-demo.ps1 -RepoUrl "https://github.com/octocat/Spoon-Knife.git" -OutputDir "C:\Temp\demo"
```

The script builds `tools.adopt`, empties the output directory so the recording
holds one run alone, and records the adoption to `adopt-demo.txt` — with
`script(1)` on Linux and `Start-Transcript` on Windows, both of which keep the
pipeline's two streams in the order a terminal shows them. Where `asciinema` is
installed, the Linux script wraps the same single run in a cast as well, replayable
with `asciinema play`. The adoption's own exit code becomes the demo's, but only
after the report has been printed: a run that stopped part-way still wrote one, and
that is the thing to read.

**What it needs.** `git`, `claude` and `mvn`. Not `gh`, and no GitHub credentials:
a dry run is assembled without the push and pull-request steps, and `gh` is what
opens the pull request, so `ToolchainStep.forDryRun()` does not ask for it.

**Why that default repository.** It is small, public, and carries enough real code
for `claude init` to have something to document. Pointed at an *empty* repository
the CLI declines to write a `CLAUDE.md` at all rather than invent one, and the run
spends every `claude-init` attempt discovering that. It is also deliberately not a
Maven project: the Maven guard wires in a *released* `claude-code-enforcer`, which
a `-SNAPSHOT` build of `tools` has none of, so a Maven repository cannot be
demonstrated from an unreleased checkout. A repository with no recognised build
file takes the GitHub Actions fallback instead, whose guard is a portable script —
which is also why `verify` needs no build tool installed.

## The recording

Captured on 2026-09-01 against `https://github.com/sindresorhus/is-online.git`,
trimmed only of the JVM's own start-up warnings and log4j's configuration lines.
The `[<url>]` prefix on each line names the repository it belongs to, which is what
keeps a batch of repositories legible when they interleave:

```text
Script started on 2026-09-01 12:39:11+00:00 [COMMAND="/home/user/tools/scripts/linux/adopt-demo.sh --adoption-only" <not executed on terminal>]
2026-09-01 12:39:15.023 INFO - Dry run: the adoption will be committed to the checkout but never pushed, and no pull request will be opened
2026-09-01 12:39:15.032 INFO - Adopting Claude Code into 1 repositories on branch claude/adopt-claude-code
2026-09-01 12:39:15.036 INFO [https://github.com/sindresorhus/is-online.git] - Repository 1 of 1: https://github.com/sindresorhus/is-online.git
2026-09-01 12:39:15.038 INFO [https://github.com/sindresorhus/is-online.git] - Adopting Claude Code into https://github.com/sindresorhus/is-online.git in 14 steps
2026-09-01 12:39:15.039 INFO [https://github.com/sindresorhus/is-online.git] - Step 1/14: toolchain
2026-09-01 12:39:15.039 INFO [https://github.com/sindresorhus/is-online.git] - Checking required tools are available: [git, claude]
2026-09-01 12:39:15.063 INFO [https://github.com/sindresorhus/is-online.git] - Found required tool: git
2026-09-01 12:39:15.081 INFO [https://github.com/sindresorhus/is-online.git] - Found required tool: claude
2026-09-01 12:39:15.082 INFO [https://github.com/sindresorhus/is-online.git] - Step toolchain completed in 43ms
2026-09-01 12:39:15.082 INFO [https://github.com/sindresorhus/is-online.git] - Step 2/14: clone
2026-09-01 12:39:15.083 INFO [https://github.com/sindresorhus/is-online.git] - Cloning https://github.com/sindresorhus/is-online.git into /home/user/tools/target/adopt-demo/workspace/is-online
2026-09-01 12:39:16.243 INFO [https://github.com/sindresorhus/is-online.git] - Step clone completed in 1s
2026-09-01 12:39:16.243 INFO [https://github.com/sindresorhus/is-online.git] - Step 3/14: build-toolchain
2026-09-01 12:39:16.253 INFO [https://github.com/sindresorhus/is-online.git] - Step build-toolchain completed in 9ms
2026-09-01 12:39:16.253 INFO [https://github.com/sindresorhus/is-online.git] - Step 4/14: branch
2026-09-01 12:39:16.253 INFO [https://github.com/sindresorhus/is-online.git] - Creating branch claude/adopt-claude-code in /home/user/tools/target/adopt-demo/workspace/is-online
2026-09-01 12:39:16.279 INFO [https://github.com/sindresorhus/is-online.git] - Step branch completed in 25ms
2026-09-01 12:39:16.280 INFO [https://github.com/sindresorhus/is-online.git] - Step 5/14: trust
2026-09-01 12:39:16.340 INFO [https://github.com/sindresorhus/is-online.git] - /home/user/tools/target/adopt-demo/workspace/is-online is already trusted for Claude Code; left unchanged
2026-09-01 12:39:16.341 INFO [https://github.com/sindresorhus/is-online.git] - Step trust completed in 60ms
2026-09-01 12:39:16.341 INFO [https://github.com/sindresorhus/is-online.git] - Step 6/14: claude-init
2026-09-01 12:39:16.342 INFO [https://github.com/sindresorhus/is-online.git] - Running claude init in /home/user/tools/target/adopt-demo/workspace/is-online (attempt 1 of 3)
2026-09-01 12:40:05.717 INFO [https://github.com/sindresorhus/is-online.git] - Step claude-init completed in 49s
2026-09-01 12:40:05.719 INFO [https://github.com/sindresorhus/is-online.git] - Step 7/14: conform
2026-09-01 12:40:05.721 INFO [https://github.com/sindresorhus/is-online.git] - Installed AGENTS.md
2026-09-01 12:40:05.760 INFO [https://github.com/sindresorhus/is-online.git] - Normalised CLAUDE.md to satisfy the claudeMdFormat rule
2026-09-01 12:40:05.760 INFO [https://github.com/sindresorhus/is-online.git] - Step conform completed in 40ms
2026-09-01 12:40:05.761 INFO [https://github.com/sindresorhus/is-online.git] - Step 8/14: commit:claude-md
2026-09-01 12:40:05.909 INFO [https://github.com/sindresorhus/is-online.git] - Committed: Adopt Claude Code: add CLAUDE.md
2026-09-01 12:40:05.909 INFO [https://github.com/sindresorhus/is-online.git] - Step commit:claude-md completed in 148ms
2026-09-01 12:40:05.910 INFO [https://github.com/sindresorhus/is-online.git] - Step 9/14: enforcer
2026-09-01 12:40:05.911 INFO [https://github.com/sindresorhus/is-online.git] - Installed .github/workflows/claude-md-guard.yml
2026-09-01 12:40:05.913 INFO [https://github.com/sindresorhus/is-online.git] - Installed .github/claude-md-guard.sh
2026-09-01 12:40:05.913 INFO [https://github.com/sindresorhus/is-online.git] - Wired the CLAUDE.md guard into the github-actions build in /home/user/tools/target/adopt-demo/workspace/is-online
2026-09-01 12:40:05.914 INFO [https://github.com/sindresorhus/is-online.git] - Step enforcer completed in 3ms
2026-09-01 12:40:05.914 INFO [https://github.com/sindresorhus/is-online.git] - Step 10/14: commit:guard
2026-09-01 12:40:06.078 INFO [https://github.com/sindresorhus/is-online.git] - Committed: Adopt Claude Code: add the CLAUDE.md guard
2026-09-01 12:40:06.079 INFO [https://github.com/sindresorhus/is-online.git] - Step commit:guard completed in 164ms
2026-09-01 12:40:06.079 INFO [https://github.com/sindresorhus/is-online.git] - Step 11/14: assets
2026-09-01 12:40:06.081 INFO [https://github.com/sindresorhus/is-online.git] - AGENTS.md already exists; left unchanged
2026-09-01 12:40:06.081 INFO [https://github.com/sindresorhus/is-online.git] - Installed .claude/settings.json
2026-09-01 12:40:06.082 INFO [https://github.com/sindresorhus/is-online.git] - Installed .claude/hooks/session-start.sh
2026-09-01 12:40:06.083 INFO [https://github.com/sindresorhus/is-online.git] - Installed .mcp.json
2026-09-01 12:40:06.084 INFO [https://github.com/sindresorhus/is-online.git] - Installed .github/workflows/claude.yml
2026-09-01 12:40:06.085 INFO [https://github.com/sindresorhus/is-online.git] - Step assets completed in 5ms
2026-09-01 12:40:06.085 INFO [https://github.com/sindresorhus/is-online.git] - Step 12/14: skills
2026-09-01 12:40:06.091 INFO [https://github.com/sindresorhus/is-online.git] - Installed .claude/skills/build-and-test/SKILL.md
2026-09-01 12:40:06.092 INFO [https://github.com/sindresorhus/is-online.git] - Installed .claude/skills/claude-md/SKILL.md
2026-09-01 12:40:06.093 INFO [https://github.com/sindresorhus/is-online.git] - Installed 2 starter skill(s) describing the github-actions build under /home/user/tools/target/adopt-demo/workspace/is-online/.claude/skills
2026-09-01 12:40:06.093 INFO [https://github.com/sindresorhus/is-online.git] - Step skills completed in 7ms
2026-09-01 12:40:06.093 INFO [https://github.com/sindresorhus/is-online.git] - Step 13/14: commit:assets
2026-09-01 12:40:06.263 INFO [https://github.com/sindresorhus/is-online.git] - Committed: Add Claude Code configuration assets
2026-09-01 12:40:06.263 INFO [https://github.com/sindresorhus/is-online.git] - Step commit:assets completed in 169ms
2026-09-01 12:40:06.263 INFO [https://github.com/sindresorhus/is-online.git] - Step 14/14: verify
2026-09-01 12:40:06.264 INFO [https://github.com/sindresorhus/is-online.git] - Verifying the CLAUDE.md guard passes with github-actions in /home/user/tools/target/adopt-demo/workspace/is-online
2026-09-01 12:40:06.271 INFO [https://github.com/sindresorhus/is-online.git] - Step verify completed in 7ms
2026-09-01 12:40:06.271 INFO [https://github.com/sindresorhus/is-online.git] - Adoption complete for https://github.com/sindresorhus/is-online.git in 51s
2026-09-01 12:40:06.274 INFO - Adopted 1 of 1 repositories in 51s
2026-09-01 12:40:06.289 INFO - Wrote the adoption report to /home/user/tools/target/adopt-demo/adopt-demo-report.json
Script done on 2026-09-01 12:40:06+00:00 [COMMAND_EXIT_CODE="0"]
```

## The report

`--report` writes the same outcome as JSON, for a failed run as well as a
successful one — the demo prints it at the end, and it is the only way to find the
temporary workspace a run that named none was given:

```json
{
  "repositoryUrl" : "https://github.com/sindresorhus/is-online.git",
  "branch" : "claude/adopt-claude-code",
  "checkout" : "/home/user/tools/target/adopt-demo/workspace/is-online",
  "pullRequestUrl" : null,
  "succeeded" : true,
  "failure" : null,
  "completedSteps" : [ "toolchain", "clone", "build-toolchain", "branch", "trust", "claude-init", "conform", "commit:claude-md", "enforcer", "commit:guard", "assets", "skills", "commit:assets", "verify" ]
}
```

`pullRequestUrl` is `null` and `completedSteps` ends at `verify`, because a dry
run's pipeline is *assembled* without `push` and `pull-request` rather than built
with steps that decide to do nothing. The report says what really happened.

## What the run left behind

A dry run keeps its checkout — that being all it produces — under the workspace the
report names. Four commits sit on the `claude/adopt-claude-code` branch there, and
reading them is the point of a rehearsal:

```bash
git -C target/adopt-demo/workspace/is-online log --oneline claude/adopt-claude-code
git -C target/adopt-demo/workspace/is-online show --stat HEAD
```

Nothing was pushed, so the adopted repository is untouched and its default branch —
which the adoption never writes to in any mode — is exactly as it was cloned.
