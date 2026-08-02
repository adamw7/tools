# Enforcer Rules

**Load**: `view .claude/skills/enforcer-rules/SKILL.md`

---

## Description

Helps Claude write, test and wire a `claude-code-enforcer` rule: the base class
contract, the one-directional package layering, the two-phase build a
maven-enforcer rule needs, and the `severity` / `reportFile` / `baselineFile`
options every rule inherits.

---

## Use Cases

- "Add a rule that checks X in CLAUDE.md"
- "Why does `-DenforceClaudeMd` fail?"
- "Gate this new rule without fixing the backlog first"
- "Wire the sub-agent rule into the build"

---

## Examples

```
> view .claude/skills/enforcer-rules/SKILL.md
> "Add an enforcer rule that fails when a skill has no README.md"
→ new *Rule extends ClaudeCodeEnforcerRule in .definition, collect violations,
  report(...), test with TestFiles + @TempDir, wire under claude-md-enforce
```

---

## Notes / Tips

- Always `mvn -pl claude-code-enforcer -am install` before re-running the check —
  otherwise the old rule JAR is what runs.
- Collect every violation and report them together; never throw on the first.
- Feature packages may not depend on each other — push shared logic into `rule`
  or `text`.
