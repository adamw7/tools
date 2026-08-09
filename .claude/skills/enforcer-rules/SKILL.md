---
name: enforcer-rules
description: Write, test and wire a claude-code-enforcer rule — the custom maven-enforcer rules that fail the build when CLAUDE.md, AGENTS.md, README.md or the .claude configuration is malformed. Use when adding or changing a rule, when configuring severity/reportFile/baselineFile, or when the user says "enforcer rule", "the doc check fails", or "wire a rule into the pom".
---

# Enforcer Rules Skill

Add or change a rule in `claude-code-enforcer`, the module of custom
`maven-enforcer-plugin` rules that guard this repository's agent-facing files.
Every rule follows the same shape, so getting the shape right is most of the
work.

## The two-phase build (there is no shortcut)
A maven-enforcer rule must be resolvable as a JAR *before* the build that uses
it runs, so a change to a rule needs two commands:

```bash
mvn -pl claude-code-enforcer -am install   # 1. publish the rule locally
mvn -N validate -DenforceClaudeMd          # 2. quick root-only doc check
```

Skipping step 1 runs the *previous* rule against your new expectations. The
check is opt-in (`claude-md-enforce` profile, activated by `-DenforceClaudeMd`),
so ordinary builds are unaffected; `.github/workflows/maven.yml` is the only
workflow that opts in.

## Anatomy of a rule

```java
@Named("myNewRule")                                  // the pom's <myNewRule> element
public class MyNewRule extends ClaudeCodeEnforcerRule {

    private File someFile;                           // injected from the configuration

    @Override
    public void execute() throws EnforcerRuleException {
        requireDocument(someFile, "someFile");       // build-setup checks first
        String content = requireContent(someFile, "someFile");
        List<String> violations = new ArrayList<>();
        // ... collect every problem, never stop at the first ...
        report("What went wrong, in one line:", violations);
    }

    void setSomeFile(File someFile) {                // package-private setter per parameter
        this.someFile = someFile;
    }
}
```

Rules that check a property across *every* definition (skills, sub-agents,
commands) extend `MultiDefinitionRule` instead and call `forEachDefinition(...)`
— it already owns `commandsDir` / `agentsDir` / `skillsDir` traversal, where a
skill is a directory holding a `SKILL.md`.

Rules that validate *one* kind of definition file by file extend
`DefinitionFormatRule`, which owns the directory scan, the `autoFix` parameter
and the grouped report. A subclass supplies the `Naming` its messages use, the
directory, `entriesIn(...)` (the `*.md` files, or the subdirectories for skills),
and `collectEntryViolations(...)` — reaching for `contentOf(...)` and
`frontMatterOf(...)` / `requiredFrontMatterOf(...)` rather than reading and
parsing again.

### What the base class gives you (don't reimplement it)
| Member | Use it for |
|---|---|
| `report(header, violations)` | The single exit point: baseline filtering, HTML report, warn-vs-fail |
| `requireConfigured` / `requireExists` / `requireDocument` | Missing parameter or file — always fails, whatever the severity |
| `requireContent` | Read a required file and fail when blank |
| `howToFix()` | Override to give the HTML report rule-specific remediation steps |

Shared configuration every rule inherits: `severity` (`error` default, `warn`
logs only), `reportFile` (self-contained HTML), `baselineFile` +
`writeBaseline` (`-Dclaude.enforcer.writeBaseline=true` records current
violations so a new rule can be gated without clearing the backlog first), and
`baseDir` for the baseline's `${basedir}` token.

## Rules the module's own ArchUnit tests pin
- **Layering is one-directional**: `text` depends on nothing; `rule` may use
  `text`; the feature packages (`definition`, `doc`, `mcp`, `secret`,
  `settings`) may use `rule` and `text` — **never each other**. Shared logic
  goes down into `text` or `rule`, not sideways.
- Every concrete `*Rule` must extend `ClaudeCodeEnforcerRule`.
- Packages stay free of cycles, plus the repo-wide `CommonCodingConventions`.

