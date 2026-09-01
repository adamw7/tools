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

## The video

<video src="https://github.com/adamw7/tools/raw/main/docs/adopt-demo.mp4" controls muted playsinline width="900">
  <a href="adopt-demo.mp4"><code>adopt-demo.mp4</code></a> — the run below as a 22-second video.
</video>

[`adopt-demo.mp4`](adopt-demo.mp4) (391 KB, 22 seconds) is the run below as a
terminal-style video — H.264 in an `.mp4` container, which is what GitHub plays
**inline** in a rendered Markdown file rather than offering as a download, and
what a browser plays without a plugin. The `moov` atom is moved to the front of
the file (`-movflags +faststart`), so playback starts before the whole file has
arrived instead of after it.

The element above needs an absolute URL: GitHub rewrites a relative link into one
its player can use for images, not for video. The link inside it is what a renderer
with no player shows instead — an IDE preview of this file, say.

A player older than H.264 is a `--output` away: `--output <name>.mpg` writes the
same run as MPEG-2, which every player back to a DVD one accepts, at six times the
size for the same 22 seconds. Nothing here links such a file, because GitHub will
not preview one.

It is a **rendering of the transcript, not a screen capture**: one frame per line
the pipeline logged, drawn from the text below. Two things about it are worth
knowing, because both are deliberate:

- **Playback is compressed, the timings are not.** A gap between two lines is held
  for at most 1.2 seconds, so the 37 seconds `claude init` spends thinking do not
  become 37 seconds of a still frame. The header carries the run's *real* elapsed
  time, read from the log, so the 22-second video still says the adoption took 37
  seconds.
- **The repository prefix is hoisted into the title bar.** Every log line carries
  the repository it belongs to — invaluable when a batch interleaves, unreadable
  when one repository repeats it sixty times. The transcript below keeps it.

`scripts/linux/adopt-demo-video.py` does the rendering, and the demo script calls
it whenever `ffmpeg` and `Pillow` are both present — a machine without them still
gets the transcript, which is the actual recording. It also runs on its own
against any transcript:

```bash
scripts/linux/adopt-demo-video.py \
    --transcript target/adopt-demo/adopt-demo.txt \
    --output docs/adopt-demo.mp4
```

The **output's suffix picks the codec**, because the two are not freely mixed:
`.mp4` is written as H.264 and `.mpg` as MPEG-2, `--codec` overrides that, and any
other suffix is refused by name rather than handed to ffmpeg to fail on. `--quality`
follows the codec it lands on — H.264's `-crf` (0 best, 51 worst, 23 by default),
MPEG's `-q:v` (1 best, 31 worst, 9 by default) — as does `--fps`, which MPEG-1 and
MPEG-2 accept only at the broadcast rates, having no way to express any other.

Size is governed by `--gop`, not by the resolution: a terminal recording is nearly
all still frames, and a default keyframe interval re-sends the whole screen twice a
second. At `--gop 250` the same 22 seconds cost 391 KB as H.264 and 2.5 MB as
MPEG-2.

## The recording

Captured on 2026-09-01 against `https://github.com/sindresorhus/is-online.git`,
trimmed only of the JVM's own start-up warnings and log4j's configuration lines.
The `[<url>]` prefix on each line names the repository it belongs to, which is what
keeps a batch of repositories legible when they interleave:

