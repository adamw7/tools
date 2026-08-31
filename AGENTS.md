# AGENTS.md

Guidance for AI coding agents working in this repository. Human contributors may
find it useful too. This file follows the [agents.md](https://agents.md)
convention and is the single source of truth for agent instructions; `CLAUDE.md`
defers to it.

Detail that only matters while working on one area lives in the project skills
under `.claude/skills/` (see *Agent configuration*). **Prefer loading the
relevant skill over re-deriving a convention**; this file states the rule, the
skill carries the reasoning and the worked example.

## Project overview

`tools` is a multi-module Maven library of Java tooling:

- **Code generation** (`code/protogen-maven-plugin`) — a Maven plugin that
  generates protobuf builders which detect a missing required field at
  **compile time** instead of runtime (shift-left). proto2 `required` fields are
  enforced by the builder chain; proto3 has no required fields, so its builders
  are all-optional with presence-aware `hasXxx()` accessors for message fields
  and explicit `optional` fields only; a `oneof` group gets a `getXxxCase()`
  discriminator and a `clearXxx()` that resets the whole group. Skill:
  `protogen`.
- **Context engineering** (`code/context`) — a fast, regex-based finder that
  builds the tree of classes a given class uses, plus a `ProjectTreeBuilder` that
  scans a whole Java project into a tree of folders, files and dependencies, to
  assemble context for gen-AI agents. The same tree can be emitted as a bundle in
  Google's **Open Knowledge Format** (OKF) v0.2 — a directory of markdown concept
  documents with YAML frontmatter, in the `io.github.adamw7.context.okf` package.
  An **MCP server** (`io.github.adamw7.context.mcp`) exposes the project-tree,
  context-finder, token-estimation and OKF-bundle tools. Skill: `context-finder`.
- **Data** (`data`) — data sources (CSV, GZip, JDBC, Parquet, JSON, YAML, TOON),
  each in an in-memory and an iterative variant. Parquet is read through an
  in-process DuckDB engine, so it exposes columns and rows like any other
  JDBC-backed source. Sources that know their columns up front implement
  `ColumnarDataSource`, a narrower contract than `IterableDataSource`, so a
  caller that needs the schema — such as the uniqueness check — cannot be handed
  a forward-only source (JSON/YAML/TOON) that would only answer `null`. A read
  that breaks part-way through fails instead of reading as a short file: the
  `Scanner`-backed sources check `Scanner.ioException()` before reporting the end
  of the data, so a truncated transfer or a corrupt GZip member cannot pass for a
  clean end and leave the uniqueness check calling a column unique on half a
  file. Also a uniqueness checker (does a subset of columns form a key, and is
  there a smaller one), open-addressing collections (`OpenAddressingMap`,
  `OpenAddressingSet`, the primitive `IntKeyOpenAddressingMap`), and an **MCP
  server** exposing the uniqueness checker. Skill: `data-sources`.
- **Claude Code adoption** (`adopt`) — an ordered pipeline that adopts Claude
  Code into a GitHub repository. Skill: `adopt-pipeline`. Summarised below.

### The adoption pipeline

The steps run in order and stop at the first failure: check the pipeline's own
tools (`git`, `claude`, `gh`, and that `gh` is logged in — `gh --version`
succeeds for a CLI nobody is logged in to) → clone → check the *cloned* project's
build tool → create a feature branch (the default branch is never written to) →
mark the checkout trusted in `~/.claude.json` so the headless CLI is not blocked
by the folder-trust prompt → `claude init` to generate `CLAUDE.md`, conform it
(plus a companion `AGENTS.md`) and commit → wire a `CLAUDE.md` guard into the
project's build and commit → optionally install the starter assets and skills and
commit them together → verify the guard passes → push → open a pull request with
`gh pr create`.

What is worth knowing before changing any of it:

- **The guard checks the whole configuration by default.** A Maven project gets
  `claudeCodeProject` wired into its `pom.xml`, so the `AGENTS.md` the run
  installs — and, with `--assets`, the `.claude` directory of settings and hooks —
  is checked by the same build that checks the `CLAUDE.md`. Wiring one rule left
  what the adoption itself wrote unguarded: a malformed `settings.json`, a skill
  with no definition, or a credential committed into a hook all passed. `--rules
  minimal` wires the document rule alone, for a repository whose maintainers want
  nothing else of theirs read. Either counts as already guarded, so re-adopting a
  repository adopted before the composite existed leaves its guard alone rather
  than splicing a second execution in beside it.
- **The guard demands the sections the document was conformed to.** They are
  written into the POM rather than left to the rule's defaults, read from the one
  accessor the reshape also reads, so the document and the guard beside it cannot
  make different demands — and a project whose `CLAUDE.md` is not a Java
  project's is held to its own headings. `--section` names them; without it the
  detected build system decides.
- **The guard is build-tool aware**, behind a `BuildSystem` abstraction: a Maven
  project gets the `claude-code-enforcer` rule wired into its `pom.xml` and
  verified with `mvn -N validate`; a Gradle project (Groovy or Kotlin DSL) gets
  an `enforceClaudeMd` guard task appended to its build script; a repository with
  no recognised build file falls back to a GitHub Actions workflow plus the
  portable `.github/claude-md-guard.sh`, so a build-less repository still keeps a
  guard. `BuildSystem.requiredClaudeMdSections` follows the same choice, so only
  the Maven path — the only one that wires the format rule — demands that rule's
  Java and Maven headings; stamping them onto a non-Java repository documented
  nothing and nothing checked them. `BuildSystem.toolProbe` answers the whole
  probe command rather than a program name, and `toolAdvice` says what to do when
  a probe fails.
- **A committed build wrapper wins over the `PATH`.** `mvnw`/`gradlew` (and their
  Windows forms) are launched by absolute path — `ProcessBuilder` resolves a
  relative program against the JVM's working directory — and through `sh` when
  the file has no executable bit, the usual state of a wrapper committed from
  Windows.
- **`claude init` is skipped for a checkout that already has a `CLAUDE.md`**: the
  CLI's output is not reproducible, so regenerating would discard edits.
- **`claude init` is retried on its outcome, not its transcript.** It is the run's
  most expensive command and was the only one with no recovery: `TransientFailures`
  leaves `claude` out because its transcript is a model's prose and may discuss a
  connection reset without one having happened. That objection is about the
  transcript, so what is judged instead is whether the file exists — a run that
  produced it has succeeded whatever it exited with, and one that did not has
  produced nothing to lose. The memory file is moved aside and restored around
  each attempt, since a second attempt must meet the checkout the first one did.
- **A reused checkout is confirmed to be the repository under adoption** by
  comparing what its `origin` names with the URL given, read with `git config
  --get-all remote.origin.url` rather than `git remote get-url`, which expands
  `url.<base>.insteadOf` rewrites and so answers a host the checkout never
  recorded. Two URLs name one repository when they agree once scheme,
  credentials, `.git` suffix, trailing slash and case are set aside; anything
  else, including a checkout with no `origin`, is refused. The checkout is then
  refreshed with `git fetch`, since `BranchStep` starts the feature branch from
  the remote-tracking refs — through the credentials the run was given, because
  the `origin` an earlier run left behind deliberately has none and a plain fetch
  reaches a private repository as an anonymous caller.
- **One run adopts a list of repositories** — repeatable `--repo <url>`, `--repos
  <file>` (one URL per line, `#` comments and blank lines skipped), or
  `repository_urls` on the MCP tool (a JSON array or a comma-separated string).
  Duplicates are dropped, `--workspace`/`--branch` name the shared workspace and
  branch, and the first positional is always a repository URL, never a workspace.
  Each repository gets its own checkout (claimed inside its own adoption) and its
  own report, and one that fails does not stop the rest; `Main` raises the
  failures together afterwards so the process still exits non-zero.
- **`--parallel <n>`** adopts several at once (up to 8). The batch was sequential
  because the tools' output interleaved into a log nobody could attribute, which
  is a solvable problem rather than a reason: each adoption puts its repository
  into the logging context of its own thread and both appender patterns print it.
  The repositories were already independent; `Checkouts` claims through a
  concurrent map so the test-and-set that stops two of them cloning into one
  directory produces a claim and a refusal rather than two claims. A batch of one,
  or a parallelism of one, starts no pool and runs on the calling thread.
- **A checkout whose adoption landed is removed**, unless `--keep-workspace`. A
  batch makes one full clone per repository and nothing used to remove them, so a
  fifty-repository run left fifty behind under a temporary directory nobody named.
  A failed adoption's checkout is kept — it is the only record of how far the run
  got — and so is a dry run's, which is all a dry run produces. A checkout that
  cannot be removed is a warning, not a reason to report the repository as failed.
- **`--verify-only`** answers "is this repository still adopted, and does its
  guard still pass?" without adopting anything: it clones, reads, and writes
  nothing, needing only `git`. It is a pipeline of its own rather than an adoption
  with its writing steps disabled, for the same reason a dry run is. Its extra
  step is `check-adopted`, which exists because the guard's own command cannot
  answer the question — a build with no guard wired in passes `mvn -N validate`
  precisely because nothing ran, so a repository that was never adopted, or whose
  guard a later commit removed, verified exactly like one that was. Both halves,
  the document and the guard, are reported together.
- **`--dry-run`** assembles the pipeline *without* `PushStep` and
  `PullRequestStep` rather than with steps that decide to do nothing, and asks
  the toolchain check only for the `git` and `claude` a rehearsal really runs. A
  run therefore needs no GitHub credentials at all, and `completedSteps` ends at
  `verify`.
- **Credentials never outlive the run.** Every log line, failure message and
  report field goes through `Redaction` — which lives in `mcp-common`
  (`io.github.adamw7.tools.secret`), the lowest module this pipeline and the
  shared MCP failure path both build on — and it masks the user information of a
  URL carrying a scheme, so a CI runner's
  `https://x-access-token:TOKEN@github.com/...` never reaches disk or an MCP
  client. `git` is still handed the URL as given, but only per invocation: the
  clone rewrites the `origin` it just recorded to the credential-free form, and
  the two commands that still have to authenticate supply the URL themselves —
  `PushStep` as a `-c remote.origin.pushurl` override, and the resuming fetch as
  a `-c url.<credentialled>.insteadOf=<origin>` rewrite plus
  `--no-write-fetch-head`. Both forms are transient. Neither
  `-c remote.origin.url` nor a positional URL would do: git reads the first as
  *another* value of a multi-valued key and resolves the configured one instead,
  and records the second in the reflog of every ref the fetch updates. Both `gh`
  invocations name the repository with `--repo` rather than letting `gh` infer it
  from the remote.
- **Configuration is one object.** `AdoptionOptions` (wrapping
  `PullRequestOptions`) carries the pull-request metadata, the starter assets,
  the rule version, the dry-run flag, the per-command timeout and the retry
  count, and both entry points hand it to the same pipeline factory, so the CLI
  and the MCP tool cannot drift. `CliArguments` declares the command line to **picocli**, binding the
  repository, workspace and branch options to methods — the first so a batch
  mixing `--repo` and `--repos` keeps the order it was written in, the other two
  because each shares a field with its positional and the last one named has to
  win — and everything else straight to a field; refusals are re-raised as an
  `IllegalArgumentException`
  carrying the hand-written usage line, with the parser's own message masked
  through `Redaction` and its exception left unchained — it quotes the argument it
  could not place, which for this command can be a credentialled clone URL.
  `--help` is answered with the usage line even when another argument on the same
  line could not be read.
- **The wired rule version may not be a `-SNAPSHOT`** (`EnforcerRuleVersion`): it
  resolves only from the adopting machine's local repository and would leave the
  adopted project's CI unable to build.
- **Detection reads the pom's own `build/plugins`** — the one place a rule runs on
  every build, and the very place the installer would add one. A rule declared only
  in `pluginManagement`, a profile, or `reporting` runs on no ordinary build, so the
  project gets an always-on declaration of its own and keeps the one it had. The
  plugin's *version* is the opposite question and reads the opposite way: an
  enforcer the build declares without one resolves through `pluginManagement`, so
  that is where the refusal to wire the rule into a plugin older than 3.1.0 —
  which cannot look a rule up by name — looks when the declaration names none.
- **A pom is edited as text, never re-serialised** (`PomDocument`): the addition
  is spliced into the bytes the file already held, at the source offsets
  **jsoup**'s XML parser reports for every start and end tag, so the adoption
  commit shows only the block that was added. A DOM records nothing about where
  its elements were read from, which is why this used to pair a JAXP parse with a
  lexical scan of its own; a pom that leaves an element open is refused, because
  jsoup repairs what it reads and an edit at a repaired offset would land inside
  an element it was never meant to touch.
- **`--assets` commits starter configuration** — an `AGENTS.md` pointer, a
  `.claude/settings.json` denying reads of obvious secret files and wiring a
  `.claude/hooks/session-start.sh` stub, a starter `.mcp.json`, and a workflow
  answering `@claude` mentions — never overwriting a file the repository already
  has.
- **`--assets` also creates the starter skills.** `SkillsStep` writes
  `.claude/skills/build-and-test/SKILL.md` and `.claude/skills/claude-md/SKILL.md`
  beside
  the assets, into the same commit. It is its own step rather than two more
  entries in `AdoptionAssets.DEFAULTS` because a skill's body depends on the
  checkout's build system: it names the build the guard was wired into
  (`BuildSystem.buildDescription()`, so the catch-all does not tell a project it
  "is built with github-actions"), the headings that guard demands, and the
  command `VerifyStep` runs — relativized, since `verifyCommand` answers a
  checkout's wrapper by absolute path and committing that would put the adoption
  host's workspace path into somebody else's repository. What the adoption does
  not know — how the project builds, tests and lints — is left as headings for its
  maintainers, the bargain the session-start hook stub already strikes. A skill
  whose name the project's own commands, sub-agents or skills already claim is
  not installed at all: the guard fails a repository where two definitions answer
  to one name, and the name is the project's. The build skill is called
  `build-and-test` for a related reason: a skill's name is a directory name, and
  `build` is one of the most widely ignored words in a JVM project's `.gitignore`,
  so four of the seven repositories `ForeignRepositoryAdoptionIT` adopts excluded
  `.claude/skills/build/SKILL.md` and `CommitStep` rightly refused to commit
  around it. Weigh a new skill's name against what a repository ignores, not only
  against what it already names.
- **Everything shells out through a `CommandRunner`**, so steps are unit-tested
  without spawning processes; `ProcessCommandRunner` bounds every command with a
  timeout, ten minutes by default and overridable with `--timeout <minutes>`
  (`timeout_minutes`). Both are bounded by `AdoptionOptions.MAX_TIMEOUT` — a day,
  past which neither a long-lived MCP server nor an unattended batch reclaims the
  command — and the record enforces it, so a caller assembling the pipeline for
  itself is held to it too.
- **A command the network refused is tried again**, because an unattended batch
  otherwise lost a repository — after paying for its `claude init` — to one
  connection reset. `RetryingCommandRunner` decorates the process runner, waiting
  2s, 4s, 8s (capped at 30s) before further attempts:
  `--retries <count>` (`retries`), two by default, zero for none, bounded by
  `AdoptionOptions.MAX_RETRIES`. What it retries is narrow, and stated once in
  `TransientFailures`: the program must be `git` or `gh` — both re-runnable, while
  `claude` and a build tool are expensive and print prose that may merely *discuss*
  a reset — and the transcript must report a transport-level refusal in the tools'
  own words. A 403, a 404, a rejected non-fast-forward, a rate limit that wants
  minutes, and the git queries that answer through a non-zero exit all fail on the
  first attempt as before; a command that *throws* — an unstartable program, a
  timeout — is never retried. A retried attempt is logged with its redacted
  transcript, since a step only reports the command that stopped it.
- **One place assembles the toolchain.** `CommandRunners.forRun(options)` builds
  the bounded process runner and its retry decorator, and both entry points call
  it; an ArchUnit rule confines those two constructors to the `command` package,
  so the command line and the MCP tool cannot answer a `--timeout` or a
  `--retries` on terms of their own. A run configured with no retries is wrapped
  all the same, the decorator being a pass-through then.
- **`--help` answers with the usage line and adopts nothing**, rather than being
  refused as an unknown option. It goes to the log, whose console appender writes
  to standard error, because the same jar is the MCP server and its stdio
  transport owns standard output.
- **The console and the log file carry different things.** The console is
  threshold-filtered to `info` and shows progress: the repository being adopted
  and which of how many, `Step 4/12: branch`, what each step cost, and a closing
  count of the repositories that landed. `logs/adopt.log` additionally carries the
  adoption's own `debug` (`io.github.adamw7.tools.adopt` is set to it; the root
  stays at `info` so the embedded Spring Boot MCP server does not bury it) —
  every `git`/`claude`/`gh` invocation with its working directory, exit code and
  duration, and the redacted transcript of the ones that failed. That trace exists
  because a step may *tolerate* a non-zero exit, which otherwise leaves a run that
  quietly did nothing looking exactly like one that did everything.

