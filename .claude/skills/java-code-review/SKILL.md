---
name: java-code-review
description: Systematic Java code review for the tools repo — leads with the ArchUnit-enforced rules the build fails on, then the five defect shapes this repository actually ships fixes for, then null safety, exceptions, concurrency, and performance. Use when the user says "review code", "check this PR", "code review", or before merging changes.
---

# Java Code Review Skill

Systematic code review for the `tools` multi-module reactor. Start with the
rules this repo *enforces at build time* — an ArchUnit or Surefire violation
fails the build, not just review — then work through the general Java checks.

## When to Use
- User says "review this code" / "check this PR" / "code review"
- Before merging a PR
- After implementing a feature

## Review Strategy
1. **Enforced-rules pass first** (section 0) — anything there fails the build, so
   flag it as Critical regardless of how the code otherwise reads.
2. **Defect-shapes pass** (section 9) — the five shapes this repo actually ships
   fixes for. Highest yield after section 0; a generic checklist misses all five.
3. **Checklist pass** — null safety, exceptions, collections, concurrency,
   idioms, resources, API, performance.
4. **Summary** — findings by severity (Critical → Minor), with line references.

## Output Format

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

---

## 0. Repo rules the build enforces (check these FIRST)

These are pinned by the module `.architecture` ArchUnit tests and the root
Surefire config. Production code is `io.github.adamw7.*` (Java 25). Flag any
violation as **Critical** — CI will reject it.

| Rule | What to flag |
|---|---|
| **No `continue` / `break`** | Any use in *any* code (production or test). Refactor the loop. |
| **No `System.out` / `System.err`** | In production code — log through log4j2 instead. (Also banned in tests.) |
| **Logging via log4j2 only** | No `java.lang.System.Logger`, no `printStackTrace`, no `System.exit`. Report failures through log4j2. |
| **Loggers `private static final`** | Any other modifier set on a `Logger` field. |
| **No `Optional` fields** | A field typed `Optional<…>`. Optional is for possibly-absent *return values*; a field holds the value itself and is null-checked. |
| **Mutable static state is `volatile`** | A non-final mutable static field without `volatile`. |
| **`java.time` only** | Any use of legacy `java.util.Date` / `Calendar`. |
| **No package cycles / layering breaks** | Data-source contracts depending on their impls, uniqueness core depending on its MCP adapter, JDBC outside `source.db`. |
| **Test conventions** | Tests only in `*Test`/`*IT`; JUnit 5 only; no `@Disabled`; no `Thread.sleep`; no `System.out`/`err`. See `testing-conventions`. |
| **Surefire 5 s/unit test** | A unit test doing real work without a justified `@Timeout`. See `testing-conventions`. |

> Prefer a plain `for`/`for-each` loop with a single exit over any construct
> that would want `continue`/`break`; extract a helper method that `return`s
> early instead.

---

## 1. Null Safety

```java
// ❌ NPE risk
String name = user.getName().toUpperCase();

// ✅ Early return
if (user.getName() == null) {
    return "";
}
return user.getName().toUpperCase();
```

**Flags:**
- Chained method calls without null checks
- `Optional.get()` without a presence check
- Returning `null` where an empty collection reads better

**Suggest:**
- `Objects.requireNonNull(x, "x must not be null")` on constructor/method params
- Return `Collections.emptyList()` (etc.) instead of `null`
- `Optional` is fine as a **return type**, never as a **field** (repo rule above)

## 2. Exception Handling

```java
// ❌ Swallowing / losing the cause
try { process(); } catch (Exception e) { /* ignored */ }
catch (IOException e) { throw new RuntimeException(e.getMessage()); }

// ✅ Log with context AND chain the cause (log4j2)
catch (IOException e) {
    log.error("Failed to process file: {}", filename, e);
    throw new ProcessingException("File processing failed", e);
}
```

**Flags:** empty catch blocks, catching `Exception`/`Throwable` broadly, dropping
the original cause, exceptions used for flow control, `printStackTrace` (banned).

## 3. Collections & Streams

