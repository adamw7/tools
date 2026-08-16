---
description: Run the two-phase CLAUDE.md enforcement check and explain any rule that failed.
allowed-tools: Bash(mvn *), Read, Edit, Grep, Glob
---

Run this repository's documentation contract check the way
`.github/workflows/maven.yml` runs it, and act on the result.

Both commands, in this order — the first publishes the rule, the second uses it:

```bash
mvn -pl claude-code-enforcer -am install
mvn -N validate -DenforceClaudeMd
```

Running only the second checks the new documents with the *previously installed*
rules, which is how a rule change appears to pass when it does not. If you
changed nothing under `claude-code-enforcer`, the first command is still cheap
insurance against a stale local artifact.

Then:

- **Everything passed** — say so, and name how many rules reported a pass. A rule
  that never ran also never fails, so the count is the evidence.
- **A rule failed** — read the violation lines rather than the summary. Each one
  names the file and the value it objected to. Fix the *document*, not the rule:
  these rules encode a contract that was agreed before the document was written,
  and a rule bent to fit a document guards nothing afterwards. If the rule really
  is wrong, say so explicitly and stop, rather than editing it in passing.
- **The build failed before the rules ran** — a missing JDK 25, a Maven outside
  3.9.x, or an unresolved `tools.claude-code-enforcer` artifact. That is a
  toolchain problem; report it as such, because the documents were not checked.

Two failures are worth naming precisely, because their message points away from
their cause:

- `contextBudget` on `CLAUDE.md` means the file outgrew 32 KB. The fix is moving
  detail into `AGENTS.md` or a skill, never trimming the required sections.
- `moduleMapConsistency` after adding a module means the module is not mentioned
  in both `CLAUDE.md` and `AGENTS.md` — add it to the module map in each.

Load the `doc-contract` skill before editing any of the three documents, and the
`enforcer-rules` skill before touching a rule.