An **MCP server** (`io.github.adamw7.tools.adopt.mcp`) exposes the pipeline as an
`adopt_repo` tool answering with the JSON `AdoptionReport` (completed steps, the
pull request URL read back with `gh pr list --json url`, and the failing step's
message when there was one). `--report <file>` writes the same document, on the
failure path too; a multi-repository run writes an overall `succeeded` plus a
`repositories` array, while a single-repository run keeps writing its document
unwrapped.

### Further reading

- [README.md](README.md) — worked code examples of each capability.
- [docs/c4-architecture.md](docs/c4-architecture.md) — a C4 model (System Context
  → Containers → Components) as Mermaid diagrams.
- [docs/compile-time-safe-builders.md](docs/compile-time-safe-builders.md) — how
  the generated builder chain shifts validation to compile time.
- [docs/adr](docs/adr) — the **architecture decision records** behind the
  standing choices (the foundational record, the security and supply-chain
  posture, TLS 1.3 and hybrid post-quantum key exchange, CodeQL, the two
  dependency-update bots, DuckDB as the Parquet engine, log4j2, MCP on Spring
  Boot, documentation as an enforced contract, path confinement scoped per data
  source). A record is immutable once
  accepted: revisiting a decision means a new ADR that supersedes it, never an
  edit to the old one. [docs/adr/README.md](docs/adr/README.md) has the
  numbering, template and status vocabulary.

## Module layout

```
tools (root pom, packaging=pom)
├── markdown-common             # shared Markdown reader: lines plus the code and
│                               #   HTML-comment masks (dependency-free)
├── claude-code-enforcer        # custom maven-enforcer rules validating CLAUDE.md,
│                               #   AGENTS.md, README.md and the .claude config
├── test-common                 # shared ArchUnit rule libraries and assertions (test-jar)
├── mcp-common                  # shared MCP server scaffolding (transport wiring, tool SPI,
│                               #   credential masking shared with adopt)
├── data                        # data sources, uniqueness checks, structures, MCP server
├── code
│   ├── protogen-maven-plugin       # the builder-generating Maven plugin
│   ├── protogen-maven-plugin-test  # integration tests / use cases for the plugin
│   └── context                     # class-usage context finder + OKF bundles + MCP server
├── adopt                       # adopts Claude Code into a GitHub repo
├── grpc-example                # end-to-end gRPC example with compile-time-safe builders
├── assembly                    # runnable SampleApp distribution: launcher jar + lib/
│                               #   (mainClass: io.github.adamw7.tools.data.SampleApp)
└── data-test                   # standalone test module for the data module
```

Root reactor modules are `markdown-common`, `claude-code-enforcer`,
`test-common`, `mcp-common`, `data`, `code`, `adopt`, `grpc-example` and
`assembly`. `data-test` is built separately (it is not in the root `<modules>`
list).

`markdown-common` exists because two modules read the same document and must
agree about it: `claude-code-enforcer`'s `claudeMdFormat` rule judges a
`CLAUDE.md`, and `adopt`'s `ClaudeMdConformer` reshapes one so that rule passes.
`adopt` cannot depend on `claude-code-enforcer` to share the reader — that would
put the maven-enforcer API and a shipped rule on every consumer's classpath — so
each carried a copy, and every way the copies drifted apart let the adoption
commit and push a file that then failed its own verification. Keep the module
free of dependencies beyond the JDK; its architecture test pins that, because
anything added there travels to both consumers and to every repository that
resolves the enforcer rule.

Base Java package: `io.github.adamw7` (`io.github.adamw7.context` for the context
module, `io.github.adamw7.tools.*` elsewhere).

Three MCP servers ship here, each a Spring Boot app whose entry point is
`Main.java` and which supports stdio (default), streamable HTTP
(`--transport.mode=streamable-http`, served at `/mcp`) or stateless HTTP
(`--transport.mode=stateless-http`, session-less, also `/mcp`). Any other value
is refused at startup, naming the three, since a mode no transport matches used
to leave a server bound to its port with no `/mcp` endpoint at all. Each has an
`MCP_USAGE.md` next to its `mcp` package:

| Server | Package | Tools |
| --- | --- | --- |
| uniqueness | `data/.../data/uniqueness/mcp/` | the uniqueness checker |
| context | `code/context/.../context/mcp/` | `project_tree`, `find_context`, `estimate_tokens`, `okf_bundle` |
| adoption | `adopt/.../adopt/mcp/` | `adopt_repo` |

## Environment & toolchain

- **Java 25** (the `java.version` property, from which the Spring Boot parent
  derives `maven.compiler.release`; `source`/`target` are deliberately unset
  because the compiler plugin ignores them once `release` is set). A JDK 25 must
  be on the `PATH` with `JAVA_HOME` set. In Claude Code web/remote sessions the
  `.claude/hooks/session-start.sh` hook installs `openjdk-25-jdk`, exports
  `JAVA_HOME`, and pre-fetches dependencies (`mvn dependency:go-offline`).
- **Maven 3.9.x.** Build from the repository root.

## Helper scripts

`scripts/` holds developer-environment conveniences (not part of the Maven
build), with parallel `linux/` (`*.sh`) and `windows/` (`*.bat`/`*.ps1`)
variants:

- `install-jdk-25` — installs Eclipse Temurin JDK 25, skipping the download when
  one is already present.
- `generate-maven-update-reports` — runs the `versions-maven-plugin` aggregate
  plugin- and dependency-update reports.
- `update-claude-code` — runs `claude update`.
- `update-git-client` — upgrades the system `git` via the host package manager.
- `update-git-repos-async` — `git pull`s every repository in the script's parent
  directory in parallel.

## Build, test, and run

```bash
# Install the custom enforcer rule into the local repo. Only needed to run the
# CLAUDE.md check locally (see "CLAUDE.md enforcement").
mvn -pl claude-code-enforcer -am install

# Full clean build + install to local repo (use clean — see note below)
mvn clean install

# Faster incremental build when you have NOT removed any generation sources
mvn install

# Build without installing (what CI runs)
mvn -B package

# Run the tests for a single module (-am is required, see below)
mvn -pl data -am test

# Build the standalone data-test module. It is not in the root <modules>, so
# -pl cannot select it; run it from its own directory, with tools.data installed.
cd data-test && mvn test
```

