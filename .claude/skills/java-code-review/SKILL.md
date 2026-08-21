---
name: java-code-review
description: Systematic Java code review for the tools repo — leads with the ArchUnit-enforced rules the build fails on, then the five defect shapes this repository actually ships fixes for, then null safety, exceptions, concurrency, and performance. Use when the user says "review code", "check this PR", "code review", or before merging changes.
---

# Java Code Review Skill

Systematic code review for the `tools` multi-module reactor. Start with the rules
this repo *enforces at build time* — an ArchUnit or surefire violation fails the
build, not just review — then the defect shapes this repository's own history
shows, then the general Java checks.

## Review strategy

1. **Enforced rules** (section 1) — anything there fails the build, so flag it as
   Critical regardless of how the code otherwise reads.
2. **Defect shapes** (section 2) — the five shapes this repo actually ships fixes
   for. Highest yield after section 1; a generic checklist misses all five.
3. **General checks** (section 3) — null safety, exceptions, collections,
   concurrency, idioms, resources, API, performance.
4. **Summary** — findings by severity, with line references.

Focus on the changed lines (`git diff`), group similar findings, reference line
numbers rather than re-quoting blocks, and skip generated sources (protogen
output under `target/`) and fixtures.

```markdown
## Code Review: [file/feature name]

### Critical
- [Issue + line reference + suggestion]

### Improvements
- [Suggestion + rationale]

### Minor/Style
- [Nitpicks, optional improvements]

### Good Practices Observed
- [Positive feedback]
```

| Severity | Criteria |
|---|---|
| **Critical** | Breaks an enforced repo rule, security/data-loss risk, or a likely crash |
| **High** | Bug likely, significant perf issue, breaks an API contract |
| **Medium** | Code smell, maintainability issue, missing best practice |
| **Low** | Style, minor optimization |

---

## 1. Repo rules the build enforces (check these FIRST)

Pinned by the module `.architecture` ArchUnit tests and the root surefire config.
Production code is `io.github.adamw7.*` (Java 25). Flag any violation as
**Critical** — CI will reject it.

| Rule | What to flag |
|---|---|
| **No `continue` / `break`** | Any use in *any* code. Prefer a single-exit loop or a helper method that returns early. |
| **Logging via log4j2 only** | `System.out`/`err`, `java.lang.System.Logger`, `printStackTrace`, `System.exit` — in tests too. |
| **Loggers `private static final`** | Any other modifier set on a `Logger` field. |
| **No `Optional` fields** | A field typed `Optional<…>`. Optional is for possibly-absent *return values*. |
| **Mutable static state is `volatile`** | A non-final mutable static field without `volatile`. |
| **`java.time` only** | Any use of legacy `java.util.Date` / `Calendar`. |
| **Abstract types prefixed `Abstract`, public fields `final`** | Except `claude-code-enforcer`, whose rule bases are named for the poms. |
| **No package cycles / layering breaks** | Data-source contracts depending on their impls, uniqueness core depending on its MCP adapter, JDBC outside `source.db`, a step spawning a process outside `command`. |
| **Test conventions** | Tests only in `*Test`/`*IT`; JUnit Jupiter only; no `@Disabled`; no `Thread.sleep`; no `System.out`/`err`. See `testing-conventions`. |
| **Surefire 5 s/unit test** | A unit test doing real work without a justified `@Timeout`. |

---

## 2. The defect shapes this repo actually ships

Of the last 26 `fix(...)` commits here, **almost none** would have been caught by
a generic Java checklist. These five shapes would have been. Review for them
explicitly.

### 2.1 A hand-rolled reader meeting real input

The largest class by a distance — `FrontMatter`, `CommandTokens`,
`MarkdownDocument`, `ImportGraph` and `MarkdownFormatRule` account for most of the
fix commits here. Every one was input the reader was not written for: a comment
opened after text on its line, an apostrophe read as an opening quote, a newline
inside a hook command, `[logo](assets/logo(1).png)`, a bare `@claude` mention read
as a memory import.

The strongest fix for this shape is not a better hand-rolled reader but no
hand-rolled reader: `FrontMatter` now composes its block with SnakeYAML, and the
quoting, comment and block-scalar rules that had cost it six or seven fix commits
came with it.

**Ask:**
- Does this track state across the whole line, or does it `startsWith` /
  `contains` and hope? A delimiter can appear anywhere, and twice.
- Does the regex stop at the *first* closing delimiter when the content can nest?
- Is markup matched *after* `withoutCodeSpans` and the fence/comment masks?
- Does an unterminated construct at end of file leave the mask in a sane state?

**Load the `text-parsers` skill** before changing any of these — it carries each
reader's invariants and the adversarial-input checklist.

### 2.2 Two implementations of one format, drifting

`ClaudeMdConformer` used to duplicate `claudeMdFormat`'s reading of a Markdown
document, because `adopt` must not ship the maven-enforcer API. #536 was four
defects from that copy drifting: the conformer produced a document the rule it
exists to satisfy rejects, so the adoption failed its own `VerifyStep` on a file
it had just reshaped to pass. #557 is the same shape one level up — detection
asked whether a pom *depended on* the enforcer artifact rather than whether it
*configures the rule*.

