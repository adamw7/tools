# Context Finder

**Load**: `view .claude/skills/context-finder/SKILL.md`

---

## Description

Helps Claude use the `code/context` module: the `Context` contract, the
name-based `Finder` vs the `PackageAwareFinder`, `ProjectSources` loading,
`BudgetedContext` with a `TokenEstimator`, the project-tree builder and its
serializers, OKF bundles, and the four MCP tools over them.

---

## Use Cases

- "Which classes does `KeyFinder.java` depend on, two levels deep?"
- "Render this project as a Mermaid dependency graph"
- "Fit this context into an 8k budget"
- "Add Scala support / a new serializer"

---

## Examples

```
> view .claude/skills/context-finder/SKILL.md
> "Assemble the context for KeyFinder within a 5000-token budget"
→ ProjectSources.load(root) → new BudgetedContext(new Finder(containers),
  new SubwordTokenEstimator(), 5000).find(root, depth)
```

---

## Notes / Tips

- Use `PackageAwareFinder` when simple names collide; `Finder` is faster when
  they don't.
- Budgeting relies on breadth-first order — don't re-sort the result before
  trimming.
- The core stays free of the `mcp` package; ArchUnit fails the build otherwise.
