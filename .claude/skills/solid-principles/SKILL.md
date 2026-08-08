---
name: solid-principles
description: Apply and review SOLID in this repo's Java — the detection heuristics per principle, the refactoring that fixes each one, and the real examples in data, adopt, context and mcp-common. Use when reviewing class design, refactoring a large class, or when the user says "check SOLID", "is this class doing too much?", or names one of the five principles.
---

# SOLID Principles Skill

Review and apply SOLID in the `tools` reactor. The heuristics below are the fast
path; long before/after code lives in **`examples.md`** next to this file — load
it only when a worked example would actually help.

## Quick reference

| Letter | Principle | One-liner |
|--------|-----------|-----------|
| **S** | Single Responsibility | One class = one reason to change |
| **O** | Open/Closed | Open for extension, closed for modification |
| **L** | Liskov Substitution | Subtypes must be substitutable for base types |
| **I** | Interface Segregation | Many specific interfaces beat one general one |
| **D** | Dependency Inversion | Depend on abstractions, not concretions |

---

## S — Single Responsibility
> One reason to change.

**Detect**: imports from unrelated domains · a name containing "And", "Manager"
or "Handler" · methods that use none of the fields · you cannot describe the
class in one sentence without "and".

**Fix**: Extract Class / Move Method.

**Here**: an `AdoptionStep` is one stage of the adoption and nothing else;
`ClaudeCodeEnforcerRule.report(...)` is the one exit point every enforcer rule
funnels its violations through; the uniqueness core knows nothing about its MCP
adapter (ArchUnit fails the build if it learns).

---

## O — Open/Closed
> New behaviour arrives as a new class, not an edit to an old one.

**Detect**: an `if/else` or `switch` on a type or status that keeps growing ·
adding a feature means editing a core class.

**Fix**: Strategy, Template Method, Decorator, Factory.

**Here**: `GitHubRepoAdopter` composes a `List<AdoptionStep>`, so a new stage is
a new step, not a branch; a new tree output format is a new
`ProjectTreeSerializer`; `BudgetedContext` decorates any `Context` instead of
teaching `Finder` about budgets; `AbstractFinder` template-methods the
depth-bounded traversal and lets subclasses supply only
`findDirectDependencies`.

---

## L — Liskov Substitution
> A subtype may not surprise a caller holding the base type.

**Detect**: a subclass throwing where the parent doesn't · returning `null`
where the parent returns a value · `instanceof` checks before calling a method ·
empty or `UnsupportedOperationException` overrides.

**Fix**: composition over inheritance, or extract a narrower interface.

**Here**: the repo prefers immutable, constructor-set state — step fields are
final by an ArchUnit rule — which removes the classic setter-based LSP traps
before they start.

---

## I — Interface Segregation
> A client should not be forced to depend on methods it does not use.

**Detect**: a fat interface (10+ methods) · implementations with empty bodies ·
different clients using disjoint subsets.

**Fix**: split by role; combine at the implementation.

**Here — the canonical example.** `IterableDataSource` is the forward-only
contract (`open`, `nextRow`, `hasMoreData`, `reset`); `ColumnarDataSource
extends IterableDataSource` adds `getColumnNames()` for sources whose schema is
known up front. Forward-only JSON/YAML/TOON iterables deliberately **do not**
implement the columnar contract, so the uniqueness check — which depends on the
narrower type — can never be handed a source that would answer `null`. Don't
widen a contract to silence a compile error; that error is the design working.

---

## D — Dependency Inversion
> High-level and low-level both depend on the abstraction.

**Detect**: `new ConcreteClass()` inside business logic · imports of
implementation packages · tests that need a database or the network.

**Fix**: constructor injection, an interface owned by the caller's side.

**Here**: steps shell out through the `CommandRunner` interface, never
`ProcessBuilder`, so tests pass a fake runner and never spawn a process; MCP
tools implement `McpTool` and never touch the SDK; `ProjectTreeBuilder` takes a
`ContextFactory` so the finder can be swapped without touching the builder.

**Wiring is plain constructor injection** — no Spring or CDI outside the MCP
adapters. Pass concretions from the caller; pass fakes from the test. That is
what makes the network-off unit tests possible.

---

## Repo rules that interact with these

- **A field must never be `Optional`** (ArchUnit). `Optional` as a *return type*
  is fine — hold the value and null-check it in a field.
- **Abstract types carry an `Abstract` prefix** (`AbstractFinder`,
  `AbstractCommandStep`), public fields are `final`, mutable static state is
  `volatile`.
- **No `continue` or `break`** anywhere — a loop that wants one usually wants a
  stream or an extracted method, which tends to improve the design anyway.
- Packages must stay free of cycles; a "shared" class pulled sideways between
  packages is a layering smell before it is a SOLID one.

## Review checklist

| Principle | Question |
|-----------|----------|
| **SRP** | More than one reason to change? |
| **OCP** | Will the next feature require editing this class? |
| **LSP** | Can every subtype stand in for the base type? |
| **ISP** | Any empty or throwing implementations? |
| **DIP** | Does high-level code name a concrete implementation? |

| Violation | Refactoring |
|-----------|-------------|
| SRP — god class | Extract Class, Move Method |
| OCP — type switching | Strategy, Factory |
| LSP — broken inheritance | Composition, Extract Interface |
| ISP — fat interface | Split Interface, Role Interface |
| DIP — hard dependency | Constructor injection, Abstract Factory |

## Related
- `examples.md` (this directory) — full before/after code per principle
- `java-code-review` — the wider review checklist, led by the enforced rules
- `testing-conventions` — the fast, network-off tests these abstractions enable
