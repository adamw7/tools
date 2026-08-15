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
  a forward-only source (JSON/YAML/TOON) that would only answer `null`. Also a
  uniqueness checker (does a subset of columns form a key, and is there a smaller
  one), open-addressing collections (`OpenAddressingMap`, `OpenAddressingSet`,
  the primitive `IntKeyOpenAddressingMap`), and an **MCP server** exposing the
  uniqueness checker. Skill: `data-sources`.
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
project's build and commit → optionally commit starter assets → verify the guard
passes → push → open a pull request with `gh pr create`.

What is worth knowing before changing any of it:

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
- **A reused checkout is confirmed to be the repository under adoption** by
  comparing what its `origin` names with the URL given, read with `git config
  --get-all remote.origin.url` rather than `git remote get-url`, which expands
  `url.<base>.insteadOf` rewrites and so answers a host the checkout never
  recorded. Two URLs name one repository when they agree once scheme,
  credentials, `.git` suffix, trailing slash and case are set aside; anything
  else, including a checkout with no `origin`, is refused. The checkout is then
  refreshed with `git fetch`, since `BranchStep` starts the feature branch from
  the remote-tracking refs.
- **One run adopts a list of repositories** — repeatable `--repo <url>`, `--repos
  <file>` (one URL per line, `#` comments and blank lines skipped), or
  `repository_urls` on the MCP tool (a JSON array or a comma-separated string).
  Duplicates are dropped, `--workspace`/`--branch` name the shared workspace and
  branch, and the first positional is always a repository URL, never a workspace.
  Each repository gets its own checkout (claimed inside its own adoption) and its
  own report, and one that fails does not stop the rest; `Main` raises the
  failures together afterwards so the process still exits non-zero.
- **`--dry-run`** assembles the pipeline *without* `PushStep` and
  `PullRequestStep` rather than with steps that decide to do nothing, and asks
  the toolchain check only for the `git` and `claude` a rehearsal really runs. A
  run therefore needs no GitHub credentials at all, and `completedSteps` ends at
  `verify`.
- **Credentials never outlive the run.** Every log line, failure message and
  report field goes through `Redaction`, which masks the user information of a
  URL carrying a scheme, so a CI runner's
  `https://x-access-token:TOKEN@github.com/...` never reaches disk or an MCP
  client. `git` is still handed the URL as given. Both `gh` invocations name the
  repository with `--repo` rather than letting `gh` infer it from the remote.
- **Configuration is one object.** `AdoptionOptions` (wrapping
  `PullRequestOptions`) carries the pull-request metadata, the starter assets,
  the rule version, the dry-run flag and the per-command timeout, and both entry
  points hand it to the same pipeline factory, so the CLI and the MCP tool cannot
  drift. `CliArguments` declares the command line to **picocli**, binding the
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
  project gets an always-on declaration of its own and keeps the one it had.
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
- **Everything shells out through a `CommandRunner`**, so steps are unit-tested
  without spawning processes; `ProcessCommandRunner` bounds every command with a
  timeout, ten minutes by default and overridable with `--timeout <minutes>`
  (`timeout_minutes`, bounded to a day since the MCP server is long-lived).
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
  Boot, documentation as an enforced contract). A record is immutable once
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
├── mcp-common                  # shared MCP server scaffolding (transport wiring, tool SPI)
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
(`--transport.mode=stateless-http`, session-less, also `/mcp`). Each has an
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

**Shellcheck.** The root pom lints `scripts/**/*.sh` with
`dev.dimlight:shellcheck-maven-plugin`, configured with
`<binaryResolutionMethod>embedded</binaryResolutionMethod>`: the `shellcheck`
binary rides in as an ordinary Maven Central artifact inside the plugin jar, so
nothing is fetched from GitHub and no `shellcheck` needs to be installed — the
lint works offline and in networks that cannot reach GitHub (including Claude
Code web/remote sessions). The plugin's default method downloads the binary from
GitHub releases; where that host is blocked the build fails on the root pom with
`Input is not in the XZ format` before any Java module is reached. Skip the lint
with `-Dskip.shellcheck=true`.

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
  exceptions; no Joda Time; packages free of cycles.
- `CommonNamingConventions` — abstract types carry an `Abstract` prefix. Kept
  separate because `claude-code-enforcer` is exempt: its abstract rule bases are
  public API that poms configure by name.
- `CommonTestConventions` — every `@Testable` method lives in a `*Test`/`*IT`
  class; no `@Disabled`; JUnit 5 only; no `System.out`/`err`; no `Thread.sleep`.

