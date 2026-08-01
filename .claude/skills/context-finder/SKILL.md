---
name: context-finder
description: Assemble gen-AI context with the code/context module — the class-usage finders (name-based vs package-aware), the project-tree builder and its serializers, token estimation and budgeting, and the project_tree/find_context/estimate_tokens MCP tools. Use when gathering the classes a file depends on, scanning a project into a tree, sizing context for a model, or when the user says "find context", "project tree", or "estimate tokens".
---

# Context Finder Skill

Use `code/context` to answer "which classes does this file actually need?" and
"how many tokens will that cost?" — the module behind the `project_tree`,
`find_context` and `estimate_tokens` MCP tools.

## When to Use
- Collecting the dependency closure of a source file to feed a model
- Scanning a project into a tree of folders, files and class dependencies
- Sizing or trimming context against a token budget
- Adding a language, a serializer, or a resolution strategy
- The user says "find context" / "project tree" / "estimate tokens"

## The core contract
```java
public interface Context {
    Set<ClassContainer> find(ClassContainer root, int depth);
}
```
A `ClassContainer` is just a `className` (the file name) plus its
`originalCode`. `depth` bounds a breadth-first expansion, so nearest
dependencies come first — which is what makes budgeting meaningful.

Load a project's sources with `new ProjectSources(Language.JAVA).load(root)`,
which returns `Map<Path, ClassContainer>`. Languages: `JAVA` (`.java`),
`KOTLIN` (`.kt`), `SCALA` (`.scala`).

## Pick the right finder
| Finder | Resolves by | Use when |
|---|---|---|
| `Finder` | simple file name, indexed once at construction | one class per simple name; fastest |
| `PackageAwareFinder` | `package` + `import` declarations | two classes share a simple name in different packages |

`PackageAwareFinder` prefers, in order: an explicit import, the referencing
file's own package, a wildcard import, then a sole project-wide candidate — and
leaves a genuinely ambiguous reference **unresolved rather than guessed**. Both
strip comments and string/character literals before matching, so a class name
mentioned in a comment is not reported as a dependency. The
`package`/`import` grammar is shared by all three languages.

## Budgeting
`BudgetedContext` decorates any `Context`: it keeps the delegate's breadth-first
order and accepts containers until the next one would exceed the budget, so you
keep the highest-priority prefix of the graph.

```java
Context context = new BudgetedContext(new Finder(containers), new SubwordTokenEstimator(), 8_000);
```
Estimators implement `TokenEstimator`: `SubwordTokenEstimator` (respects word
and symbol boundaries — the better default) and `HeuristicTokenEstimator`.

## Project tree
`ProjectTreeBuilder(contextFactory, language, depth).build(root)` returns a
`ProjectTreeNode`. Serialize with a `ProjectTreeSerializer`:
`ProjectTreeJsonSerializer` (the MCP default), `…Markdown…`, `…Dot…` (Graphviz),
`…Mermaid…`, or print with `ProjectTreePrinter`. The builder takes a
`ContextFactory`, so a different finder can be substituted without touching the
builder — add a serializer or a factory rather than branching inside the
builder.

## MCP tools
`project_tree`, `find_context` and `estimate_tokens` are the adapter over the
above, in `…context.mcp`. `PathPolicy` confines them to allowed directories.
**The core must never depend on the `mcp` package** — ArchUnit pins it. See the
`mcp-server` skill before changing a tool, and update
`code/context/.../mcp/MCP_USAGE.md` in the same change.

## Adding to this module
- New language → add to `Language` with its extension; the finders follow.
- New output format → implement `ProjectTreeSerializer`, don't extend the tree.
- New resolution strategy → extend `AbstractFinder` and supply
  `findDirectDependencies`; traversal and depth-bounding are inherited.

## References
- `code/context/.../mcp/MCP_USAGE.md` — the three tools and their parameters
- `README.md` — *Context engineering* worked examples
- `.claude/skills/mcp-server/SKILL.md` — for the adapter side
