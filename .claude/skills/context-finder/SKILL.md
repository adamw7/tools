---
name: context-finder
description: Assemble gen-AI context with the code/context module — the class-usage finders (name-based vs package-aware), the project-tree builder and its serializers, Open Knowledge Format (OKF) bundles, token estimation and budgeting, and the project_tree/find_context/estimate_tokens/okf_bundle MCP tools. Use when gathering the classes a file depends on, scanning a project into a tree, emitting an OKF bundle, sizing context for a model, or when the user says "find context", "project tree", "OKF", or "estimate tokens".
---

# Context Finder Skill

Use `code/context` to answer "which classes does this file actually need?" and
"how many tokens will that cost?" — the module behind the `project_tree`,
`find_context`, `estimate_tokens` and `okf_bundle` MCP tools.

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

## Open Knowledge Format
The `…context.okf` package emits the same tree as an **OKF v0.2** bundle — a
directory of markdown files, not one document:

```java
OkfBundle bundle = new OkfBundler(Language.JAVA).bundle(tree);
new OkfBundleWriter().write(bundle, Path.of("target/okf"));
```

- Every directory → the reserved `index.md` (no frontmatter; only a bundle-root
  index may carry `okf_version`). Every file → a concept document at its name plus
  `.md`; a file whose name would claim a reserved document (`index`, `log`) gets
  `.concept.md` instead, so no two documents share a path.
- `type` is the **one** mandatory frontmatter field; `title`, `description`,
  `resource`, `tags` are recommended; v0.2 records production as
  `generated: { by, at }` using the `<producer>/<version>` actor convention.
- Cross-links use the bundle-relative `/`-prefixed form. A dependency is linked
  only when its file name identifies exactly one concept — unresolved or
  ambiguous names stay plain code rather than linking to a guess.
- `OkfConcept` holds a concept's facts before rendering, so the index entry and
  the frontmatter carry the same description, as the spec recommends.
- Adding a frontmatter field → extend `OkfFrontmatter` (fixed render order,
  every scalar quoted), not the bundler.

Two build-level checks guard this, and a change to the emitter has to keep both
green:
- `OkfBundleConformanceTest` (this module) restates the spec's conformance
  conditions against every emitted bundle. It runs in the ordinary `test` phase,
  so `mvn test` / `package` / `install` all catch drift.
- The `okfBundleFormat` enforcer rule checks a bundle already on disk, at
  `validate` under `-DenforceClaudeMd` — what `.github/workflows/maven.yml` runs
  on every pull request. See the `enforcer-rules` skill to change it.

## MCP tools
`project_tree`, `find_context`, `estimate_tokens` and `okf_bundle` are the
adapter over the above, in `…context.mcp`. All four resolve with
`PackageAwareFinder` — the whole-project tools pass it to `ProjectTreeBuilder` as a
`ContextFactory` rather than taking the builder's name-based default, so one server
answers one question one way. `depth` runs from 1 to 10. `PathPolicy` confines them
to allowed directories, and the server is **read-only** — `okf_bundle` returns the
bundle as JSON rather than writing it, so keep writing on the caller's side.
**The core must never depend on the `mcp` package** — ArchUnit pins it. See the
`mcp-server` skill before changing a tool, and update
`code/context/.../mcp/MCP_USAGE.md` in the same change.

## Adding to this module
- New language → add to `Language` with its extension; the finders follow.
- New output format → implement `ProjectTreeSerializer`, don't extend the tree.
- New resolution strategy → extend `AbstractFinder` and supply
  `findDirectDependencies`; traversal and depth-bounding are inherited.

## References
- `code/context/.../mcp/MCP_USAGE.md` — the four tools and their parameters
- [OKF v0.2 spec](https://github.com/GoogleCloudPlatform/knowledge-catalog/blob/main/okf/SPEC.md)
  — conformance rules, frontmatter families, reserved filenames
- `README.md` — *Context engineering* worked examples
- `.claude/skills/mcp-server/SKILL.md` — for the adapter side