Adding a repository-wide convention means editing one library, not six tests; an
exemption means a module not importing a library, which stays visible.

Per-module rules:

- **`data`** — data-source contracts in `source.interfaces` must not depend on
  their `source.db`/`source.file` implementations; the uniqueness core must not
  depend on its MCP adapter; the `structure` collections stay decoupled from data
  sources; JDBC (`java.sql`) stays confined to `source.db`.
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

- `data` and `code/context` exercise their MCP servers over streamable HTTP.
- `adopt`'s `MultiRepoAdoptionIT` clones GitHub's `octocat/Hello-World` and
  `octocat/Spoon-Knife` with the real `git`, proving a batch gives each
  repository its own checkout, that an uncloneable repository costs only itself,
  and that two URLs for one repository, or a checkout holding a *different*
  repository, are refused. It stops at the branch step, so it never runs
  `claude`, never pushes, never opens a pull request and needs no `gh` login.
  Because it clones over the network, it identifies each checkout by the
  `owner/repository` its `origin` names rather than by the URL it was given.
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
  five **real** repositories shallow-cloned from GitHub — `octocat/Hello-World`,
  `octocat/Spoon-Knife`, `github/gitignore`, `anthropics/claude-code` and
  `anthropics/anthropic-quickstarts` — because both tests above read files
  written to be read by these rules, and an adopter's project was not. None of
  the five passes and none should; what is asserted is that every rule *reaches a
  verdict* on every one of them (`EnforcerVerdicts` reads maven-enforcer's
  per-rule `passed`/`failed with message:` lines back out of the log, since an
  exit code cannot tell a rule that examined a project from one that never ran),
  that no build goes red on the rules' own account — an unbindable parameter, an
  exception escaping a rule — that every failure says why, that the four
  optional-file rules pass where the file really is absent, and that
  `commandFormat` reads `anthropics/claude-code`'s real `.claude/commands`
  instead of reporting it absent. A sixth test adopts the contract into a fresh
  clone and enforces it green, so the rules are shown satisfiable outside the
  repository that wrote them. The build runs in a directory of its own and only
  the rule parameters point at a checkout (`RuleConfiguration.against`), so no
  clone is written to; the shared pom both fixtures build lives in `EnforcerPom`.
  Two mechanics: the `*IT`s run at `integration-test`, *before* this module is
  installed, so they publish the freshly packaged jar themselves (`install-file`,
  with the flattened pom); and a forked test JVM cannot work out where Maven, the
  local repository or that jar are, so the profile passes each in as an
  `enforcer.it.*` system property that `BuildEnvironment` reads.

### Coverage and mutation testing

- **Coverage** — `mvn -Pcoverage verify` (JaCoCo) writes reports to
  `**/target/site/jacoco/` and **fails** if a bundle's instruction **or** branch
  coverage drops below **80%**. The profile also raises the per-test timeout to
  8 s, because instrumentation slows first-use class-loading.
- **Mutation testing** — `mvn -Ppitest install` (PIT + JUnit 5) writes reports to
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
| `pitest.yml` | weekly (Sun); manual | `mvn install -Ppitest`, uploads the PIT reports. |
| `maven-windows.yml` | weekly (Sun); manual | `mvn install` on `windows-latest` — keep path, line-ending and file-locking assumptions platform-neutral. |
| `docker.yml` | weekly (Sat); on release; manual | Builds `assembly/Dockerfile` for `linux/amd64`, **runs** it against a sample CSV to prove `SampleApp` launches and logs, and scans it with Trivy (failing on fixable HIGH/CRITICAL). Only on a release does it push a `linux/amd64,linux/arm64` image to GHCR with SBOM and provenance. Deliberately not on pull requests — dispatch it by hand after touching `assembly` or the Dockerfile. |
| `maven-publish.yml` | on release | Deploys to **GitHub Packages** (`-P github-packages`). |
| `central-publish.yml` | on release; manual | Deploys to **Maven Central** (`-P release`), or a staged-only dry run on manual dispatch. |

Every workflow builds on JDK 25 (Temurin) and passes `-ntp`.

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
| `testing-conventions` | the surefire timeouts, network-off unit tests, the ArchUnit conventions, JUnit 5 only |
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
other.

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

Personal overrides belong in `.claude/settings.local.json`, which is gitignored;
`localSettingsIgnored` fails the build if that entry disappears.

### What this repository does not ship

Four agent-configuration files are absent by choice: `.mcp.json` (the three
servers here are *published* for other projects to configure, not consumed by
this one), `.claude-plugin/plugin.json`, `.claude/agents` and `.claude/commands`.
The first two rules are wired and pass on the absent file, so they start
enforcing the day one is added; `subAgentFormat` and `commandFormat` cannot be
wired until their directory exists — add the directory and the rule together.

