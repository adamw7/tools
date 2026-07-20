# Testing Conventions

**Load**: `view .claude/skills/testing-conventions/SKILL.md`

---

## Description

Helps Claude write tests that satisfy the `tools` repo's enforced testing rules:
the 900 ms Surefire per-test timeout, network-off unit tests, ArchUnit test
conventions, and JUnit 5 only.

---

## Use Cases

- "Write a unit test for this class"
- "Why is this test timing out?"
- "This test needs the network — where does it go?"

---

## Examples

```
> view .claude/skills/testing-conventions/SKILL.md
> "Add a test for the uniqueness checker"
→ Fast *Test in the right package, no network, under 900 ms
```

---

## Notes / Tips

- Anything touching the network is an `*IT`, not a unit test.