```java
// ❌ Modifying while iterating → ConcurrentModificationException
for (Item item : items) { if (item.isExpired()) items.remove(item); }
// ✅
items.removeIf(Item::isExpired);

// ❌ Assuming a mutable result
List<String> names = users.stream().map(User::getName).collect(Collectors.toList());
names.add("extra");
// ✅ Be explicit when you need to mutate
List<String> names = users.stream().map(User::getName)
    .collect(Collectors.toCollection(ArrayList::new));
```

**Flags:** modification during iteration, assuming `toList()` is mutable, not
using `List.of()`/`Set.of()`/`Map.of()` for constants, parallel streams without
a reason.

## 4. Concurrency

```java
// ❌ Not thread-safe
private Map<String, User> cache = new HashMap<>();
// ✅
private final Map<String, User> cache = new ConcurrentHashMap<>();

// ❌ Check-then-act race
if (!map.containsKey(key)) map.put(key, computeValue());
// ✅ Atomic
map.computeIfAbsent(key, k -> computeValue());
```

**Flags:** shared mutable state without synchronization, check-then-act without
atomicity, **missing `volatile` on mutable static state** (repo rule), locking
on non-final objects.

## 5. Java Idioms

```java
// ✅ equals + hashCode on immutable fields, both or neither
@Override public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof User user)) return false;   // pattern matching (Java 25)
    return Objects.equals(id, user.id);
}
@Override public int hashCode() { return Objects.hash(id); }
```

**Flags:** `equals` without `hashCode` (or vice versa), mutable fields in
`hashCode`, missing `toString` on domain objects, sensitive data in `toString`,
constructors with > 3–4 params (consider a builder — the repo's
`protogen-maven-plugin` generates compile-time-safe builders for protobuf).

## 6. Resource Management

```java
// ✅ Try-with-resources; separate declarations so both always close
try (FileWriter fw = new FileWriter(file);
     BufferedWriter writer = new BufferedWriter(fw)) {
    // ...
}
```

