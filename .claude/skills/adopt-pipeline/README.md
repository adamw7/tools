# Adopt Pipeline

**Load**: `view .claude/skills/adopt-pipeline/SKILL.md`

---

## Description

Helps Claude work on the `adopt` module: the ordered adoption steps and what each
one does, the `AdoptionStep` contract, the ArchUnit rules a new step must satisfy
(no process spawning outside `command`, immutable state, credential masking), the
CLI flags, and how batch runs isolate failures.

---

## Use Cases

- "Add a step that installs a pre-commit hook during adoption"
- "Why did the adoption stop at the verify step?"
- "Run the adoption without pushing" (dry run)
- "Adopt these five repositories in one run"

---

## Examples

```
> view .claude/skills/adopt-pipeline/SKILL.md
> "Add a step that copies a CODEOWNERS file into the checkout"
→ new *Step in .step, immutable fields, shell out via CommandRunner only,
  register in GitHubRepoAdopter.defaultSteps, unit-test with a fake runner
```

---

## Notes / Tips

- A dry run omits `PushStep` and `PullRequestStep` outright — it does not run
  them in a no-op mode.
- Only `CloneStep` may read the credentialled clone URL; everything else uses
  `displayUrl()`.
- One failing repository must never abort the rest of a batch.
