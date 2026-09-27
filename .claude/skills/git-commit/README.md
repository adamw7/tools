# Git Commit Messages

**Load**: `view .claude/skills/git-commit/SKILL.md`

---

## Description

Helps Claude write clear, concise, and conventional Git commit messages for this
repository, scoped by its real modules.

---

## Use Cases

- "Commit staged changes"
- "Create commit for bug fix #123"
- "Generate conventional commit message"

---

## Examples

```
> view .claude/skills/git-commit/SKILL.md
> "Commit these changes"
→ fix(source.db): close the statement when a read stops early
```

---

## Notes / Tips

- Best used after staging the changes, one module or concern per commit
