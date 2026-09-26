# Java Code Review

**Load**: `view .claude/skills/java-code-review/SKILL.md`

---

## Description

Systematic code review for this repo. Leads with the rules the build fails on, then the five defect shapes this repository actually ships fixes for, then the general Java checks: null safety, exception handling, collections, concurrency, idioms, resource management, API design, and performance.

---

## Use Cases

- "Review this class"
- "Check this PR for issues"
- "Code review the changes in PluginManager"
- "What's wrong with this code?"

---

## Examples

```
> view .claude/skills/java-code-review/SKILL.md
> "Review the changes in data/src/main/java/io/github/adamw7/tools/data/source/file/CSVDataSource.java"
→ Returns findings grouped by severity (Critical → Minor)
```

---

## Checklist Categories

1. **Repo rules the build enforces** - what ArchUnit and Surefire fail the build on
2. **Defect shapes this repo ships** - hand-rolled readers, drifting duplicate
   implementations, command transcripts, success reported for work never done,
   credential paths
3. **General Java checks** - null safety, exception handling, collections and
   streams, concurrency, idioms, resource management, API design, performance

---

## Notes / Tips

- Section 9 is the high-yield pass here; sections 1–8 are the generic checks
- Works best on focused changes (single class or PR)
- Includes positive feedback section for good practices
- Suggests tests for edge cases found during review
