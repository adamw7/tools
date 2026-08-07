---
name: text-parsers
description: Work on this repo's text readers — MarkdownDocument, MarkdownText, ImportGraph, CommandTokens, the ClaudeMdConformer copy of them, and the SnakeYAML-backed FrontMatter — and the adversarial input that has broken each. Use when changing how a document, YAML front matter, a memory import or a hook command is read, when a rule fires on valid input or passes invalid input, or when the user says "fence", "front matter", "heading detection", "memory import", or "hook command".
---

# Text Parsers Skill

The `claude-code-enforcer` rules and the `adopt` conformer read Markdown, YAML
front matter, `@path` imports and shell hook commands with small readers of their
own. Those readers carry the largest share of this repository's shipped defects:
`FrontMatter` and `CommandTokens` have each been fixed in six or seven separate
commits, `MarkdownDocument` and `ClaudeMdConformer` in four or five.

Every one of those fixes was the same thing — real input the reader had not been
written for. This skill is the accumulated list, so the next change starts from
it instead of rediscovering it. `FrontMatter` is the one that has since been
handed to a library: it composes the block with SnakeYAML rather than scanning
quotes and comments itself, and its section below says what that changed.

## When to Use
- Changing any reader in `claude-code-enforcer/.../text`, `.../doc/ImportGraph`,
  `.../settings/CommandTokens`, or `adopt`'s `ClaudeMdConformer`
- A rule fails a valid document, or passes an invalid one
- Adding a rule that reads document structure rather than a whole file
- The user says "fence", "front matter", "heading", "memory import",
  "hook command", or "the enforcer rejects a file it shouldn't"

## The one rule that matters most

**Change the shared reader, never the caller.** `MarkdownDocument`,
`FrontMatter`, `MarkdownText` and `CommandTokens` exist so every rule agrees on
what a fence, a key, a code span and a token are. A rule that re-derives one of
those locally is how the readers drifted in the first place — a fix landed in one
place and the other two callers kept the bug.

Concretely: commit #560 fixed *three* rules at once (`memoryImports`,
`validateFileReferences`, `forbiddenTokens`) because the comment mask they all
share was only being consulted by heading detection. The fix was one method
exposed on `MarkdownDocument`, not three patches.

## The readers and their invariants

### `MarkdownDocument`

Parses lines plus two masks — fenced code, and HTML comment — once at
construction, so structural checks share them.

| Invariant | Why |
|---|---|
| A fence closes only on a run of the **same character**, **at least as long**, carrying **no info string** | Docs about Markdown nest fences: `~~~` inside ```` ``` ````, ```` ```java ```` inside ```` ```` ````. Closing on the first character ends the block early, then reads the real closer as a *fresh opening* one and masks the rest of the file as code. |
| A heading is ATX — `#{1,6}` then whitespace or end of line | `#1 rule: run mvn install` is prose. Read as an H1 it ends the section above and that section gets reported empty. |
| Headings are recognised **outside fences and outside comments** | A commented-out section must not satisfy the check that demands it. |
| Comment state is tracked from **every delimiter on the line**, not the first characters | `Superseded: <!--` opens a block. `<!-- a --> <!-- b` ends open. Both were missed by a `startsWith`/`contains` pair. |
| A comment inside a fence is sample text — **the fence wins** | Ordering matters: `commentMask` takes `insideFence` as input. |
| `containsInProse` skips comments *and* fences, both directions | A commented-out mention must neither satisfy a required-token check nor trip a forbidden-token one. |
| A blank line and a commented-out line do **not** settle whether a section has a body | A section whose only content is commented out reads as empty, which is what commenting it out meant. |

### `FrontMatter`

**The one reader here that is no longer hand-rolled.** It delimits the `---`
block itself, then hands the block to SnakeYAML and answers each entry as one
line of text — length, blankness, uniqueness, shape. Six or seven of this
repository's `fix(...)` commits were quoting, comment and block-scalar rules
re-derived by hand; those rules now come from the loader.