## Collect, then report
Report *all* violations together, not the first one. A rule that throws mid-scan
makes a contributor fix one problem per build. Build a `List<String>` and hand it
to `report(...)`.

**One violation is one line.** A baseline stores one accepted violation per line,
so a message spanning several could never be matched again: it was suppressed by
nothing and reported as stale for ever. Rules build their own messages on one
line, but they quote back text they did not write — the entry `permissionsFormat`
echoes is any string the settings file declared — so `Baseline` folds every line
break out of a live violation and a recorded entry alike. Interpolating
untrusted text is fine; emitting a deliberate `\n` in a message is not.

## Testing a rule
- Tests live beside the rule (`…/enforcer/<package>/MyNewRuleTest.java`) and lay
  out fixtures in a `@TempDir` with the `TestFiles` helpers
  (`writeString`, `writeBytes`, `createDirectory`) — they wrap `IOException`
  unchecked so a fixture reads as one expression.
- Assert on the *message*, not just the throw: check
  `EnforcerRuleException#getMessage()` names the offending file and value.
- `CapturingLogger` lets a test assert what `severity=warn` logged instead of
  threw.
- Cover: passes when correct, fails per violation kind, passes on an empty
  directory, and the always-fails build-setup cases (missing parameter/file).

**A unit test cannot prove the rule runs.** It calls the setters itself, so it
skips artifact resolution, `@Named` discovery, and Plexus binding — the ways a
correct rule never runs at all. The `*IT`s in `…/enforcer/e2e` run real Maven
builds and cover that seam; extend them alongside a new rule:
`RuleConfiguration.complete()` must configure it with **every** parameter it
accepts (that fixture is checked against the compiled classes, so a rule or
parameter left out fails `EnforcerRuleBuildIT`), and `RepositoryEnforcementIT`
compares the shipped catalogue against the root pom's profile. Run them with
`mvn -pl claude-code-enforcer -am -P integration-tests verify`.

**Naming trap for a `List<String>` parameter.** Plexus infers a configured
list's element type from the *child element name*, trying the rule's own package
first, so a class matching the capitalised child name — `SecretPattern` for
`<secretPatterns><secretPattern>` — wins over `String` and the build fails
trying to instantiate it. Keep helper names clear of the singular of any list
parameter in the same package (hence `CredentialPattern`).

## Wiring it into the root pom
Add the `@Named` element under the `claude-md-enforce` profile's `<rules>` block
in the root `pom.xml`, with one child per parameter:

```xml
<myNewRule>
    <someFile>${project.basedir}/CLAUDE.md</someFile>
</myNewRule>
```

Then document it in the `## CLAUDE.md enforcement` sections of `CLAUDE.md` and
`AGENTS.md` — that list is the rule catalogue contributors read.

**A rule whose definition directory must exist can only be wired once the
directory does.** `subAgentFormat` (`.claude/agents`) and `commandFormat`
(`.claude/commands`) ship but are deliberately unwired here, because a
configured-but-absent directory is treated as a build-setup mistake. Add the
directory and the wiring in the same change. `pluginFormat`, `mcpServersValid`
and `mcpConfigFormat` differ — they pass on the absent file and start enforcing
the moment one appears. `okfBundleFormat` follows that second pattern for a
directory: an absent `bundleDir` is a pass, so it is wired here today against a
bundle this repository does not yet ship.

**A new rule must also be listed in
`src/main/resources/META-INF/sisu/javax.inject.Named`.** The index is
hand-maintained; a rule missing from it compiles, unit-tests green, and then
fails a real build with "Failed to create enforcer rules with name: …". The
`*IT`s are what catch it.

## References
- `AGENTS.md` — *CLAUDE.md enforcement* (source of truth, full rule catalogue)
- `claude-code-enforcer/.../rule/ClaudeCodeEnforcerRule.java` — the base contract
- `claude-code-enforcer/.../architecture/EnforcerArchitectureTest.java` — layering
- `claude-code-enforcer/.../e2e/EnforcerRuleBuildIT.java` — the pom-to-rule seam
- Root `pom.xml` — the `claude-md-enforce` profile