The reading now lives once, in `markdown-common`, which both modules depend on.
That is the fix this defect shape usually wants: when two implementations must
agree, look for the third place they could both depend on before reaching for a
test that compares them. What survives as a copy is the *required sections* list
— data, guarded by `ClaudeMdConformerContractTest`.

**Ask:**
- Is there a second implementation of this format, constant, or predicate
  anywhere in the reactor? Search before assuming not.
- Is there a test that runs the **real** other side over this side's output — not
  a copy of what the other side is believed to want? See
  `ClaudeMdConformerContractTest`.
- Does the check test the *thing it cares about*, or a proxy that correlates?

### 2.3 Reading a command transcript

The runner merges stderr into the transcript. A `git` that warns about an
unreadable system config puts that line in with the `--porcelain` entries, and
#539 read its fourth character onwards as a changed path — refusing a resume the
step promises. That was the *second* time: the identical defect had already been
fixed for the origin query beside it.

**Ask:**
- Can this output carry stderr noise? Skip lines that are not well-formed entries
  rather than reading them as one — while still letting a real entry beside the
  warning take effect.
- Is captured output bounded? Is the exit code checked, or only the text?

### 2.4 Success reported for work that never happened

The most expensive shape, because nothing downstream catches it. `git add -A`
skips an ignored path in silence, so a file the adoption wrote stayed in the
working tree — where `VerifyStep` went on finding it and passing — while the
pushed branch carried nothing of it. `okfBundleFormat` returned early for an
absent bundle and never reached `report()`, leaving a previous failing run's HTML
on disk.

**Ask:**
- Does the verification read the same state the operation wrote — the commit, not
  the working tree; the remote, not the local branch?
- Does **every** exit path, including the early and empty ones, reach the
  reporting call?
- Can this succeed by doing nothing? Does a test assert the input was rejected
  *before* the operation, so a no-op cannot pass it?

### 2.5 A new path for a credential

Three separate commits: a token in a rejected-positional message, in a `gh`
transcript logged raw, in an MCP argument log. A `gh` without `--repo` reads the
remote through git, which echoes a credentialled clone URL back.

**Ask:**
- Does any new message, log line, exception, or report field carry a URL or a
  command transcript? It goes through `Redaction`.
- Is the masking **structural** — on the type that holds the value, like
  `CommandResult.redactedOutput()` — or does it depend on every call site
  remembering? The latter is what leaked.

### Quick pass

| If the diff touches… | Check |
|---|---|
| a parser / regex / mask | 2.1 — and load `text-parsers` |
| a constant or predicate that exists twice | 2.2 — is there a contract test? |
| `CommandResult`, `--porcelain`, `gh`, `git` output | 2.3, 2.5 |
| a step that verifies, reports, or commits | 2.4 |
| any log, message, exception, or report field | 2.5 |

---

## 3. General Java checks

| Area | Flag | Prefer |
|---|---|---|
| **Null safety** | chained calls without a check, `Optional.get()` without a presence check, returning `null` for a collection | early return, `Objects.requireNonNull(x, "x must not be null")` on public inputs, `List.of()`/`Collections.emptyList()` |
| **Exceptions** | empty catch, catching `Exception`/`Throwable` broadly, dropping the cause, exceptions as flow control | `log.error("Failed to process {}", file, e)` **and** rethrow chaining the cause |
| **Collections & streams** | mutation during iteration, assuming `toList()` is mutable, parallel streams without a reason | `removeIf`, `Collectors.toCollection(ArrayList::new)` when mutation is needed, `List.of()` for constants |
| **Concurrency** | shared mutable state without synchronization, check-then-act races, locking on non-final objects | `ConcurrentHashMap`, `computeIfAbsent`, `volatile` on mutable static state (repo rule) |
| **Idioms** | `equals` without `hashCode`, mutable fields in `hashCode`, sensitive data in `toString`, > 3–4 constructor params | `Objects.equals`/`Objects.hash`, `instanceof` pattern matching, a builder |
| **Resources** | `AutoCloseable` opened outside try-with-resources, JDBC statements not closed (`data`'s `source.db`) | try-with-resources, one declaration per resource so both always close |
| **API design** | boolean parameters, > 3 parameters, inconsistent null handling, unvalidated public inputs | enums, a parameter object; reinforce SRP/DIP (see `solid-principles`) |
| **Performance** | string concatenation in loops, `Pattern.compile` inside a loop, N+1 access, object churn in tight loops | `StringBuilder`, a `private static final Pattern`, primitive streams where it matters |

## References
- `CLAUDE.md` / `AGENTS.md` — source of truth for the enforced rules
- Module `.architecture` tests — the rules as executable checks
- `git log --grep='^fix'` — the source of section 2; each commit body names the
  input that broke the code and why
- Related skills: `text-parsers` (2.1), `solid-principles`,
  `testing-conventions`, `maven-conventions`