**Always pair `-pl` with `-am`.** A bare `mvn -pl data test` fails before it
compiles anything: the root pom's `ReactorModuleConvergence` enforcer rule
rejects a reactor whose module parents are not part of it, and sibling
`-SNAPSHOT` dependencies (e.g. `mcp-common`) do not resolve from the local
repository until they are installed. `-am` ("also make") fixes both.

To run a **single test class or method**, note that `-Dtest` applies to every
module in the reactor, so surefire fails the upstream modules where the pattern
matches nothing. Use either form:

```bash
# From the module directory — needs the parent and siblings installed once
# (mvn install -DskipTests), because they resolve from the local repository.
cd data && mvn test -Dtest='KeyFinderTest#repeatedRowIsADuplicate'

# From the root, telling surefire not to fail the modules with no match
mvn -pl data -am test -Dtest=KeyFinderTest -Dsurefire.failIfNoSpecifiedTests=false
```

**Always use `clean` after removing a source of code generation**, so stale
generated builders in `target/` cannot linger and mask the change. Otherwise
plain `mvn install` is fine and faster.

**Quiet, parallel builds.** `.mvn/maven.config` passes `--no-transfer-progress`
(no artifact-transfer noise) and `-T1C` (one build thread per core), so the
reactor builds independent modules — and runs their test forks — in parallel;
Maven honours the dependency graph, so ordering stays correct. Override with
`-T1` for a serial build. Contending test forks stretch the one-time cold-fork
warmup well beyond its ~0.7 s dedicated-core cost (measured up to ~3.9 s), which
is why the per-test timeout is 5 s rather than tighter. The workflows also pass
`-ntp` explicitly, so quiet CI logs do not depend on `.mvn/maven.config` being
picked up.

**Shellcheck.** The root pom lints `scripts/**/*.sh` and
`.claude/hooks/**/*.sh` with `dev.dimlight:shellcheck-maven-plugin`, configured
with
`<binaryResolutionMethod>embedded</binaryResolutionMethod>`: the `shellcheck`
binary rides in as an ordinary Maven Central artifact inside the plugin jar, so
nothing is fetched from GitHub and no `shellcheck` needs to be installed — the
lint works offline and in networks that cannot reach GitHub (including Claude
Code web/remote sessions). The plugin's default method downloads the binary from
GitHub releases; where that host is blocked the build fails on the root pom with
`Input is not in the XZ format` before any Java module is reached. Skip the lint
with `-Dskip.shellcheck=true`.

`<failBuildIfWarnings>true</failBuildIfWarnings>` is what makes the lint a gate.
The plugin defaults it to `false`, which prints shellcheck's findings as Maven
warnings and then passes: a probe script carrying an unquoted expansion and an
unassigned variable was reported line by line and the build still succeeded, so
the lint had been running for a log nobody reads. Both source directories are
clean, so the flag costs nothing today and fails the build on the next finding.
The hooks directory is linted because a mistake there breaks a *session* rather
than a build, and is found by whoever opened that session: `hooksFormat` checks
the shebang and the executable bit, and only shellcheck reads what the script
actually does.

**The pull-request build.** `.github/workflows/maven.yml` installs the enforcer
rule (`mvn -B -pl claude-code-enforcer -am install -DskipTests`, tests skipped
because it only needs to publish the JAR) and then runs
`mvn -B package -DenforceClaudeMd`. That `package` step is capped at **120
seconds** two ways: `timeout-minutes: 2` cancels a build that wedges, and a
wall-clock check fails with a `::error::` annotation saying by how much a
slow-but-finishing build overran. Both are scoped to the step, so checkout, JDK
setup and the bootstrap do not count against the budget. A run that trips either
limit means the build got slower — profile it rather than raising the number.
The cap applies only here; the scheduled Windows, coverage, mutation and
integration-test builds are legitimately longer and carry no timeout.

## Testing, coverage & mutation testing

Write tests for all new logic — behavior, edge cases, and error paths. Skill:
`testing-conventions`.

### Unit tests

Run in the normal `test`/`package` lifecycle. Surefire (root `pom.xml`) enforces:

| Limit | Value | Guards |
| --- | --- | --- |
| `junit.jupiter.execution.timeout.testable.method.default` | **5 s** (8 s under coverage) | a unit test doing real work |
| `junit.jupiter.execution.timeout.lifecycle.method.default` | **10 s** (15 s under coverage) | heavier shared setup, e.g. `DBTest` booting an embedded Derby |
| `forkedProcessTimeoutInSeconds` | **300 s** | a fork that wedges outright, which a per-method timeout cannot interrupt |

The 5 s bound sits above the cold-fork warmup under parallel-build CPU
contention but still catches real work; it is not a budget to spend. A genuinely
heavier test (shelling out to `protoc`, streaming a large data set) opts out with
an explicit `@Timeout` carrying a comment that says why. `*IT`s do not inherit
the limit, and ArchUnit runs on a separate engine the JUnit timeout does not
apply to. The one module using Mockito (`protogen-maven-plugin`) pre-loads it as
a surefire `-javaagent`, so the byte-buddy self-attach happens at JVM startup
rather than inside the first timed test.

**Unit tests run a class at a time in parallel.** Surefire turns JUnit's parallel
execution on with `mode.classes.default = concurrent` but `mode.default =
same_thread`: `unit.test.parallelism` (default **3**) test classes run at once in
a module, while the methods of one class stay on a single thread. A class already
owns its fixture — 79 of them take a `@TempDir`, which JUnit makes unique per
class — so concurrency *between* classes is the mode this suite can take;
concurrency *within* one would interleave methods sharing a `@BeforeEach`-built
instance, which nothing here was written for. The number is deliberately a small
constant, not per-core: the reactor's `-T1C` is already per-core, and a per-core
value here would multiply out to the square of the core count and push the
slowest methods into the 5 s limit. `-Dunit.test.parallelism=1` turns it off for
a run.

Process-global state is guarded at the class that writes it, because a class
cannot own it alone:

| State | Guard | Where |
| --- | --- | --- |
| a system property the whole JVM reads | `@Isolated` | `TransportConfigurerTest`, `TlsConfigurationTest`, `MainTest`, `ClaudeCodeEnforcerRuleConfigurationTest` |
| `PathValidator`'s deprecated process-wide base directory | `@Isolated` | `PathValidatorTest` |
| the one embedded Derby database | `@ResourceLock("derby-testDB")` on `DBTest` | inherited by `UniquenessCheckTest`, `SQLDataSourceTest` |

The Derby lock is what the suite most depends on: its subclasses share a static
`Connection` and one `jdbc:derby:memory:testDB`, so without the lock two of them
racing create the same tables, overwrite each other's connection, and drop the
database mid-test — reproducibly, every run. `Switch` needs no guard: it is
one-way and every class engages it before its own first test.

**Unit tests run with the network off.** The `data` module registers a
`NetworkOffExtension` — a JUnit `BeforeAllCallback` discovered through
`META-INF/services` — that calls `Switch.off()` before any test runs. Both the
auto-detection and the `tools.test.network.off` guard property are set only on
surefire, so the failsafe `*IT`s, which need real network, are unaffected.
Because `ServiceLoader` ignores `META-INF/services` for a named JPMS module,
`data` runs its unit tests from the classpath (`<useModulePath>false</useModulePath>`);
the published artifact stays a proper module. `NetworkOffDuringUnitTestsTest` verifies the switch is already engaged by the
time a unit test executes. A test opts in explicitly with `@NetworkOff`, which engages the switch regardless of the guard property (so the
network is off when that test is run from an IDE too).

### Architecture tests

ArchUnit tests run as ordinary JUnit tests in every module, under an
`...architecture` test package, and analyse only production classes. New code
must satisfy them or the module's test suite fails.