## CLAUDE.md enforcement

The `claude-code-enforcer` module is a set of custom `maven-enforcer-plugin`
rules that **fail the build** when the repository's agent files are missing or
malformed. They run at the **root** only, in the `claude-md-enforce` profile.
Skill: `enforcer-rules`.

### The rule catalogue

| Rule | Checks |
| --- | --- |
| `claudeMdFormat` | `CLAUDE.md` exists, is non-empty, starts with the `# CLAUDE.md` title (a leading BOM is tolerated), references `AGENTS.md`, and carries every required section: `## Project`, `## Java version`, `## Maven`, `## Principles for Java Development`, `## Testing`, `## Dependencies`. |
| `agentsMdFormat` | the same structural checks on `AGENTS.md`: the `# AGENTS.md` title plus `## Project overview`, `## Module layout`, `## Environment & toolchain`, `## Build, test, and run`, `## Code style & conventions`, `## Releasing`, `## Pull requests & commits`. |
| `crossDocConsistency` | each configured single-group regex captures the same value in `CLAUDE.md` and `AGENTS.md` — `Java (\d+)` pins the Java version. |
| `readmeConsistency` | the same, between `README.md` and `AGENTS.md` (`proto(\d)` pins the protobuf major version). Unlike `crossDocConsistency`, a fact the README simply does not repeat is ignored — it is allowed to document a curated subset. |
| `moduleMapConsistency` | every `<module>` of the aggregator pom (commented-out ones ignored) is mentioned in each configured doc, by its last path segment. Presence-only by design; `ignoredModules` exempts one, and a pom with no modules always fails as a build-setup mistake. |
| `contextBudget` | every configured file (and every `*.md` under configured directories) fits `maxBytes`/`maxLines`/`maxTokens`. A budget of zero is disabled; at least one must be set. The fix is moving detail into `AGENTS.md` or a skill. |
| `memoryImports` | `CLAUDE.md`'s `@path` imports resolve on disk, without cycles, no deeper than `maxDepth` (default 5, the loader's limit). Imports are recognised as Claude Code evaluates them — outside fences and code spans — so `` `@claude` `` in prose is not one; `@~/...` imports and `ignoredImports` are skipped. |
| `skillFilesExist` | every directory under `.claude/skills` holds a non-empty `SKILL.md` whose front matter declares every `requiredKeys` entry (`name`, `description`). The `name` is lower-case kebab-case, ≤ 64 chars, and equal to the directory name; `allowedFrontMatterKeys` also reports unknown keys, catching `descripton`, and `maxDescriptionLength` bounds the description. A key declared twice is reported, and every check reads the last declaration — the one a YAML loader keeps. |
| `subAgentFormat` | the same front-matter checks on every `*.md` under `agentsDir`; the `name` must match the file name and `allowedModels` rejects an unknown `model`. |
| `commandFormat` | every `*.md` under `commandsDir` is non-empty with a lower-case kebab-case file name (the command's name). Front matter is optional; a present `description` must be non-empty and a present `model` within `allowedModels`. |
| `uniqueNames` | no name is used twice across the configured `commandsDir`/`agentsDir`/`skillsDir` (file name for commands and sub-agents, directory name for skills). At least one directory must be configured and any configured one must exist. |
| `uniqueDescriptions` | no `description` is used by two definitions, comparing case- and whitespace-insensitively — Claude routes by description, so one duplicate shadows the other. |
| `settingsJsonValid` | `.claude/settings.json` exists and is valid JSON, and can assert `requiredPermissions`/`forbiddenPermissions` against `permissions.allow`. |
| `permissionsFormat` | each entry of `allow`/`deny`/`ask` is a non-blank `Tool` or `Tool(specifier)` — a malformed `Bash(mvn *` grants nothing and fails silently at runtime. Duplicates within a list, and an entry in both `allow` and `deny`, are reported. `allowedTools` rejects a mistyped tool (`mcp__` entries exempt) and `forbiddenEntryPatterns` bans an over-broad grant such as `Bash(*)` by shape. |
| `hookCommandsValid` | the `hooks` section's shape: every event maps to an array of groups, each with a `hooks` array whose entries declare a non-blank `type` (and `command` for a command hook). A project-local script — `$CLAUDE_PROJECT_DIR`-rooted or plain repository-relative — must exist on disk; an argument that merely looks like a path need not, so `--out $CLAUDE_PROJECT_DIR/target/log.txt` is not reported as missing. `allowedEvents` rejects a mistyped event and `validateScriptReferences` toggles the existence check. |
| `hooksFormat` | the scripts under `hooksDir`: non-empty, `#!` shebang, executable bit, `allowedExtensions`. With a `settingsFile` it also cross-checks the wiring (symlinks resolved, so a script cannot escape the directory) and `reportUnreferencedScripts` flags an unused one. An absent `hooksDir` passes. |
| `mcpServersValid` | `.mcp.json`, when present, is valid JSON whose `mcpServers` entries are objects with a well-formed transport (`stdio` needs a `command`; `sse`/`http` need a `url`). An explicit `type` outside `allowedTypes` (`stdio`, `sse`, `http`) is rejected, and `requiredServers`/`forbiddenServers` assert which must or must not be declared. |
| `mcpConfigFormat` | the details `mcpServersValid` leaves: `args` an array of strings, `env`/`headers` objects of strings, a syntactically valid `http`/`https` `url` (`https` only when `requireHttps`), and no server mixing `command` with `url`. A `url` built from an environment expansion is left alone. |
| `okfBundleFormat` | an Open Knowledge Format bundle at `bundleDir` against the spec's conformance conditions: parseable frontmatter with a non-empty `type`, reserved names keeping their structure (`index.md` carries no frontmatter beyond a root `okf_version`; `log.md` groups entries under ISO 8601 headings), and closed vocabularies checked (`status`, `stale_after`, `generated`). Where the format defines no vocabulary nothing is imposed — an unregistered `type` passes. `requiredKeys` adds frontmatter keys every concept must declare, `okfVersion` pins the version the bundle root declares, and `requireIndex` (off by default) demands a listing in every directory holding concepts. The producer side is guarded separately by `code/context`'s `OkfBundleConformanceTest`, which restates the same conditions in the ordinary `test` phase. |
| `noSecrets` | the configured files and directories for literal credentials — Anthropic, AWS, GitHub and Slack token formats plus private key blocks by default; `secretPatterns` adds custom regexes, or replaces the defaults when `useDefaultPatterns` is off. Each match is reported with file, line and kind but only the first characters, so the report never republishes the secret. |
| `localSettingsIgnored` | the configured `.gitignore` covers each `ignoredPaths` entry (by default `.claude/settings.local.json`), honouring negations, anchoring, directory patterns and `*`/`?`/`**` globs. |
| `pluginFormat` | `.claude-plugin/plugin.json`, when present: valid JSON with every `requiredKeys` entry, a kebab-case `name`, a dotted `version`, a non-empty `description`, and `allowedKeys` reporting typos. |

Rules whose target is optional (`mcpServersValid`, `mcpConfigFormat`,
`okfBundleFormat`, `pluginFormat`, `noSecrets`, `hooksFormat`) pass on the absent
file and start enforcing the moment one appears. A *configured* definition
directory, by contrast, must exist — which is why `subAgentFormat` and
`commandFormat` stay unwired here.

### What every rule shares

Every rule extends `ClaudeCodeEnforcerRule`, which reports all violations
together (never stopping at the first) and offers:

- **`severity`** — `error` (default, fails the build) or `warn` (logs the same
  violations), so a new rule can be adopted gradually.
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
must resolve, read both as written and percent-decoded).

