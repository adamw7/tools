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
> "Review the changes in src/main/java/org/example/UserService.java"
→ Returns findings grouped by severity (Critical → Minor)
```

---

## Checklist Categories

0. **Enforced repo rules** - what ArchUnit and Surefire fail the build on
1. **Null Safety** - NPE risks, Optional usage
2. **Exception Handling** - Swallowed exceptions, stack traces
3. **Collections & Streams** - Iteration, mutability
4. **Concurrency** - Thread safety, race conditions
5. **Java Idioms** - equals/hashCode, builders
6. **Resource Management** - try-with-resources
7. **API Design** - Boolean params, validation
8. **Performance** - String concat, N+1 queries
9. **Defect shapes** - hand-rolled readers, drifting duplicate implementations,
   command transcripts, success reported for work never done, credential paths

---

## Notes / Tips

- Section 9 is the high-yield pass here; sections 1–8 are the generic checks
- Works best on focused changes (single class or PR)
- Includes positive feedback section for good practices
- Suggests tests for edge cases found during review