Repo-wide rules (declared once in `test-common` and imported with
`ArchTests.in(...)`, so each module's test states only what is specific to it):

- `CommonCodingConventions` — loggers are `private static final`; public fields
  are `final`; mutable static state is `volatile`; a field is never `Optional`;
  date and time use `java.time`, never `Date`/`Calendar`; production code logs
  through log4j2 — no `System.out`/`err`, `java.util.logging`,
  `java.lang.System.Logger`, `printStackTrace` or `System.exit`; no generic
  exceptions; no Joda Time; packages free of cycles. The rule bans the JDK's own
  logging rather than requiring log4j2 by name, which is what lets
  `protogen-maven-plugin` log through `AbstractMojo.getLog()` instead; its own
  `pluginLogsThroughTheMojoLog` states that narrower rule, so the exemption is
  visible in the module rather than a hole in the shared one.
- `CommonNamingConventions` — abstract types carry an `Abstract` prefix. Kept
  separate because `claude-code-enforcer` is exempt: its abstract rule bases are
  public API that poms configure by name.
- `CommonTestConventions` — every `@Testable` method lives in a `*Test`/`*IT`
  class; no `@Disabled`; JUnit Jupiter only; no `System.out`/`err`; no
  `Thread.sleep`; a `*Test` that writes a system property is `@Isolated`, since
  the unit run is class-parallel (the `*IT`s failsafe runs one after another are
  exempt).

Adding a repository-wide convention means editing one library, not six tests; an
exemption means a module not importing a library, which stays visible.

Per-module rules:

- **`data`** — data-source contracts in `source.interfaces` must not depend on
  their `source.db`/`source.file` implementations; the uniqueness core must not
  depend on its MCP adapter; the `structure` collections stay decoupled from data
  sources; JDBC (`java.sql`) stays confined to `source.db`. Its
  `TestConventionsArchitectureTest` adds the module's counterpart to the shared
  system-property rule: a `*Test` that sets or clears `PathValidator`'s deprecated
  process-wide base directory is `@Isolated`, that field being the other JVM-wide
  state a test here can write. It retires with those two methods — a test confining
  a source through its own `AllowedPaths` writes nothing the JVM shares.
- **`adopt`** (`AdoptArchitectureTest`) — the `command` package is the only place
  a process is spawned, and a step knows only the `CommandRunner` contract, never
  `ProcessCommandRunner`; a step never reaches back to `GitHubRepoAdopter`,
  `BatchAdoption`, `CliArguments` or `Main`, and holds no mutable field, since
  one instance adopts every repository of a batch; only `CloneStep` and
  `PushStep` call `AdoptionContext.repositoryUrl()` — the two commands that
  authenticate to the remote — while everything else reads the redacted
  `displayUrl()` or the credential-free `checkoutUrl()`; the adoption speaks no
  network protocol of its own outside the
  MCP package; `mcp` is a delivery mechanism nothing depends on and the only
  place that knows Spring; implementations are pinned to their contracts by name
  (`*Step`, `*BuildSystem`, `*CommandRunner`, `*Tool`); the only public
  `static void main` methods are the two the pom names; no public method declares
  a checked `IOException`, failure being the unchecked `AdoptionException`;
  `GitHubRepoAdopter` is the only class outside `step` that may depend on an
  `AdoptionStep`; a `BuildSystem` never depends on `CommandRunner`; and
  `System.getenv`, `java.util.concurrent` and `Thread` are confined to `command`.
- **`claude-code-enforcer`** (`EnforcerArchitectureTest`) — every concrete rule is
  a public `@Named` type (maven-enforcer resolves rules through the Sisu index, so
  an unannotated one tests green and then fails the build that configures it),
  whose `@Named` value is its class name minus the `Rule` suffix, decapitalised —
  the string the poms and these docs use. A rule may read the project and nothing
  else: no `ProcessBuilder`/`Process`/`Runtime` (so `hookCommandsValid` checks
  commands without running one), no network (so the checks work offline and a
  secret scanner cannot become the leak), and the only classes writing through
  `Files` are `HtmlReport`, `Baseline` and `MarkdownText`'s front-matter fix.
  `..enforcer.text..` stays free of the Maven API, `javax.inject` and Jackson,
  which is what lets a reader be tested without a Maven session, and `JsonNodes`
  is the only class depending on `ObjectMapper`, so every JSON rule reads through
  the one mapper configured for the comments and trailing commas Claude Code's
  files allow.

`TestConventionsArchitectureTest` (same package, analysing only test classes via
`ImportOption.OnlyIncludeTests`) adds the shared test conventions plus, in
`adopt`, that a step test never spawns a real process and no `*IT` depends on
`PushStep` or `PullRequestStep`; and in `claude-code-enforcer`, that
`ProcessBuilder` is confined to the `e2e` package and every `@Testable` method
there sits in an `*IT` — forking Maven belongs to failsafe, not to the
pull-request build and its 120-second budget.

**Shared assertions.** `test-common` also carries `ExpectedFailures`, whose
`assertFailure(type, call, fragments...)` is the assertion nearly every rule and
adoption-step test makes: the call must fail with that exception and its message
must carry each fragment, with the thrown message as the assertion description by
construction. A test with more to say about the failure keeps the returned
exception.

### Integration tests

`*IT` classes are gated behind the `integration-tests` profile — declared per
module in `data`, `code/context`, `adopt` and `claude-code-enforcer`, so a new
module's `*IT`s stay unrun until it gets its own copy. Run them with
`mvn -P integration-tests verify`. They cover what needs something real:

- All three MCP servers are exercised over streamable HTTP and stateless HTTP:
  each boots on a random port and a real MCP client lists its tools and calls
  one, so the definitions and the results are met as a client meets them.
  `adopt`'s pair — `McpStreamableHttpIT` and `McpStatelessHttpIT` over
  `AbstractAdoptMcpIT` — calls `adopt_repo` with `verify_only`, which clones
  `octocat/Hello-World`, finds it was never adopted and answers with the failed
  run's JSON report. That is the heaviest payload the server can be asked for
  without credentials: a `dry_run` would reach `claude init`, and a verification
  shells out to `git` alone and writes nothing, to GitHub or to the checkout.
  The checkout is asserted on disk under the test's own workspace, so the clone
  is known to have run on the server rather than the arguments having been
  echoed back. The streamable test also pins the whole argument set of the
  schema as it arrives on the wire, an argument lost in translation being
  invisible from inside the pipeline.
- `adopt`'s `MultiRepoAdoptionIT` clones GitHub's `octocat/Hello-World` and
  `octocat/Spoon-Knife` with the real `git`, proving a batch gives each
  repository its own checkout, that an uncloneable repository costs only itself,
  and that two URLs for one repository, or a checkout holding a *different*
  repository, are refused. It stops at the branch step, so it never runs
  `claude`, never pushes, never opens a pull request and needs no `gh` login.
  Because it clones over the network, it identifies each checkout by the
  `owner/repository` its `origin` names rather than by the URL it was given.
- `adopt`'s `ForeignRepositoryAdoptionIT` adopts seven **real** repositories in
  one batch — `google/gson`, `square/okhttp`,
  `anthropics/anthropic-quickstarts`, `anthropics/claude-code`,
  `github/gitignore`, `JakeWharton/timber` and `modelcontextprotocol/servers` —
  chosen for the shapes they put in front of the steps that read a checkout: a
  multi-module Maven build, a Gradle build on the Kotlin DSL and another on the
  Groovy one, a real `CLAUDE.md`, a real `.claude` directory, a project whose own
  files already sit where two of the starter assets go, a default branch called
  neither `main` nor `master`, and a very large flat tree with no build file.
  Every step test drives its step over a directory this repository laid out, so
  between them these seven are the only place all three `BuildSystem`s — and both
  Gradle DSLs — meet build files nobody wrote for them. What is asserted is that
  the adoption's work is *its own and nothing else*: the pipeline runs to its end
  on each, the guard that lands is the one the checkout's build files ask for
  (the enforcer execution spliced into gson's `pom.xml`, the guard task appended
  to okhttp's `build.gradle.kts` and to timber's `build.gradle`, the workflow and
  script for the rest) and is written in that script's own DSL — a Kotlin block
  appended to a Groovy script would register the task the verification looks for
  and leave the project a build that no longer compiles — the two commits carry
  only paths `AdoptionAssets.WRITTEN_PATHS` names and the working tree is left
  clean, the guard commit's diff *removes* nothing the build file already
  declared, the default branch and the remote are untouched, and a second
  adoption of the same batch commits nothing. That nothing was published is asked
  of GitHub itself with `ls-remote`, not only of the clone's tracking refs: a ref
  in the checkout is evidence about the checkout, and the promise is about seven
  repositories belonging to other people. The default branch is read from the
  remote rather than guessed, which only a repository like `timber` — developed
  on `trunk` — can show: its adoption branch is asserted to have been cut from
  that branch. The
  run installs the starter assets too, because `AssetInstaller`'s promise that
  the project's own version always wins is about a file somebody else keeps at
  one of their paths, and only a real repository brings one: `claude-code` ships
  a `claude.yml` workflow of its own and `servers` an `.mcp.json` of its own, so
  the batch asserts each repository was given exactly the assets it lacked and
  that both of those files came through byte-identical to the blobs that were
  cloned. The starter skills land in the same run, and each is asserted to name
  the guard its own checkout got and no other build system's — the one way a
  generated file can be wrong on a repository nobody prepared. A last test reads the one document the pipeline reshapes rather than
  adds beside:
  `anthropic-quickstarts`' real `CLAUDE.md` is conformed and the real
  `claudeMdFormat` rule then accepts it, the foreign document having been
  asserted to fail it first.
  Like `MultiRepoAdoptionIT` it stops short of pushing; it also leaves out
  `claude init` and the build-toolchain and verify steps, which for a checkout
  shipping a wrapper would download that project's whole build tool. The Maven
  guard is pinned to a released `--rule-version`, since the installer refuses to
  wire a `-SNAPSHOT` into somebody else's POM.
- `claude-code-enforcer`'s `e2e` package runs **real Maven builds**, because
  everything between a pom and a rule's `execute()` is what a unit test assumes:
  artifact resolution, Sisu finding the class behind a `@Named` element, and
  Plexus binding each configuration element to a field. A rule can be correct and
  still never run, and none of that is visible from inside the rule.
  `EnforcerRuleBuildIT` builds a throwaway project and enforces
  `RuleConfiguration.complete()`, which configures **every** shipped rule with
  **every** parameter it accepts — held against the compiled classes
  (`ShippedRules`, by reflection) so a new rule or parameter no fixture addresses
  is named rather than silently unexercised — and pins the shared behaviours
  across the Maven boundary (collect-then-report, `severity=warn` going green
  while still logging, the HTML report landing on disk, a baseline suppressing
  recorded violations, and the build-setup checks that fail whatever the
  severity). `RepositoryEnforcementIT` runs this repository's own
  `mvn -N validate -DenforceClaudeMd`, holds the shipped catalogue against the
  profile (`subAgentFormat` and `commandFormat` are the documented exemptions),
  and then breaks a *copy* — a skill losing its `SKILL.md`, the two agent
  documents disagreeing about the Java version — to prove the wiring bites.
  `ForeignRepositoryEnforcementIT` points the same complete configuration at
  eight **real** repositories shallow-cloned from GitHub — `octocat/Hello-World`,
  `octocat/Spoon-Knife`, `github/gitignore`, `anthropics/claude-code`,
  `anthropics/anthropic-quickstarts`, `github/spec-kit`,
  `modelcontextprotocol/servers` and `anthropics/claude-code-action` — because
  both tests above read files written to be read by these rules, and an adopter's
  project was not. None of the eight passes and none should; what is asserted is
  that every rule *reaches a verdict* on every one of them (`EnforcerVerdicts`
  reads maven-enforcer's per-rule `passed`/`failed with message:` lines back
  out of the log, since an exit code cannot tell a rule that examined a
  project from one that never ran), that no build goes red on the rules' own
  account — an unbindable parameter, an exception escaping a rule — that every
  failure says why, that the four optional-file rules pass where the file
  really is absent, and that a rule reads a real file rather than reporting it
  absent: `commandFormat` over `anthropics/claude-code`'s `.claude/commands`,
  `subAgentFormat` and `settingsJsonValid` over `claude-code-action`'s
  `.claude/agents` and `.claude/settings.json`, and `agentsMdFormat` over
  `spec-kit`'s `AGENTS.md`. The two optional MCP rules are asked the same
  question the other way round, since for them an absent file *is* a pass:
  `servers` ships an `.mcp.json` declaring none of the servers the
  configuration requires, so a rule that read it has something to say and a
  rule that passed it over has not. One more asks git what the eight checkouts
  hold after all the builds — modified, staged, untracked and ignored alike —
  because they are somebody else's projects and the enforcing build is given
  only their path: an auto-fix rewriting a document it was asked to check, or a
  report landing beside the file it read, would show up there and nowhere else.
  A further test adopts the contract into a fresh clone and enforces it green,
  so the rules are shown satisfiable outside the repository that wrote them. The
  last takes the adopter's other route — record the backlog, gate what is added
  to it — and pins the half a fixture cannot reach: `commandFormat`'s real backlog in
  `anthropics/claude-code` (the `allowed-tools` key its commands declare,
  which the configuration does not allow) is recorded against the shared
  checkout and replayed against a *second* clone of it, at a path the
  recording never saw. A baseline is a file a project commits, so a signature
  naming the clone it was recorded under would suppress nothing anywhere else
  — the fixture's three baseline builds share one directory and cannot show
  it. Only a command added to the second clone fails that build, and
  everything the baseline accepted stays unreported. The build runs in a
  directory of its own and only the rule parameters point at a checkout
  (`RuleConfiguration.against`), so no shared clone is written to; the shared
  pom both fixtures build lives in `EnforcerPom`. Two mechanics: the `*IT`s
  run at `integration-test`, *before* this module is installed, so they
  publish the freshly packaged jar themselves (`install-file`, with the
  flattened pom); and a forked test JVM cannot work out where Maven, the local
  repository or that jar are, so the profile passes each in as an
  `enforcer.it.*` system property that `BuildEnvironment` reads.

### Coverage, mutation testing and static analysis

- **Coverage** — `mvn -Pcoverage verify` (JaCoCo) writes reports to
  `**/target/site/jacoco/` and **fails** if a bundle's instruction **or** branch
  coverage drops below **80%**. The profile also raises the per-test timeout to
  8 s, because instrumentation slows first-use class-loading.
- **Mutation testing** — `mvn -Ppitest install` (PIT + Jupiter) writes reports to
  `**/target/pit-reports/` and **fails** when a module's mutation score drops
  below its `pitest.mutationThreshold`:

  | Module | Threshold | Measured when set |
  | --- | --- | --- |
  | `code/context` | 92 | 95% |
  | `markdown-common` | 91 | 94% |
  | `adopt` | 88 | 91% |
  | `claude-code-enforcer` | 88 | 91% |
  | `code/protogen-maven-plugin` | 84 | 87% |
  | `data` | 82 | 86% |
  | `mcp-common` | 80 (the root pom's floor) | 84% |

  Each number sits a few points under the measured score, so an equivalent
  refactor — or a timed-out mutant, which PIT counts as killed — cannot turn a
  green build red on its own. **Ratchet a threshold up** once a score has settled
  above it; never down to make a red build pass. `grpc-example` and
  `code/protogen-maven-plugin-test` opt out with `pitest.skip` (beside the
  `jacoco.skip` they already set): they hold no hand-written production Java,
  only classes generated from `.proto` files, so PIT was measuring a generator's
  output rather than anybody's tests. The generator itself is mutated in
  `protogen-maven-plugin`.

  Run PIT with `install` (or any phase past `package`), not `test`: a
  `test`-only reactor build never packages `mcp-common`, so `data`'s
  `requires tools.mcp.common` — an automatic module name derived from that
  **jar**'s file name — cannot resolve against an exploded `target/classes`.
- **Static analysis** — `mvn -Pspotbugs verify -DskipTests` (SpotBugs) writes
  `target/spotbugsXml.xml` and `target/spotbugsSarif.json` per module. It runs at
  `Max` effort and a `Low` threshold, so nothing is filtered out before a human
  has read it, and it is **report-only**: the tree carries findings today, so a
  `check` goal would fail every build instead of surfacing them. `spotbugs.yml`
  publishes the SARIF to the code-scanning tab, which is where the findings are
  meant to be triaged. It publishes each module's report under its own category,
  `spotbugs/<module>/`, written into the run's `automationDetails.id` before the
  upload: every module's run names the same SpotBugs driver, and code scanning
  rejects a delivery holding two runs it cannot tell apart. The same two
  generated-code modules opt out with `spotbugs.skip`, for the same reason they
  set `jacoco.skip` and `pitest.skip`.

  A finding that has been decided about rather than fixed is excluded in the
  module's own filter file, wired in from the module pom — today only
  `data/spotbugs-exclude.xml`, which accepts
  `SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE` on the two JDBC sources that run the
  caller's query by contract. Every `Match` there names a class, a method and a
  pattern, so the detector still fires anywhere else in the module;
  `SpotBugsExcludeFilterTest` fails the build if one grows broader than that.

  Run it through `verify` rather than the bare `spotbugs:spotbugs` goal: on its
  own the goal cannot resolve the sibling `-SNAPSHOT` test-jars, which only a
  reactor run supplies.

## Continuous integration

Workflows live in `.github/workflows/`. Only `maven.yml` gates pull requests to
`main`; the rest run on a schedule, manually, or on a release — so a green PR is
not proof the whole matrix passes.

| Workflow | Trigger | What it runs |
| --- | --- | --- |
| `maven.yml` | push, PR → `main` | Installs the enforcer rule, then `mvn -B package -DenforceClaudeMd` — the **only** workflow that runs the CLAUDE.md/AGENTS.md checks. The build step is capped at **120 s**. |
| `integration-tests.yml` | daily | `mvn -P integration-tests verify`. |
| `codeql.yml` | weekly (Sat) | CodeQL security/static analysis for Java (autobuild). |
| `coverage.yml` | weekly (Sat) | `mvn verify -Pcoverage`, uploads the JaCoCo reports. |
| `spotbugs.yml` | weekly (Sun); manual | `mvn verify -Pspotbugs -DskipTests`, publishes the SARIF to the code-scanning tab and uploads the reports. Report-only — it does not gate pull requests. |
| `pitest.yml` | weekly (Sun); manual | `mvn install -Ppitest`, uploads the PIT reports. |
| `maven-windows.yml` | weekly (Sun); manual | `mvn install` on `windows-latest` — keep path, line-ending and file-locking assumptions platform-neutral. |
| `docker.yml` | weekly (Sat); on release; manual | Builds `assembly/Dockerfile` for `linux/amd64`, **runs** it against a sample CSV to prove `SampleApp` launches and logs, and scans it with Trivy (failing on fixable HIGH/CRITICAL). Only on a release does it push a `linux/amd64,linux/arm64` image to GHCR with SBOM and provenance. Deliberately not on pull requests — dispatch it by hand after touching `assembly` or the Dockerfile. |
| `packages-cleanup.yml` | monthly; manual | Prunes the GitHub Packages Maven registry, keeping the newest `min-versions-to-keep` (3 by default, overridable on a manual dispatch) versions of each published package. The package list is derived from the poms at run time, so it follows the reactor. The GHCR image is left alone on purpose — see the workflow header. |
| `maven-publish.yml` | on release | Deploys to **GitHub Packages** (`-P github-packages`). |
| `central-publish.yml` | on release; manual | Deploys to **Maven Central** (`-P release`), or a staged-only dry run on manual dispatch. |

Every workflow builds on JDK 25 (Temurin) and passes `-ntp`. Three things hold
across all eleven, and a new workflow is expected to keep them:

- **A stated `permissions:` scope.** No workflow inherits the repository's
  default `GITHUB_TOKEN` scope. Six need nothing but `contents: read`;
  `docker.yml` and `maven-publish.yml` add `packages: write` for the registry
  push, `packages-cleanup.yml` adds it on its pruning job alone (its discovery
  job reads the poms and needs `contents: read` only), `codeql.yml` adds
  `actions: read` and `security-events: write` to
  upload its results, and `spotbugs.yml` adds `security-events: write` to
  publish its SARIF.
- **A `concurrency:` group.** `maven.yml` alone sets `cancel-in-progress: true`:
  a second push to a pull request supersedes the first, and a build that caps
  itself at 120 s has no business finishing an answer nobody is waiting for.
  Everywhere else it is `false` — a scheduled run is not superseded by a newer
  commit, and a publish interrupted half-way can leave a partial release behind.
- **Actions referenced by their major tag alone** — `uses: actions/setup-java@v6`,
  never a commit SHA and never `@v6.0.0`. The major tag is the one each action's
  maintainers move, so a patch or a security fix inside an action reaches CI
  without a commit here, and the workflow says in the line itself what it runs
  rather than hiding it behind a digest. The trade is deliberate: a tag is
  mutable, so this trusts each action's owner not to re-point it — see the
  trade-off recorded in
  [ADR 0005](docs/adr/0005-renovate-dependency-updates.md).
  `aquasecurity/trivy-action` is the one action that cannot follow the rule: it
  publishes no major tag at all — every one of its 75 tags is an exact `v0.x.y`
  release — so `docker.yml` names `@v0.36.0` and Renovate bumps it like any other
  dependency.
- **`assembly/Dockerfile` is the opposite case and stays pinned by digest.** An
  image tag is re-pushed in place by whoever owns it, and those `eclipse-temurin`
  base images end up *inside* the released artifacts, which is the part of the
  supply-chain posture of
  [ADR 0002](docs/adr/0002-security-policy-and-supply-chain-posture.md) that a
  mutable base image would undo. Renovate keeps those digests fresh.

### Dependency updates

Two bots are meant to split the work ([ADR 0005](docs/adr/0005-renovate-dependency-updates.md),
[ADR 0006](docs/adr/0006-dependabot-security-updates.md)): **Renovate** owns
routine version currency, **Dependabot** owns security remediation. Only Renovate
is in force today — ADR 0006 is still `Proposed`, because Dependabot security
updates are a repository *setting* rather than a committed file and have not been
switched on; the record flips to `Accepted` when they are. Renovate's
configuration is `.github/renovate.json`:

- It runs on a **schedule** (Monday before 06:00 UTC, ≤ 5 open PRs, 2 per hour),
  so ordinary bumps arrive in one weekly batch.
- `vulnerabilityAlerts` and `osvVulnerabilityAlerts` are **off**, so Dependabot
  raises security bumps without a duplicate PR.
- Artifacts that must move together are **grouped** into one PR: the Maven
  plugins, the coverage and mutation tooling, the test libraries, and the
  protobuf toolchain — the last because `protobuf-java` and the `protoc` the
  plugin runs share one property, and letting them drift cost two minor versions
  once already.
- This project's own `io.github.adamw7:**` modules are **disabled**: they resolve
  inside the reactor at `${revision}`.
- `pinDigests` is **off** for the **github-actions** manager and **on** for
  **dockerfile**, matching the two conventions above: the workflows track major
  tags, so there is no action digest to refresh, while the Dockerfile's base
  images stay pinned and current. What Renovate still raises for actions is the
  major move — `@v7` to `@v8` — as one grouped PR, and that is the one to read
  release notes for.
- A **major** bump of the Maven API artifacts or of Spring Boot needs dependency
  dashboard approval — a Maven 4 API is wired in on purpose while the build is
  pinned to 3.9.x, and the framework the MCP servers boot on deserves a review.

Because every version lives in the root pom, these PRs are single-file changes
that run through the normal `maven.yml` build; review them like any other change.

## Agent configuration

This repository's own Claude Code configuration lives under `.claude/`, and every
part of it is validated by the rules of the next section — a change here is a
change the build checks.

### Skills

`.claude/skills/` holds **thirteen** project skills, each a directory with a
`SKILL.md` whose YAML front matter declares a `name` (lower-case kebab-case,
matching the directory name) and a `description` saying both what the skill
covers and when to load it. Skills load on demand rather than into every session,
which is why they, not `CLAUDE.md`, are where detail belongs.

| Skill | Covers |
| --- | --- |
| `data-sources` | the `data` sources, the `ColumnarDataSource` vs forward-only contract, the uniqueness/key checker |
| `context-finder` | `code/context` — the finders, the project tree and its serializers, OKF bundles, token estimation, the four MCP tools |
| `protogen` | the `protogen-maven-plugin` — proto2 required fields, proto3 presence accessors, `oneof` discriminators |
| `adopt-pipeline` | the `adopt` pipeline's ordered steps, step contract, CLI flags, credential masking |
| `mcp-server` | adding a tool or server on the `mcp-common` scaffolding — the `McpTool` SPI, the transports, path confinement, `MCP_USAGE.md`, the `*IT`s |
| `enforcer-rules` | writing, testing and wiring a `claude-code-enforcer` rule, including `severity`/`reportFile`/`baselineFile` |
| `doc-contract` | keeping `CLAUDE.md`, `AGENTS.md` and `README.md` inside the enforced documentation contract |
| `maven-conventions` | versions only in the root pom, version-free module poms, the profiles, clean-after-codegen |
| `testing-conventions` | the surefire timeouts, network-off unit tests, the ArchUnit conventions, JUnit Jupiter only |
| `java-code-review` | review led by the rules the build fails on, then the defect shapes this repository ships fixes for |
| `text-parsers` | the invariants of the readers — `MarkdownDocument`, `MarkdownText`, `ImportGraph`, `CommandTokens`, the `ClaudeMdConformer` copy, the SnakeYAML-backed `FrontMatter` — and the input that has broken each |
| `solid-principles` | the per-principle detection heuristics and the refactorings that fix them |
| `git-commit` | conventional commit messages using this repository's real module scopes |

`text-parsers` and section 9 of `java-code-review` are the bug-finding pair, and
they are written from this repository's own `fix(...)` history rather than from a
generic checklist.

A new skill needs no wiring: `skillFilesExist`, `uniqueNames` and
`uniqueDescriptions` already point at `.claude/skills`, so it is validated the
moment it lands. Its `description` must not duplicate another's — Claude routes
by matching intent against these descriptions, so one duplicate shadows the
other, and the two uniqueness rules compare skills, sub-agents and commands
together rather than each directory on its own.

`skillFilesExist` is wired with `allowedFrontMatterKeys`, as `commandFormat` is:
`name`, `description`, `allowed-tools`, `model` and `license` — the keys Claude
Code accepts, not the two used here, so a skill may grow an `allowed-tools`
without touching the pom. Without the list a mistyped `descripton:` is simply an
unread key, and a skill whose description never loads is a skill that never
loads.

### Sub-agents

`.claude/agents/` holds **two** sub-agents, each a `*.md` file whose front matter
declares a `name` matching the file name and a `description`. Both exist for the
same reason: the job reads far more than it answers, so the reading belongs in a
context that is thrown away.

| Sub-agent | Does |
| --- | --- |
| `build-verifier` | runs the right Maven command for a change — the two-phase doc check, `-pl` paired with `-am`, the single-test forms — and reports the verdict plus only the output that explains a failure |
| `doc-drift-auditor` | reads `CLAUDE.md`, `AGENTS.md`, `README.md` and the poms together and reports the facts that drifted but that no rule pins: counts, catalogue tables, quoted commands |

Neither fixes anything; both report. A `model` may be declared, and
`subAgentFormat` is wired with the aliases Claude Code accepts (`opus`,
`sonnet`, `haiku`, `inherit`), so a typo such as `claud-opus` fails the build
rather than silently falling back.

### Commands

`.claude/commands/` holds **three** slash commands. A command answers to its file
name, so that name — not a front matter `name` — must be lower-case kebab-case;
front matter itself is optional.

| Command | Runs |
| --- | --- |
| `/doc-check` | the two-phase enforcement check, with what each common failure actually means |
| `/module-build` | one module at a chosen phase, with the `-pl`/`-am` pairing and the single-test forms |
| `/new-enforcer-rule` | the end-to-end checklist for adding a rule: class, Sisu index, tests, IT fixture, pom wiring, docs |

Each covers a procedure whose steps are easy to leave out and whose omission
fails late — a rule missing from the Sisu index unit-tests green, and a doc check
run in one phase validates the *previous* rule. `commandFormat` is wired with
`allowedFrontMatterKeys`, so a mistyped `argument-hnt` is reported rather than
ignored.

### Settings and hooks

`.claude/settings.json` carries two sections:

- `permissions.allow` pre-approves the commands a session runs constantly — `mvn`
  (and `PowerShell(mvn *)` for the Windows path), the
  `dependency:tree`/`dependency:analyze` reports, the `unzip -l`/`unzip -p`
  archive inspection, and `Edit`. Each entry must be a well-formed `Tool` or
  `Tool(specifier)` and must not also appear in `deny`.
- `hooks.SessionStart` runs `$CLAUDE_PROJECT_DIR/.claude/hooks/session-start.sh`.

`.claude/hooks/session-start.sh` provisions a web/remote session and returns
immediately anywhere else: it exits at once unless `CLAUDE_CODE_REMOTE=true`,
then installs `openjdk-25-jdk` when no JDK 25 is present, exports `JAVA_HOME` and
`PATH` through `CLAUDE_ENV_FILE` so later tool calls see them, and warms the local
repository with `mvn dependency:go-offline`. Keep it `set -euo pipefail`,
executable, and opening with a `#!` shebang — `hooksFormat` requires the shebang
and the executable bit, and the root pom lints it with shellcheck.

Keep it **idempotent**, too. `SessionStart` fires on resume as well as startup,
and a resumed session can land in a re-provisioned container with no JDK, so the
hook cannot be a run-once script — it re-runs and must not accumulate. The
exports are appended only when the env file does not already carry them, and the
dependency warm is guarded by a marker inside the local repository it fills, so
it is skipped on a resume into the same container and repeated when the container
is new; a warm that fails leaves no marker and is retried rather than recorded as
done. The exports are written *before* the warm, so a hook killed by its timeout
still leaves a JDK on the `PATH`.

`hooksFormat` is wired with `reportUnreferencedScripts`, which catches the silent
direction of the wiring: a script that no hook names never runs, and nothing
about it looks wrong — it has its shebang, its executable bit and its shellcheck
pass. The other direction, a hook naming a script that is not there, is checked
by default.

Personal overrides belong in `.claude/settings.local.json`, which is gitignored;
`localSettingsIgnored` fails the build if that entry disappears.

### What this repository does not ship

Two agent-configuration files are absent by choice: `.mcp.json` (the three
servers here are *published* for other projects to configure, not consumed by
this one) and `.claude-plugin/plugin.json`. Both rules are wired and pass on the
absent file, so they start enforcing the day one is added.

`.claude/agents` and `.claude/commands` were absent for the same reason until the
definitions above were written, and their rules could not be wired before that:
a *configured* definition directory must exist. Add the directory and the rule
together, as that change did.

## CLAUDE.md enforcement

The `claude-code-enforcer` module is a set of custom `maven-enforcer-plugin`
rules that **fail the build** when the repository's agent files are missing or
malformed. They run at the **root** only, in the `claude-md-enforce` profile.
Skill: `enforcer-rules`.

### The rule catalogue

| Rule | Checks |
| --- | --- |
| `claudeMdFormat` | `CLAUDE.md` exists, is non-empty, starts with the `# CLAUDE.md` title (a leading BOM is tolerated, and the title is matched by the text it carries, so `# CLAUDE.md ##` counts), references `AGENTS.md`, and carries every required section: `## Project`, `## Java version`, `## Maven`, `## Principles for Java Development`, `## Testing`, `## Dependencies`. |
| `agentsMdFormat` | the same structural checks on `AGENTS.md`: the `# AGENTS.md` title plus `## Project overview`, `## Module layout`, `## Environment & toolchain`, `## Build, test, and run`, `## Code style & conventions`, `## Releasing`, `## Pull requests & commits`. |
| `crossDocConsistency` | each configured single-group regex captures the same value in `CLAUDE.md` and `AGENTS.md` — `Java (\d+)` pins the Java version. |
| `readmeConsistency` | the same, between `README.md` and `AGENTS.md` (`proto(\d)` pins the protobuf major version). Unlike `crossDocConsistency`, a fact the README simply does not repeat is ignored — it is allowed to document a curated subset. |
| `moduleMapConsistency` | every `<module>` of the aggregator pom (commented-out ones ignored) is mentioned in each configured doc, by its last path segment. Presence-only by design; `ignoredModules` exempts one, and a pom with no modules always fails as a build-setup mistake. |
| `contextBudget` | every configured file (and every `*.md` under configured directories) fits `maxBytes`/`maxLines`/`maxTokens`. A budget of zero is disabled; at least one must be set. The fix is moving detail into `AGENTS.md` or a skill. |
| `memoryImports` | `CLAUDE.md`'s `@path` imports resolve on disk, without cycles, no deeper than `maxDepth` (default 5, the loader's limit). Imports are recognised as Claude Code evaluates them — outside fences and code spans — so `` `@claude` `` in prose is not one. A token is a path when it is written with a `./`, `../`, `/` or `~/` prefix or ends in an `importExtensions` extension (`md`, `markdown`, `txt` by default), which is what keeps the `@anthropic-ai/claude-code` of an install line and the `@Named.class` of a Java note out of it; `@~/...` imports and `ignoredImports` are skipped. |
| `skillFilesExist` | every directory under `.claude/skills` holds a non-empty `SKILL.md` whose front matter declares every `requiredKeys` entry (`name`, `description`). The `name` is lower-case kebab-case, ≤ 64 chars, and equal to the directory name; `allowedFrontMatterKeys` also reports unknown keys, catching `descripton`, and `maxDescriptionLength` bounds the description. A key declared twice is reported, and every check reads the last declaration — the one a YAML loader keeps. |
| `subAgentFormat` | the same front-matter checks on every `*.md` under `agentsDir`; the `name` must match the file name and `allowedModels` rejects an unknown `model`. |
| `commandFormat` | every `*.md` under `commandsDir` is non-empty with a lower-case kebab-case file name (the command's name). Front matter is optional; a present `description` must be non-empty and a present `model` within `allowedModels`. |
| `uniqueNames` | no name is used twice across the configured `commandsDir`/`agentsDir`/`skillsDir` (file name for commands and sub-agents, directory name for skills). At least one directory must be configured and any configured one must exist. |
| `uniqueDescriptions` | no `description` is used by two definitions, comparing case- and whitespace-insensitively — Claude routes by description, so one duplicate shadows the other. |
| `settingsJsonValid` | `.claude/settings.json` exists and is valid JSON, and can assert `requiredPermissions`/`forbiddenPermissions` against `permissions.allow`. |
| `permissionsFormat` | each entry of `allow`/`deny`/`ask` is a non-blank `Tool` or `Tool(specifier)` — a malformed `Bash(mvn *` grants nothing and fails silently at runtime. Duplicates within a list, and an entry in both `allow` and `deny`, are reported. `allowedTools` rejects a mistyped tool (`mcp__` entries exempt) and `forbiddenEntryPatterns` bans an over-broad grant such as `Bash(*)` by shape. |
| `hookCommandsValid` | the `hooks` section's shape: every event maps to an array of groups, each with a `hooks` array whose entries declare a non-blank `type` as a JSON string (and `command`, likewise a string, for a command hook). A project-local script — `$CLAUDE_PROJECT_DIR`-rooted or plain repository-relative — must exist on disk; an argument that merely looks like a path need not, so `--out $CLAUDE_PROJECT_DIR/target/log.txt` is not reported as missing. `allowedEvents` rejects a mistyped event and `validateScriptReferences` toggles the existence check. |
| `hooksFormat` | every script under `hooksDir`, at any depth: non-empty, `#!` shebang, executable bit, `allowedExtensions`. With a `settingsFile` it also cross-checks the wiring (symlinks resolved, so a script cannot escape the directory) and `reportUnreferencedScripts` flags an unused one. An absent `hooksDir` passes; one that is there and is not a directory fails, since a rule that silently scanned nothing reads like a project with no hooks. |
| `mcpServersValid` | `.mcp.json`, when present, is valid JSON whose `mcpServers` entries are objects with a well-formed transport (`stdio` needs a `command`; `sse`/`http` need a `url`, each declared as a JSON string rather than coerced from a number or a boolean). An explicit `type` outside `allowedTypes` (`stdio`, `sse`, `http`) is rejected, an `mcpServers` that is present but is not an object is reported as that rather than as a missing one, and `requiredServers`/`forbiddenServers` assert which must or must not be declared. |
| `mcpConfigFormat` | the details `mcpServersValid` leaves: `args` an array of strings, `env`/`headers` objects of strings, a `url` declared as a string and a syntactically valid `http`/`https` one (`https` only when `requireHttps`), and no server mixing `command` with `url`. A `url` built from an environment expansion is left alone. |
| `okfBundleFormat` | an Open Knowledge Format bundle at `bundleDir` against the spec's conformance conditions: parseable frontmatter with a non-empty `type`, reserved names keeping their structure (`index.md` carries no frontmatter beyond a root `okf_version`; `log.md` groups entries under ISO 8601 headings), and closed vocabularies checked (`status`, `stale_after`, `generated`). Where the format defines no vocabulary nothing is imposed — an unregistered `type` passes. `requiredKeys` adds frontmatter keys every concept must declare, `okfVersion` pins the version the bundle root declares, and `requireIndex` (off by default) demands a listing in every directory holding concepts. The producer side is guarded separately by `code/context`'s `OkfBundleConformanceTest`, which restates the same conditions in the ordinary `test` phase. |
| `noSecrets` | the configured files and directories for literal credentials — Anthropic, AWS, GitHub and Slack token formats plus private key blocks by default; `secretPatterns` adds custom regexes, or replaces the defaults when `useDefaultPatterns` is off. Each match is reported with file, line and kind but only the first characters, so the report never republishes the secret. |
| `localSettingsIgnored` | the configured `.gitignore` covers each `ignoredPaths` entry (by default `.claude/settings.local.json`), honouring negations, anchoring, directory patterns and `*`/`?`/`**` globs. |
| `pluginFormat` | `.claude-plugin/plugin.json`, when present: valid JSON with every `requiredKeys` entry, a kebab-case `name`, a dotted `version` and a non-empty `description` — each declared as a JSON string — and `allowedKeys` reporting typos. |
| `claudeCodeProject` | all of the above, from a `projectDir` alone. It resolves each input by the conventional path Claude Code itself uses, runs only the parts whose input is present, and prefixes every violation with the part that found it. `skippedRules` switches a part off by that name, `claudeMdSections`/`claudeMdReference` pass the document contract through, `claudeMdBudgetBytes` sizes `CLAUDE.md` (32 KB by default, zero to skip), and `autoFix` reaches the document parts. `crossDocConsistency` and `readmeConsistency` are deliberately not included: they take the patterns a particular project needs kept in step, and no convention supplies those. |

Rules whose target is optional (`mcpServersValid`, `mcpConfigFormat`,
`okfBundleFormat`, `pluginFormat`, `noSecrets`, `hooksFormat`) pass on the absent
file and start enforcing the moment one appears. A *configured* definition
directory, by contrast, must exist, so `skillFilesExist`, `subAgentFormat` and
`commandFormat` can only be wired once `.claude/skills`, `.claude/agents` and
`.claude/commands` are there — every rule this module ships is wired here today,
and `RepositoryEnforcementIT` fails if one stops being.

### What every rule shares

Every rule extends `ClaudeCodeEnforcerRule`, which reports all violations
together (never stopping at the first). A file it cannot decode as UTF-8, and a
directory it cannot walk, fail as a verdict naming the file rather than as an
`UncheckedIOException` that would abort the build as an internal error. It also
offers:

- **`severity`** — `error` (default, fails the build) or `warn` (logs the same
  violations), so a new rule can be adopted gradually. Anything else is refused as
  a build-setup mistake: read as the default, a `<severity>warning</severity>`
  failed the build while the author who wrote it believed the rule had been
  downgraded, which is the one misconfiguration whose symptom is
  indistinguishable from the rule working.
- **`reportFile`** — a self-contained HTML report of what failed and why plus
  per-rule "How to fix" steps, written on pass and fail alike so it always
  reflects the latest run.
- **`baselineFile`** — the violations a rule already accepts, so it can become an
  error gate without first clearing the backlog: a listed violation is suppressed
  and only a new one fails. Record them once with
  `<writeBaseline>true</writeBaseline>` (or `-Dclaude.enforcer.writeBaseline=true`),
  then commit the file. Each signature normalises the project base directory to
  `${basedir}`, so configure `<baseDir>${project.basedir}</baseDir>` alongside it:
  Maven runs every module from wherever it was invoked, so without it the token
  falls back to the working directory and a baseline recorded from the root
  suppresses nothing when the build starts elsewhere.
- **Build-wide defaults for all three** — `-Dclaude.enforcer.severity`,
  `-Dclaude.enforcer.reportDir` and `-Dclaude.enforcer.baselineDir`. They are the
  three parameters that are the same answer for every rule a project wires, and a
  full catalogue is around twenty of them; spelled per rule that is sixty elements
  to keep in step. A parameter configured on the rule itself still wins, so a
  build can downgrade the catalogue and insist on one rule. A directory names each
  rule's file after the rule — two rules sharing one report would overwrite each
  other's verdict, and one shared baseline would let a violation accepted for one
  suppress an identical message from another — and the reports written into one
  gain an `index.html` linking them.
- **Asking to record a baseline with no `baselineFile`** is refused rather than
  ignored. It used to read as "no baseline, so check normally", which told the
  operator the build failed on the very violations they had just asked to accept.
- **A debug trace of what each rule was pointed at** — `mvn -X` prints one line
  per rule naming its configured input files and how many violations survived the
  baseline, plus the scan counts (`Skills: checking 13 definition(s) in …`) and
  the accepted absences (`mcp.json is absent at …; nothing to check`).
  maven-enforcer's own verdict names only the class, so a rule that read a
  document and one that passed because it was pointed at nothing read alike. Log
  through the base class's `log()`, never `getLog()`: the enforcer injects a
  logger and nothing else does, so `getLog()` is null wherever a rule is built
  directly and `log()` falls back to a silent one.
- **A `severity=warn` violation says it was tolerated** — the warning carries the
  rule and its inputs and states that the build was not failed, so it is not read
  as one more `[WARNING]` worded exactly like the failure it would have been.

`claudeMdFormat` and `agentsMdFormat` share a `MarkdownFormatRule` base doing the
existence, BOM, title and section checks, with optional `forbiddenTokens`,
`enforceSectionOrder`, `maxLineLength` and `validateFileReferences` (local links
must resolve, read both as written and percent-decoded). The title, the required
sections and (for `claudeMdFormat`) the companion document to reference are all
overridable, so a project whose documents are not this one's keeps the structural
checking on its own headings; `<requiredReference/>` left empty drops the
companion check for a project that keeps none.

They also take `autoFix` (off by default), which repairs the structure it would
otherwise only report: a missing title, a heading that is a near miss for a
required one, an absent or empty section, and a missing companion reference are
mechanical edits with one correct answer. The reshape is
`markdown-common`'s `MarkdownConformer`, working to the same `MarkdownContract`
the rule then checks against and reading the document through the same
`MarkdownDocument` — which is also what the adoption pipeline's conformer uses, so
the repair and the check cannot come to disagree. Only the structure is repaired:
a forbidden token, an over-long line and a broken file reference are still
reported, because what a link ought to point at is the author's to say.

The front-matter rules (`skillFilesExist`, `subAgentFormat`, `commandFormat`)
share a `DefinitionFormatRule` base owning the directory requirement, the scan
and the grouped report; a subclass says which entries carry a definition and
which checks it wants. They also accept `autoFix` (off by default): when a
delimiter is written with too many dashes (`----`) or an opening `---` has no
closer, the rule rewrites the file in place and continues against the corrected
content. The repair only acts when the document opens with a dashes line
enclosing real `key: value` entries, so a lone `---` thematic break is never
mistaken for front matter.

Each input file is read and parsed once per build, whatever asks for it:
`CLAUDE.md` is read by five rules and `settings.json` by four, and sharing the
result costs them none of their independence — what is shared is the file's
content, not any rule's reading of it. An entry is keyed by the path with its
modification time and size, so a file `autoFix` rewrote mid-build misses rather
than serving what it held before the fix.

### Running the rules without Maven

A maven-enforcer rule has to be resolvable as a JAR before the build that uses it
runs, which is the two-phase bootstrap below. That is the right cost for a check
that gates a build and the wrong one for a pre-commit hook, a project built with
Gradle or with nothing, or anyone wanting to know what the rules make of a
repository before wiring anything. The jar is therefore also a command line,
running the same `claudeCodeProject` composite a pom would configure:

```bash
java -cp tools.claude-code-enforcer.jar:enforcer-api.jar \
     io.github.adamw7.tools.enforcer.cli.Main . --fix --skip okfBundleFormat
```

`enforcer-api` is on the classpath because it is a `provided` dependency of the
rule jar — Maven supplies it in the wiring that matters, and a standalone run has
to bring it. Every option is a parameter of that rule (`--skip`, `--fix`,
`--warn`, `--budget`, `--report`, `--debug`), so the command line and a pom
configure one thing rather than two that could drift; an unrecognised option is
refused rather than ignored. Failure is reported by throwing, as everywhere else
here, so the process exits non-zero without the class ending a JVM it does not
own.

The command line is analysed apart from the shared coding conventions, because
one of them cannot hold there: an entry point invoked as `java -jar` has no host
process to report through, and giving this module a logging framework to satisfy
the rule would put one on the plugin class path of every build that wires a rule.
`CommonCodingConventions` is explicit that such a rule must be exempted visibly
rather than relaxed for everyone, so `..enforcer.cli` carries its own
`CommandLineArchitectureTest` restating what does apply — including that only
`Main` reaches a stream.

### Two things that will bite you

**Front matter is composed, not loaded.** `FrontMatter` delimits the `---` block
itself and hands it to **SnakeYAML**, stopping at `Yaml.compose` — the node tree,
not constructed Java objects. That keeps `okf_version: 0.20` the string `0.20`
instead of rounding it to a double, and keeps a key declared twice visible, which
every loader that builds a `Map` collapses. Each value is folded onto one line,
so a block scalar, a wrapped plain scalar and a nested mapping all read back as
text. A block no loader can read is no front matter at all and `parse` answers
empty, which is what the rules report best. This replaced a hand-rolled reader
that had been fixed for real input six or seven times — see the `text-parsers`
skill before touching it.

**Naming a helper after a list parameter breaks the build.** Plexus infers a
configured list's element type from the **child element name**, trying the rule's
own package first, so a class whose name matches the capitalised child name —
`SecretPattern` for `<secretPatterns><secretPattern>` — is chosen over `String`
and the build fails trying to instantiate it. The parameter then works in a unit
test, which calls the setter directly, and not in the builds it exists for. Keep
a helper's name clear of the singular of any list parameter in its package (hence
`CredentialPattern`); `EnforcerRuleBuildIT` catches a reintroduction.

### Running the check

It is **opt-in**: the `claude-md-enforce` profile activates only on
`-DenforceClaudeMd`, so every other build is unaffected and needs no bootstrap.
Only `.github/workflows/maven.yml` opts in.

A maven-enforcer rule must be a JAR resolvable from a repository before the build
runs, and Maven resolves plugin dependencies from repositories rather than the
reactor, so the rule cannot be produced and consumed in one build. Use a
**two-phase build**:

```bash
mvn -pl claude-code-enforcer -am install   # 1. publish the rule locally
mvn -N validate -DenforceClaudeMd          # 2. quick root-only doc check
```

The module's pom is flattened (flatten-maven-plugin) so the installed pom has no
unresolved `${revision}` and is resolvable as a plugin dependency.

## Code style & conventions

Hard requirements for any code you add or modify:

- **SOLID principles** for all code (skill: `solid-principles`).
- **Clean code**: short methods, meaningful parameter names.
- **No `continue` or `break`** statements.
- **Write unit tests for all new logic** — behavior, edge cases, error paths.
- **Match the surrounding code** — naming, comment density, and idiom.

### Maven conventions

Skill: `maven-conventions`.

- All dependency **versions and scopes** are declared only in the root `pom.xml`
  under `<dependencyManagement>`; all **plugin versions** only under
  `<pluginManagement>`. Module poms reference both without versions. The one
  thing a module may say about a managed dependency is a *narrower* scope, where
  two modules genuinely want different ones: `enforcer-api` is managed
  `provided` and narrowed to `test` by `adopt`, and `jsoup` is managed unscoped —
  `adopt` parses every `pom.xml` it edits — and narrowed to `test` by
  `claude-code-enforcer`, which only reads HTML back in its own tests.
- **Do not add a new dependency without asking first.**
- A family of artifacts that must move together shares one property rather than a
  version each: `protobuf.version` (the runtime *and* the `protoc` the plugin
  runs), `grpc.version`, `derby.version`, `log4j2.version`, `maven.api.version`.
  `derby.version` and `log4j2.version` are Spring Boot's own property names on
  purpose — the BOM manages the rest of each family from them, so pinning only
  the artifacts declared here would leave the siblings on Boot's older version.
- The root pom's parent is `spring-boot-starter-parent`, so its
  `<dependencyManagement>` and `<pluginManagement>` reach every module. Two of
  its entries are deliberately neutralised in the root pom rather than lived
  with: the `generate` execution it binds on `protobuf-maven-plugin` (for its
  gRPC starter) would look for a `src/main/proto` the plugin module has none of,
  and the `protoc-gen-grpc-java` it adds to that plugin's `<plugins>` would reach
  the modules that generate plain protobuf. `grpc-example` names the gRPC
  generator in its own execution instead. The parent also decides which JUnit
  generation the tests run on — Boot 4 brings **JUnit 6** (Jupiter), which is why
  `data-test` needs a slightly larger heap than it did under Boot 3.
- A module that is an example, a test harness or a distribution opts out of
  publication with `maven.deploy.skip` (GitHub Packages) and
  `central.skipPublishing` (Maven Central) — not by redeclaring the `release`
  profile.
- The root `enforce` execution runs `requireProfileIdsExist`, so a mistyped `-P`
  fails the build instead of silently running without the profile. It is
  satisfied when *any* project in the reactor declares the id, which is what lets
  the per-module `integration-tests` profile be requested from the root.

#### Java module names

Every **published** jar states its JPMS module name, so a consumer on the module
path gets a name that belongs to the artifact rather than one the JVM guessed
from the filename — a guess that changes with the artifactId or the version
scheme and takes the consumer's `requires` clause down with it.

Two ways to state it, and the module descriptor wins where a module has one:

| Module | Name | Stated in |
|---|---|---|
| `data` | `io.github.adamw7.tools.data` | `module-info.java` |
| `data-test` | `io.github.adamw7.tools.data.test` | `module-info.java` |
| `markdown-common` | `tools.markdown.common` | jar manifest |
| `claude-code-enforcer` | `tools.claude.code.enforcer` | jar manifest |
| `mcp-common` | `tools.mcp.common` | jar manifest |
| `code/context` | `tools.code.context` | jar manifest |
| `code/protogen-maven-plugin` | `tools.protogen.maven.plugin` | jar manifest |
| `adopt` | `tools.adopt` | jar manifest |

A module without a `module-info.java` configures `maven-jar-plugin` with an
`Automatic-Module-Name` manifest entry — three lines, and it locks the name in
without the far larger change a full descriptor is:

```xml
<plugin>
	<groupId>org.apache.maven.plugins</groupId>
	<artifactId>maven-jar-plugin</artifactId>
	<configuration>
		<archive>
			<manifestEntries>
				<Automatic-Module-Name>tools.markdown.common</Automatic-Module-Name>
			</manifestEntries>
		</archive>
	</configuration>
</plugin>
```

The name is the one the filename already produced — the artifactId with every
non-alphanumeric run collapsed to a dot — so pinning it breaks no consumer who
was already reading the guess. `protogen-maven-plugin` is the one exception: its
artifactId carries no `tools.` prefix, and a Maven plugin is resolved by
coordinates and loaded by Maven's own classloader, never named in a `requires`,
so it takes the `tools.` name the rest of the repository uses.

**A new published module picks this up too.** `grpc-example`, `assembly` and the
`*-test` harnesses are not published (`central.skipPublishing`) and need nothing.
Spring Boot's `repackage` preserves the entry, so `code/context` and `adopt` keep
their name in the executable jar as well as the plain one.

#### Reproducible builds

Setting `project.build.outputTimestamp` makes every archive-producing plugin
stamp one fixed instant and write entries in a stable order, so two builds of a
commit come out byte-identical — which is what lets a consumer rebuild a Maven
Central release and diff it against the published jars, the supply-chain posture
of [ADR 0002](docs/adr/0002-security-policy-and-supply-chain-posture.md).

The property is **not declared in the pom**: a literal there is a date somebody
has to remember to bump, and releases here are a `revision` edit rather than a
`maven-release-plugin` run. The publishing workflows derive it from the released
commit and pass it as a user property instead:

```bash
mvn ... -Dproject.build.outputTimestamp="$(git log -1 --format=%cI)"
```

Pinning it to the commit rather than the clock is the point: anyone can check out
the tag, run the same command, and get the published bytes. An ordinary
`mvn install` passes nothing and is not reproducible, which costs nothing —
those artifacts are never published.

Verify a change has not broken reproducibility by building twice and comparing:

```bash
stamp="$(git log -1 --format=%cI)"
mvn -B clean package -DskipTests -Dproject.build.outputTimestamp="$stamp"
find . -path "*/target/*.jar" -not -path "*/target/classes/*" | sort | xargs sha256sum > /tmp/build1.sha
mvn -B clean package -DskipTests -Dproject.build.outputTimestamp="$stamp"
find . -path "*/target/*.jar" -not -path "*/target/classes/*" | sort | xargs sha256sum | diff /tmp/build1.sha -
```

`mvn artifact:check-buildplan` answers the related question — whether every
plugin in the build plan supports reproducible builds — and needs this repo's own
`protogen-maven-plugin` installed first.

## Releasing

To release version `X`:

1. Change the `revision` property in the root `pom.xml` to `X` (it is normally a
   `-SNAPSHOT`, e.g. `2.5.0-SNAPSHOT`).
2. Commit and push.
3. Confirm all builds pass.
4. Release and mark as latest in GitHub.

Nothing about the reproducible-build timestamp is a manual step: both publishing
workflows derive `project.build.outputTimestamp` from the released commit,
failing the release outright if the commit timestamp cannot be read.

Creating the GitHub release fires two workflows:

- `maven-publish.yml` deploys to **GitHub Packages** (`mvn deploy -P
  github-packages`, using the `distributionManagement` repository). The profile
  attaches the javadoc jar, which a default `mvn deploy` would not.
- `central-publish.yml` deploys to **Maven Central** via the Sonatype Central
  Portal (`mvn -P release deploy`). The `release` profile attaches the sources
  and javadoc jars, GPG-signs every artifact, and hands the bundle to the
  `central-publishing-maven-plugin` (`autoPublish=true`).

Central publishing is **opt-in** through the `release` profile, and the plugin is
bound to the `deploy` phase, so ordinary and CI builds never publish and never
need GPG keys or Central credentials. The release job requires four repository
secrets: `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD` (a Central Portal
user token), plus `MAVEN_GPG_PRIVATE_KEY` and `MAVEN_GPG_PASSPHRASE`.

Every reactor module is published to Central except `assembly`, `grpc-example`,
`protogen-maven-plugin-test` and `test-common`, each of which sets
`<central.skipPublishing>true</central.skipPublishing>` in its own
`<properties>`. That drives the plugin's `skipPublishing` flag per module, so the
exclusion holds with or without the workflow's `-pl` filter. (The
`protogen-maven-plugin-test` harness has no main sources, so it would also fail
Central validation with an empty `-sources.jar`.) The same modules set
`maven.deploy.skip` to stay out of GitHub Packages.