**Flags:** `Closeable`/`AutoCloseable` opened outside try-with-resources; JDBC
connections/statements not closed (relevant in `data`'s `source.db`).

## 7. API Design

```java
// ❌ Boolean params — meaning is lost at the call site
process(data, true, false);
// ✅ Enums
process(data, ProcessMode.ASYNC, ErrorHandling.STRICT);
```

**Flags:** boolean params (prefer enums), > 3 params (parameter object),
inconsistent null handling, missing validation on public inputs. Reinforce
**SRP/DIP** — see `solid-principles`.

## 8. Performance

```java
// ❌ String concat in a loop / regex compiled per iteration
// ✅
StringBuilder sb = new StringBuilder();
private static final Pattern PATTERN = Pattern.compile("pattern.*");
```

**Flags:** string concatenation in loops, regex compiled inside loops, N+1
access patterns, object churn in tight loops, not using primitive streams
(`IntStream`/`LongStream`) where it matters.

---

## 9. The defect shapes this repo actually ships

Sections 1–8 are the general Java checks. They are worth running, but on the
evidence of this repository's own history they are not where its bugs are: of the
last 26 `fix(...)` commits, **almost none** would have been caught by them. These
five shapes would have been. Review for them explicitly.

### 9.1 A hand-rolled reader meeting real input

The largest class by a distance — `FrontMatter`, `CommandTokens`,
`MarkdownDocument`, `ImportGraph` and `MarkdownFormatRule` account for most of
the fix commits here. Every one was input the reader was not written for: a
comment opened after text on its line, an apostrophe read as an opening quote, a
newline inside a hook command, `[logo](assets/logo(1).png)`, a bare `@claude`
mention read as a memory import.

The strongest fix for this shape is not a better hand-rolled reader but no
hand-rolled reader: `FrontMatter` now composes its block with SnakeYAML, and the
quoting, comment and block-scalar rules that had cost it six or seven fix commits
came with it. Ask whether the format under review has a parser worth depending on
before reviewing the scanning loop line by line.

**Ask:**
- Does this track state across the whole line, or does it `startsWith` /
  `contains` and hope? A delimiter can appear anywhere, and twice.
- Does the regex stop at the *first* closing delimiter when the content can nest?
- Is markup matched *after* `withoutCodeSpans` and the fence/comment masks, or
  before?
- Does an unterminated construct at end of file leave the mask in a sane state?

**Load the `text-parsers` skill** before changing any of these — it carries each
reader's invariants and the full adversarial-input checklist.

### 9.2 Two implementations of one format, drifting

`ClaudeMdConformer` duplicates `claudeMdFormat`'s reading of a Markdown document
because `adopt` must not ship the maven-enforcer API. #536 was four defects from
exactly that copy drifting: the conformer produced a document the rule it exists
to satisfy rejects, so the adoption failed its own `VerifyStep` on a file it had
just reshaped to pass. #557 is the same shape one level up — detection asked
whether a pom *depended on* the enforcer artifact rather than whether it
*configures the rule*, so a project running `noSecrets` and no CLAUDE.md guard at
all was reported as already guarded.

**Ask:**
- Is there a second implementation of this format, constant, or predicate
  anywhere in the reactor? Search before assuming not.
- Is there a test that runs the **real** other side over this side's output —
  not a copy of what the other side is believed to want? See
  `ClaudeMdConformerContractTest`.
- Does the check test for the *thing it cares about*, or for a proxy that
  correlates with it?

### 9.3 Reading a command transcript

The runner merges stderr into the transcript. A `git` that warns about an
unreadable system config puts that line in with the `--porcelain` entries, and
#539 read its fourth character onwards as a changed path — refusing a resume the
step promises. That was the *second* time: the fix commit notes the identical
defect had already been fixed for the origin query beside it.

**Ask:**
- Is this parsing output that can carry stderr noise? Skip lines that are not
  well-formed entries rather than reading them as one — while still letting a
  real entry beside the warning take effect.
- Is captured output bounded?
- Is the exit code checked, or only the text?

### 9.4 Success reported for work that never happened

The most expensive shape, because nothing downstream catches it. `git add -A`
skips an ignored path in silence, so a file the adoption wrote stayed in the
working tree — where `VerifyStep` went on finding it and passing — while the
pushed branch carried nothing of it. `okfBundleFormat` returned early for an
absent bundle and never reached `report()`, leaving a previous failing run's HTML
on disk claiming a failure for a check that had just passed.

**Ask:**
- Does the verification read the same state the operation actually wrote — the
  commit, not the working tree; the remote, not the local branch?
- Does **every** exit path, including the early and empty ones, go through the
  reporting call?
- Can this succeed by doing nothing? Does a test assert the input was rejected
  *before* the operation, so a no-op cannot pass it?

### 9.5 A new path for a credential

Three separate commits: a token in a rejected-positional message, in a `gh`
transcript logged raw, in an MCP argument log. A `gh` without `--repo` reads the
remote through git, which echoes a credentialled clone URL back.

**Ask:**
- Does any new message, log line, exception, or report field carry a URL or a
  command transcript? It goes through `Redaction`.
- Is the masking **structural** — on the type that holds the value, like
  `CommandResult.redactedOutput()` — or does it depend on every call site
  remembering? Prefer the former; the latter is what leaked.

### Quick pass

| If the diff touches… | Check |
|---|---|
| a parser / regex / mask | 9.1 — and load `text-parsers` |
| a constant or predicate that exists twice | 9.2 — is there a contract test? |
| `CommandResult`, `--porcelain`, `gh`, `git` output | 9.3, 9.5 |
| a step that verifies, reports, or commits | 9.4 |
| any log, message, exception, or report field | 9.5 |

---

## Severity Guidelines

| Severity | Criteria |
|----------|----------|
| **Critical** | Breaks an enforced repo rule (section 0), security/data-loss risk, or a likely crash |
| **High** | Bug likely, significant perf issue, breaks an API contract |
| **Medium** | Code smell, maintainability issue, missing best practice |
| **Low** | Style, minor optimization |

## Token Optimization
- Focus on changed lines (`git diff`); group similar findings.
- Reference line numbers, don't re-quote whole blocks.
- Skip generated sources (protogen output under `target/`) and fixtures.

## References
- `CLAUDE.md` / `AGENTS.md` — source of truth for the enforced rules
- Module `.architecture` tests (e.g. `ProtogenArchitectureTest`,
  `ContextArchitectureTest`, `TestConventionsArchitectureTest`)
- `git log --grep='^fix'` — the source of section 9; each commit body names the
  input that broke the code and why
- Related skills: `text-parsers` (section 9.1), `solid-principles`,
  `testing-conventions`, `maven-conventions`
