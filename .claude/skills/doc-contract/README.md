# Doc Contract

**Load**: `view .claude/skills/doc-contract/SKILL.md`

---

## Description

Helps Claude keep `CLAUDE.md`, `AGENTS.md`, `README.md` and the `.claude`
configuration inside the contract the enforcer rules validate: required
headings, the 32 KB context budget, the module map, the facts pinned across
documents, and the skill front-matter conventions.

---

## Use Cases

- "Add a new module to the reactor" (three files must change together)
- "Bump the project to Java 26"
- "`-DenforceClaudeMd` fails with moduleMapConsistency"
- "Document this feature — where does it go?"

---

## Examples

```
> view .claude/skills/doc-contract/SKILL.md
> "I added a `graph` module — the doc check is red"
→ mention it in the CLAUDE.md module map AND AGENTS.md "Module layout",
  then mvn -pl claude-code-enforcer -am install && mvn -N validate -DenforceClaudeMd
```

---

## Notes / Tips

- AGENTS.md wins over CLAUDE.md; CLAUDE.md is a budgeted summary.
- A required heading with an empty body fails just like a missing one.
- Skill directory name must equal the front-matter `name`, and descriptions must
  be unique across all skills.