Nothing has to be cleaned up by hand afterwards: `packages-cleanup.yml` sweeps
the GitHub Packages Maven registry monthly and keeps the newest three versions
of each package. Maven Central keeps every released version permanently, so a
pruned GitHub Packages version is a second copy going away, never the release
itself.

To publish from a workstation:
`mvn -P release deploy -Dproject.build.outputTimestamp="$(git log -1 --format=%cI)"`
with the `central` server credentials in `~/.m2/settings.xml` and a GPG key on
the keyring. A hand-run deploy that omits the property still publishes, but the
artifacts will not be reproducible.

### Staged-only dry run (validate without releasing)

`central-publish.yml` accepts a manual `workflow_dispatch` that signs and uploads
the bundle so the Central Portal validates it, but overrides the plugin with
`-Dcentral.autoPublish=false -Dcentral.waitUntil=validated`, leaving the
deployment staged. Drop or publish it manually in the portal. Central rejects
`-SNAPSHOT` versions, so run it from a commit whose `revision` is a real version
or supply a non-`SNAPSHOT` `revision` input. Locally the same check is
`mvn -P release deploy -Dcentral.autoPublish=false -Dcentral.waitUntil=validated`.

## Containers & Kubernetes

`assembly/Dockerfile` is the one Dockerfile, used for the released image and for
the Kubernetes Job under `k8s/`. It packages the distribution the `assembly`
module builds — a launcher jar whose manifest carries `Main-Class` and a
`lib/`-prefixed `Class-Path`, next to a `lib/` of intact dependency jars — and
runs it with `java -jar`. Before changing any of it:

- **Build it from the repository root**, not from `assembly/`: the context must
  include `docker/log4j2-console.properties` as well as `assembly/target/`.
  `.dockerignore` excludes everything and re-admits only those two paths.
- **The distribution is deliberately not a `jar-with-dependencies`.** Merging
  every dependency into one archive collapses same-named metadata, and both
  `log4j-core` and `spring-boot` ship a `Log4j2Plugins.dat`; the survivor cost
  log4j2 its plugin registry and dropped it to `DefaultConfiguration` at level
  `ERROR`, so the app ran and logged nothing. See `assembly/src/assembly/bin.xml`.
- **`data` attaches its Spring Boot jar under the `boot` classifier** so its main
  artifact stays an ordinary library jar. Repackaging in place gave every
  consumer the nested `BOOT-INF/` layout and broke `Main-Class` resolution.
- **Ownership is set by `COPY --chown`, never a later `RUN chown`**, which would
  rewrite the whole distribution into a second layer and store it twice.
- **A JDK build stage strips the DuckDB driver's macOS and Windows natives**
  (~100 MB each) before the runtime stage copies `lib/`. Both Linux
  architectures stay, because the release publishes `linux/amd64` and
  `linux/arm64`.
- **`USER` is the numeric `10001:10001`**, because Kubernetes' `runAsNonRoot`
  admission check cannot verify a name-based user.