| Invariant | Why |
|---|---|
| The block is **composed**, never loaded | `Yaml.compose` answers a node tree, so a value stays the text its author wrote — `okf_version: 0.20` is the string `0.20`, not a double rounded to `0.2` — and a duplicated key stays visible instead of collapsing into a `Map`. |
| Every value is **folded onto one line** | A block scalar, a wrapped plain scalar, a nested mapping or a sequence all read back as text. Folding a mapping is lossy on purpose: `by: agent/1` comes back as text. A rule needing the structure should ask SnakeYAML directly. |
| A key declared twice yields its **last** declaration | That is the one a YAML loader keeps, so the one Claude Code acts on. `duplicateKeys()` reports the duplication separately. |
| A block no loader can read is **not front matter** | `description: "a" and "b"` and an unterminated `"oops` have no YAML meaning, and Claude Code's loader fails on them too. `parse` answers empty and the rules report the block's absence, which says more than a guess at the malformed line. A block that *reads* but is not a mapping — `name:value`, no space — is present and declares nothing. |
| Front matter opens on the **very first line** | Content reaching `---` after blank lines has no front matter, because Claude Code sees none either. |

The cases that used to need their own hand-written rule — `name: "git-commit"`
unquoted, `Don't stop # a note` keeping its apostrophe, `version: 1.0#2` having
no comment, `description: >` folding the lines below it — are all the loader's
job now, and `FrontMatterTest` still pins every one of them.

### `MarkdownText`

- `read` throws `UncheckedIOException`; `readIfText` yields empty. **A rule
  scanning a directory it does not control uses `readIfText`** — an undecodable
  file must be reported as a malformed definition, not abort the build and take
  the remaining definitions' violations with it.
- `withoutCodeSpans` before matching any markup. Documentation about Markdown
  writes its sample link or import in backticks precisely because it is a sample.
- `write` refuses a symbolic link, so an auto-fix cannot be redirected through a
  planted link.
- Strip the byte-order mark on read, once, centrally.

### `ImportGraph`

- Built **breadth-first** so a file is first reached by its *shortest* chain —
  Claude Code's hop limit is about the shortest chain, so a depth-first count
  would make the rule decide differently run to run.
- An import must **look like a path**: a separator, an extension, or both. A bare
  `@claude` mention or an `@adamw7` handle is prose. No amount of backticking
  every occurrence could be relied on instead.
- Trailing sentence dots are dropped: `see @docs/setup.md.` imports
  `docs/setup.md`.
- A rooted `@/docs.md` resolves against **the importing file's** root, not the
  process's — otherwise Windows picks whichever drive the build started on.
- An unreadable target is a **leaf, not a failure**: an imported file may be any
  format.

### `CommandTokens`

Splits a hook command the way a shell would.

- Separators are whitespace **and** `;`, `&`, `|`, **newline**, and subshell
  `(`/`)` — all outside quotes. A newline separates commands: a hook written
  across several lines had every line after the first taken for arguments. Glued
  parens invent a program named `(script.sh)`.
- Quotes are honoured and **kept on the token** — a hook path with a space is
  legitimate and quoted for that reason; `ClaudeProjectDir` strips them while
  expanding.
- Skip `VAR=value` prefixes and shell **reserved words** (`if`, `then`, `fi`, …)
  to find the program, as a shell does. Reading the first token blindly took
  `then` for the program.
- An interpreter (`bash`, `python3`, `node`, …) names a script in its first
  non-option argument — **unless** `-c` is present, alone or in a cluster like
  `-ec`, where the argument is script text and not a path.
- Only the program of each segment, plus an interpreted script, is a script
  candidate. An ordinary argument that looks like a path is not: requiring
  `--out target/log.txt` to exist fails a build over a file the hook is about to
  write.

## The two-readers problem

`ClaudeMdConformer` in `adopt` spells out a second copy of `MarkdownDocument`'s
fence and comment reading, plus `claudeMdFormat`'s required sections. It has to —
`adopt` is a plain library and must not put the maven-enforcer API on every
consumer's classpath.

**A copy is only safe when a test proves it agrees.** The pattern here is
`ClaudeMdConformerContractTest`: it runs the *real* rule, on a test-scoped
dependency, over the conformer's output, and asserts the raw fixture is rejected
first so a conformer that stopped reshaping cannot pass by doing nothing.

