---
name: java-code-review
description: Systematic Java code review for the tools repo — leads with the ArchUnit-enforced rules the build fails on, then null safety, exceptions, concurrency, and performance. Use when the user says "review code", "check this PR", "code review", or before merging changes.
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
1. **Enforced-rules pass first** — anything below fails the build, so flag it as
   Critical regardless of how the code otherwise reads.
2. **Checklist pass** — null safety, exceptions, collections, concurrency,
   idioms, resources, API, performance.
3. **Summary** — findings by severity (Critical → Minor), with line references.

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
- Related skills: `solid-principles`, `testing-conventions`, `maven-conventions`