- **The runtime stage deletes `/usr/bin/pebble`** (and `/var/lib/pebble`). The
  base of `eclipse-temurin:25-jre` ships Canonical's Pebble service manager
  there; this image never runs it, and as a static Go binary outside dpkg's
  control the Go CVEs it vendors cannot be patched by an upgrade and fail Trivy
  on their own. Expect the same of any future unused binary the base adds.
- **Logging goes through `docker/log4j2-console.properties`**, selected with
  `-Dlog4j2.configurationFile` in `JDK_JAVA_OPTIONS`. The `data` module's own
  config is file-only on purpose: its MCP server speaks JSON-RPC over stdio, and
  a stdout appender there would corrupt the protocol stream.

`k8s/` runs `SampleApp` (the CSV column-uniqueness checker) on a local minikube
cluster as a run-to-completion **Job**, not a Deployment:
`configmap-sample-data.yaml` mounts a sample CSV, `job-uniqueness-check.yaml`
runs the check, `kustomization.yaml` bundles both for `kubectl apply -k k8s/`,
and `run-on-minikube.sh` / `.ps1` drive the whole flow (build → image → minikube
→ load → apply → logs). The Job meets the **restricted** Pod Security Standard
(non-root numeric UID `10001`, read-only root filesystem, all capabilities
dropped, `RuntimeDefault` seccomp, no service-account token, an `emptyDir`
`/tmp`, an `activeDeadlineSeconds` bound), which is why the Dockerfile declares a
numeric `USER`. Pick the column with the `COLUMN` env var (Linux/macOS) or
`-Column` parameter (Windows). See [k8s/README.md](k8s/README.md).

## Security

- Report vulnerabilities privately by email to the address in
  [SECURITY.md](SECURITY.md); do not open a public issue for them.
- [SECURITY.md](SECURITY.md) also lists which released versions receive security
  fixes (only the latest line).
- `codeql.yml` runs CodeQL analysis weekly and `spotbugs.yml` runs SpotBugs
  weekly (neither gates pull requests); both publish to the code-scanning tab.
  Keep new code free of the issues they flag.

## Pull requests & commits

- Use clear, descriptive, conventional commit messages (skill: `git-commit`).
- Keep changes focused; add or update tests alongside the code.
- Do **not** open a pull request unless explicitly asked.
