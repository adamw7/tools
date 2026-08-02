# SOLID Principles

**Load**: `view .claude/skills/solid-principles/SKILL.md`
**Worked examples**: `view .claude/skills/solid-principles/examples.md`

---

## Description

SOLID checklist grounded in this repository: detection heuristics and the
refactoring that fixes each violation, with the real examples from `data`,
`adopt`, `code/context` and `mcp-common`. Full before/after code lives in
`examples.md`, loaded only when a worked example is needed.

---

## Use Cases

- "Check this class for SOLID violations"
- "Is this class doing too much?" (SRP)
- "How do I add new types without modifying code?" (OCP)
- "Why shouldn't Square extend Rectangle?" (LSP)
- "This interface is too big" (ISP)
- "How to make this testable?" (DIP)

---

## Examples

```
> view .claude/skills/solid-principles/SKILL.md
> "Review this UserService for SOLID principles"
→ Identifies the SRP violation, suggests extracting validation and notification
```

---

## Principles Covered

| Principle | Key Question |
|-----------|--------------|
| **S**ingle Responsibility | Does it have one reason to change? |
| **O**pen/Closed | Can I extend without modifying? |
| **L**iskov Substitution | Can subtypes replace base types? |
| **I**nterface Segregation | Are clients forced to implement unused methods? |
| **D**ependency Inversion | Does it depend on abstractions? |

---

## Notes / Tips

- `ColumnarDataSource` vs `IterableDataSource` is this repo's ISP in one line —
  don't widen a contract to silence a compile error.
- A field must never be `Optional` here (ArchUnit); as a return type it's fine.
- Wiring is plain constructor injection — no Spring or CDI outside the MCP
  adapters.

---

## Related Skills

- `java-code-review` — the full review checklist, led by the enforced rules
- `testing-conventions` — the network-off tests these abstractions enable
- `data-sources` — the schema contract used as the ISP example

---

## Resources

- [SOLID (Wikipedia)](https://en.wikipedia.org/wiki/SOLID)
- [Clean Code by Robert C. Martin](https://www.oreilly.com/library/view/clean-code-a/9780136083238/)
- [SOLID Principles in Java (Baeldung)](https://www.baeldung.com/solid-principles)
