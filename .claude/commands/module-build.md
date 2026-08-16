---
description: Build or test one module of this reactor with the flags it actually needs.
argument-hint: <module> [test|install|verify]
allowed-tools: Bash(mvn *), Read, Grep, Glob
---

Build the module named in `$ARGUMENTS`, at the lifecycle phase it names (default
`install`).

```bash
mvn -pl <module> -am <phase>
```

**`-pl` is always paired with `-am`.** Without it the build fails before
compiling anything: the root pom's `ReactorModuleConvergence` rule rejects a
reactor whose modules' parents are absent from it, and sibling `-SNAPSHOT`s such
as `mcp-common` do not resolve from the local repository until they are
installed. If you see a missing parent or an unresolved sibling, that is this
mistake and not the module's code.

Resolve the module name against the root pom's `<modules>` before running
anything. Two are worth knowing:

- `code` is an aggregator; its real modules are `code/protogen-maven-plugin`,
  `code/protogen-maven-plugin-test` and `code/context`.
- `data-test` is deliberately outside the root reactor, so it is built from its
  own directory rather than with `-pl`.

If the request is really one test rather than a module, use one of these instead
— `-Dtest` applies to every module in the reactor, so surefire fails the upstream
ones where nothing matches:

```bash
cd <module> && mvn test -Dtest='SomeTest#someMethod'
mvn -pl <module> -am test -Dtest=SomeTest -Dsurefire.failIfNoSpecifiedTests=false
```

The first form needs `mvn install -DskipTests` to have run once, since a module
directory resolves its siblings from the local repository rather than the
reactor.

Add `clean` when the change removed a code-generation source, so a stale
generated builder in `target/` cannot mask it.

Report the verdict and, on a failure, the module and test that failed with the
assertion message — not the reactor summary.