When you touch either side:

1. Change the enforcer reader.
2. Mirror it in the conformer.
3. Add the case to the contract test — **fixture rejected before, accepted after**.

Skipping step 3 is what shipped #536: four defects where the conformer produced a
document the rule it exists to satisfy rejects, so the adoption failed its own
`VerifyStep` on a file it had just reshaped to pass it. On someone else's
repository, after the branch was pushed.

## Adversarial input checklist

Every row has broken a reader here. Run a new or changed reader against the ones
in its column before calling it done.

| Input | Reader |
|---|---|
| ```` ```` ````-wrapped ```` ``` ````; `~~~` inside backticks; ```` ```java ```` as a closer | fences |
| `#1 rule: …`; `#hashtag`; `####### seven` | headings |
| `Superseded: <!--`; `<!-- a --> <!-- b`; comment inside a fence; unterminated comment at EOF | comments |
| `name: "git-commit"`; `Don't stop # a note`; `version: 1.0#2`; `description: >` then indented lines; `key:` bare; a key declared twice; `key:value` with no space | front matter |
| `[logo](assets/logo(1).png)`; a link inside backticks; a link inside a comment | links |
| `@claude` in prose; `@adamw7`; `` `@docs/x.md` ``; `see @docs/setup.md.`; `@~/global.md`; `@/rooted.md` | imports |
| `a.sh; b.sh`; a command across two lines; `( a.sh )`; `FOO=bar a.sh`; `if [ -n "$CI" ]; then a.sh; fi`; `bash -ec 'echo hi'`; `"my hook.sh"` | hook commands |
| A UTF-8 BOM; a file that is not UTF-8 at all; CRLF line endings; an empty file | every file reader |

## Testing

- **Table-driven, one row per adversarial input.** The existing `*Test` classes
  beside each reader are the model; add rows rather than new classes.
- **Pin the bug, not the fix.** Each fix commit here adds a case named for the
  input that broke it. A test that only exercises the happy path lets the next
  refactor undo the fix silently.
- **Assert both directions** on any check with a mirror: a commented-out token
  must not satisfy a required-token rule *and* must not trip a forbidden-token
  one. Half of #560 was the mirror case.
- **Every exit path reports.** A rule that returns early for an absent or empty
  input still calls `report()` — otherwise a configured `reportFile` keeps a
  previous failing run's HTML on disk and `writeBaseline` records nothing.
- Mutation testing is the sharpest tool for these readers, and
  `claude-code-enforcer` holds an 88% threshold:
  `mvn -Ppitest install -pl claude-code-enforcer -am`. A surviving mutant on a
  boundary condition in a mask is very often a real gap.
- Standard rules still apply — 5 s per unit test, JUnit 5, no `Thread.sleep`.
  See `testing-conventions`.

## Common mistakes

- Reading a line with `startsWith` / `contains` where state must be tracked
  across the whole line.
- Matching markup before `withoutCodeSpans`, or before checking
  `isInsideFence` / `isInsideComment`.
- Regex that stops at the first closing delimiter — `[^)]` truncated
  `assets/logo(1).png` and reported the half as missing.
- Fixing one caller of a shared reader and leaving the others.
- Adding a check to the conformer without adding its case to the contract test.
- Reaching for a fuller YAML/Markdown model inside these readers. If a rule needs
  real structure, that is a signal to bring in a parser deliberately — and a new
  dependency needs asking first (see `CLAUDE.md`, *Dependencies*).

## References
- `claude-code-enforcer/src/main/java/io/github/adamw7/tools/enforcer/text/`
  — `MarkdownDocument`, `FrontMatter`, `MarkdownText`, `FrontMatterFixer`
- `.../enforcer/doc/ImportGraph.java`, `.../enforcer/settings/CommandTokens.java`
- `adopt/src/main/java/io/github/adamw7/tools/adopt/step/ClaudeMdConformer.java`
  and `ClaudeMdConformerContractTest`
- The javadoc on each reader records *why* each invariant exists — read it before
  changing the code under it
- Related skills: `enforcer-rules`, `adopt-pipeline`, `java-code-review`,
  `testing-conventions`
