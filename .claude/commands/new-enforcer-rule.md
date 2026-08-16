---
description: Add a claude-code-enforcer rule end to end — class, Sisu index, tests, IT fixture, pom wiring and docs.
argument-hint: <ruleName> [what it should check]
---

Add the enforcer rule described by `$ARGUMENTS`. Load the `enforcer-rules` skill
first; it carries the detail this checklist only names.

A rule is not done when it compiles and its unit test is green — a rule can be
flawless and still guard nothing, because the ways it silently never runs all lie
outside the class. Work through every step:

1. **The class** — `claude-code-enforcer/src/main/java/.../enforcer/<package>/`,
   `@Named("<ruleName>")`, extending `ClaudeCodeEnforcerRule`, or
   `DefinitionFormatRule` / `MultiDefinitionRule` when it validates definitions.
   One package-private setter per parameter. Collect every violation into a
   `List<String>` and hand it to `report(...)` — never throw at the first, and
   never emit a `\n` inside one violation, which no baseline could match again.
2. **The Sisu index** — add the class to
   `src/main/resources/META-INF/sisu/javax.inject.Named`. It is hand-maintained: a
   rule missing from it compiles, unit-tests green, then fails a real build with
   "Failed to create enforcer rules with name".
3. **Layering** — `text` depends on nothing, `rule` may use `text`, and the
   feature packages may use both but never each other. Shared logic goes down,
   not sideways; `EnforcerArchitectureTest` fails if it goes sideways.
4. **Naming** — keep any helper class clear of the singular of a list parameter
   in the same package. Plexus infers a list's element type from the child element
   name and would instantiate the helper instead of `String`, which fails only in
   a real build.
5. **Unit tests** beside the rule, fixtures in a `@TempDir` via `TestFiles`.
   Assert on the message, not just the throw. Cover: passes when correct, fails
   once per violation kind, passes on an empty directory, and the build-setup
   cases that always fail. Use `CapturingLogger` for what `severity=warn` logged.
6. **The IT fixture** — `RuleConfiguration.complete()` must configure the rule
   with **every** parameter it accepts; `EnforcerRuleBuildIT` checks that block
   against the compiled classes and fails if a rule or parameter is missing.
7. **Survive a repository nobody prepared** — `ForeignRepositoryEnforcementIT`
   points the same configuration at five real clones. The rule must reach a
   verdict on a file it did not expect and a directory that is not there, and
   report both rather than throwing.
8. **Wire it** into the root pom's `claude-md-enforce` profile, one child element
   per parameter. A rule taking a definition directory can only be wired once that
   directory exists — add the directory and the wiring in the same change.
   `RepositoryEnforcementIT` compares the shipped catalogue against the profile,
   so an unwired rule needs a documented exemption there.
9. **Document it** — a row in the rule catalogue of `AGENTS.md` and a mention in
   the `## CLAUDE.md enforcement` list of `CLAUDE.md`. That catalogue is what
   contributors read.

Then verify, in this order:

```bash
mvn -pl claude-code-enforcer -am install
mvn -N validate -DenforceClaudeMd
mvn -pl claude-code-enforcer -am -P integration-tests verify
```