The front-matter rules (`skillFilesExist`, `subAgentFormat`, `commandFormat`)
share a `DefinitionFormatRule` base owning the directory requirement, the scan
and the grouped report; a subclass says which entries carry a definition and
which checks it wants. They also accept `autoFix` (off by default): when a
delimiter is written with too many dashes (`----`) or an opening `---` has no
closer, the rule rewrites the file in place and continues against the corrected
content. The repair only acts when the document opens with a dashes line
enclosing real `key: value` entries, so a lone `---` thematic break is never
mistaken for front matter.

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
- A module that is an example, a test harness or a distribution opts out of
  publication with `maven.deploy.skip` (GitHub Packages) and
  `central.skipPublishing` (Maven Central) — not by redeclaring the `release`
  profile.
- The root `enforce` execution runs `requireProfileIdsExist`, so a mistyped `-P`
  fails the build instead of silently running without the profile. It is
  satisfied when *any* project in the reactor declares the id, which is what lets
  the per-module `integration-tests` profile be requested from the root.

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
- `codeql.yml` runs CodeQL analysis weekly (it does not gate pull requests); keep
  new code free of the issues it flags.

## Pull requests & commits

- Use clear, descriptive, conventional commit messages (skill: `git-commit`).
- Keep changes focused; add or update tests alongside the code.
- Do **not** open a pull request unless explicitly asked.