```text
Script started on 2026-09-01 12:56:44+00:00 [COMMAND="/home/user/tools/scripts/linux/adopt-demo.sh --adoption-only" <not executed on terminal>]
2026-09-01 12:56:48.467 INFO - Dry run: the adoption will be committed to the checkout but never pushed, and no pull request will be opened
2026-09-01 12:56:48.477 INFO - Adopting Claude Code into 1 repositories on branch claude/adopt-claude-code
2026-09-01 12:56:48.481 INFO [https://github.com/sindresorhus/is-online.git] - Repository 1 of 1: https://github.com/sindresorhus/is-online.git
2026-09-01 12:56:48.484 INFO [https://github.com/sindresorhus/is-online.git] - Adopting Claude Code into https://github.com/sindresorhus/is-online.git in 14 steps
2026-09-01 12:56:48.484 INFO [https://github.com/sindresorhus/is-online.git] - Step 1/14: toolchain
2026-09-01 12:56:48.485 INFO [https://github.com/sindresorhus/is-online.git] - Checking required tools are available: [git, claude]
2026-09-01 12:56:48.507 INFO [https://github.com/sindresorhus/is-online.git] - Found required tool: git
2026-09-01 12:56:48.528 INFO [https://github.com/sindresorhus/is-online.git] - Found required tool: claude
2026-09-01 12:56:48.528 INFO [https://github.com/sindresorhus/is-online.git] - Step toolchain completed in 43ms
2026-09-01 12:56:48.529 INFO [https://github.com/sindresorhus/is-online.git] - Step 2/14: clone
2026-09-01 12:56:48.529 INFO [https://github.com/sindresorhus/is-online.git] - Cloning https://github.com/sindresorhus/is-online.git into /home/user/tools/target/adopt-demo/workspace/is-online
2026-09-01 12:56:49.691 INFO [https://github.com/sindresorhus/is-online.git] - Step clone completed in 1s
2026-09-01 12:56:49.691 INFO [https://github.com/sindresorhus/is-online.git] - Step 3/14: build-toolchain
2026-09-01 12:56:49.703 INFO [https://github.com/sindresorhus/is-online.git] - Step build-toolchain completed in 11ms
2026-09-01 12:56:49.704 INFO [https://github.com/sindresorhus/is-online.git] - Step 4/14: branch
2026-09-01 12:56:49.704 INFO [https://github.com/sindresorhus/is-online.git] - Creating branch claude/adopt-claude-code in /home/user/tools/target/adopt-demo/workspace/is-online
2026-09-01 12:56:49.735 INFO [https://github.com/sindresorhus/is-online.git] - Step branch completed in 30ms
2026-09-01 12:56:49.735 INFO [https://github.com/sindresorhus/is-online.git] - Step 5/14: trust
2026-09-01 12:56:49.784 INFO [https://github.com/sindresorhus/is-online.git] - /home/user/tools/target/adopt-demo/workspace/is-online is already trusted for Claude Code; left unchanged
2026-09-01 12:56:49.785 INFO [https://github.com/sindresorhus/is-online.git] - Step trust completed in 49ms
2026-09-01 12:56:49.785 INFO [https://github.com/sindresorhus/is-online.git] - Step 6/14: claude-init
2026-09-01 12:56:49.785 INFO [https://github.com/sindresorhus/is-online.git] - Running claude init in /home/user/tools/target/adopt-demo/workspace/is-online (attempt 1 of 3)
2026-09-01 12:57:25.109 INFO [https://github.com/sindresorhus/is-online.git] - Step claude-init completed in 35s
2026-09-01 12:57:25.111 INFO [https://github.com/sindresorhus/is-online.git] - Step 7/14: conform
2026-09-01 12:57:25.113 INFO [https://github.com/sindresorhus/is-online.git] - Installed AGENTS.md
2026-09-01 12:57:25.150 INFO [https://github.com/sindresorhus/is-online.git] - Normalised CLAUDE.md to satisfy the claudeMdFormat rule
2026-09-01 12:57:25.151 INFO [https://github.com/sindresorhus/is-online.git] - Step conform completed in 39ms
2026-09-01 12:57:25.151 INFO [https://github.com/sindresorhus/is-online.git] - Step 8/14: commit:claude-md
2026-09-01 12:57:25.321 INFO [https://github.com/sindresorhus/is-online.git] - Committed: Adopt Claude Code: add CLAUDE.md
2026-09-01 12:57:25.322 INFO [https://github.com/sindresorhus/is-online.git] - Step commit:claude-md completed in 170ms
2026-09-01 12:57:25.322 INFO [https://github.com/sindresorhus/is-online.git] - Step 9/14: enforcer
2026-09-01 12:57:25.323 INFO [https://github.com/sindresorhus/is-online.git] - Installed .github/workflows/claude-md-guard.yml
2026-09-01 12:57:25.323 INFO [https://github.com/sindresorhus/is-online.git] - Installed .github/claude-md-guard.sh
2026-09-01 12:57:25.324 INFO [https://github.com/sindresorhus/is-online.git] - Wired the CLAUDE.md guard into the github-actions build in /home/user/tools/target/adopt-demo/workspace/is-online
2026-09-01 12:57:25.324 INFO [https://github.com/sindresorhus/is-online.git] - Step enforcer completed in 1ms
2026-09-01 12:57:25.324 INFO [https://github.com/sindresorhus/is-online.git] - Step 10/14: commit:guard
2026-09-01 12:57:25.492 INFO [https://github.com/sindresorhus/is-online.git] - Committed: Adopt Claude Code: add the CLAUDE.md guard
2026-09-01 12:57:25.492 INFO [https://github.com/sindresorhus/is-online.git] - Step commit:guard completed in 167ms
2026-09-01 12:57:25.493 INFO [https://github.com/sindresorhus/is-online.git] - Step 11/14: assets
2026-09-01 12:57:25.494 INFO [https://github.com/sindresorhus/is-online.git] - AGENTS.md already exists; left unchanged
2026-09-01 12:57:25.494 INFO [https://github.com/sindresorhus/is-online.git] - Installed .claude/settings.json
2026-09-01 12:57:25.495 INFO [https://github.com/sindresorhus/is-online.git] - Installed .claude/hooks/session-start.sh
2026-09-01 12:57:25.496 INFO [https://github.com/sindresorhus/is-online.git] - Installed .mcp.json
2026-09-01 12:57:25.497 INFO [https://github.com/sindresorhus/is-online.git] - Installed .github/workflows/claude.yml
2026-09-01 12:57:25.497 INFO [https://github.com/sindresorhus/is-online.git] - Step assets completed in 4ms
2026-09-01 12:57:25.497 INFO [https://github.com/sindresorhus/is-online.git] - Step 12/14: skills
2026-09-01 12:57:25.503 INFO [https://github.com/sindresorhus/is-online.git] - Installed .claude/skills/build-and-test/SKILL.md
2026-09-01 12:57:25.504 INFO [https://github.com/sindresorhus/is-online.git] - Installed .claude/skills/claude-md/SKILL.md
2026-09-01 12:57:25.504 INFO [https://github.com/sindresorhus/is-online.git] - Installed 2 starter skill(s) describing the github-actions build under /home/user/tools/target/adopt-demo/workspace/is-online/.claude/skills
2026-09-01 12:57:25.504 INFO [https://github.com/sindresorhus/is-online.git] - Step skills completed in 7ms
2026-09-01 12:57:25.505 INFO [https://github.com/sindresorhus/is-online.git] - Step 13/14: commit:assets
2026-09-01 12:57:25.700 INFO [https://github.com/sindresorhus/is-online.git] - Committed: Add Claude Code configuration assets
2026-09-01 12:57:25.700 INFO [https://github.com/sindresorhus/is-online.git] - Step commit:assets completed in 195ms
2026-09-01 12:57:25.701 INFO [https://github.com/sindresorhus/is-online.git] - Step 14/14: verify
2026-09-01 12:57:25.701 INFO [https://github.com/sindresorhus/is-online.git] - Verifying the CLAUDE.md guard passes with github-actions in /home/user/tools/target/adopt-demo/workspace/is-online
2026-09-01 12:57:25.708 INFO [https://github.com/sindresorhus/is-online.git] - Step verify completed in 6ms
2026-09-01 12:57:25.708 INFO [https://github.com/sindresorhus/is-online.git] - Adoption complete for https://github.com/sindresorhus/is-online.git in 37s
2026-09-01 12:57:25.710 INFO - Adopted 1 of 1 repositories in 37s
2026-09-01 12:57:25.725 INFO - Wrote the adoption report to /home/user/tools/target/adopt-demo/adopt-demo-report.json
Script done on 2026-09-01 12:57:25+00:00 [COMMAND_EXIT_CODE="0"]
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
