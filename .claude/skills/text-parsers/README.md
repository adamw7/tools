# Text Parsers

**Load**: `view .claude/skills/text-parsers/SKILL.md`

---

## Description

Carries the invariants of this repo's hand-rolled text readers —
`MarkdownDocument`, `FrontMatter`, `MarkdownText`, `ImportGraph`, `CommandTokens`
and the `ClaudeMdConformer` copy of them — together with the adversarial input
that has broken each one, so a change starts from that list instead of
rediscovering it.

---

## Use Cases

- "The enforcer fails a document that looks fine"
- "Add a rule that reads headings / front matter / imports"
- "Change how a hook command is split"
- "Keep the conformer in step with the rule it must satisfy"

---

## Examples

```
> view .claude/skills/text-parsers/SKILL.md
> "Why does memoryImports follow an import inside an HTML comment?"
→ Comment mask, isInsideComment, and the three rules that share it
```

---

## Notes / Tips

- Change the shared reader, not the caller — a local re-derivation is how these
  drifted before.
- A second copy of a format is only safe with a contract test running the real
  rule over its output.
