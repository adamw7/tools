---
name: protogen
description: Generate compile-time-safe protobuf builders with the protogen-maven-plugin — proto2 required-field enforcement, proto3 presence-aware accessors, and oneof discriminators. Use when configuring the plugin, reasoning about the generated builder chain, or when the user says "generate builders", "protobuf builder", "required field", or "shift-left validation".
---

# Protogen Skill

Generate and use the compile-time-safe protobuf builders produced by
`code/protogen-maven-plugin`. The point of this module is **shift-left**: stock
protobuf builders only detect a missing `required` field at runtime (an
`UninitializedMessageException` from `build()`), while the generated builder
chain makes the same mistake **fail to compile**.

## When to Use
- Wiring `protogen-maven-plugin` into a `pom.xml`
- Explaining or debugging why generated builders won't compile until every
  required field is set
- Questions about proto2 vs proto3 handling, presence accessors, or `oneof`
- The user says "generate builders" / "protobuf builder" / "required field" /
  "shift-left validation"

## How the plugin is wired
The plugin runs **after** protobuf classes exist, so it consumes the compiled
`*.proto` output and emits builder sources. Bind its `code-generator` goal to a
generate-sources phase, point it at the packages holding the generated protobuf
messages, and name an output package for the builders:

```xml
<plugin>
    <groupId>io.github.adamw7</groupId>
    <artifactId>protogen-maven-plugin</artifactId>
    <!-- Use the latest release: https://github.com/adamw7/tools/releases/latest -->
    <configuration>
        <generatedSourcesDir>${project.basedir}/target/generated-sources/</generatedSourcesDir>
        <pkgs>
            <param>io.github.adamw7.tools.code.protos</param>
        </pkgs>
        <outputpackage>io.github.adamw7.tools.code.builders</outputpackage>
    </configuration>
    <executions>
        <execution>
            <phase>generate-sources</phase>
            <goals>
                <goal>code-generator</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

Mojo goal / parameters (`CodeMojo`, `defaultPhase = GENERATE_SOURCES`):

| Parameter | Property | Meaning |
|---|---|---|
| `generatedSourcesDir` | `generatedsourcesdir` | Where the builder sources are written |
| `pkgs` | `pkgs` | Packages to scan for compiled protobuf messages |
| `outputpackage` | `outputpackage` | Package the generated builders go into |

Add the output directory as a source root (e.g. `build-helper-maven-plugin`'s
`add-source` / `add-test-source`) so the builders are compiled with the rest of
the module.

## What the generated chain guarantees
For a message with `required` fields, the plugin emits a chain of single-method
interfaces so each required setter returns the interface exposing only the
*next* required setter. `build()` appears only after the last required field, so
you cannot call it early — the missing-field bug is now a compile error:

```java
// required: id, department  →  both must be set before build() is reachable
Person person = builder.setId(1).setDepartment("dep")
                       .setEmail("sth@sth.net").setName("Adam").build();
```

## proto2 vs proto3 (the rules that trip people up)
- **proto2**: every `required` field is enforced by the builder chain; every
  singular field tracks presence, so all get a `hasXxx()` accessor.
- **proto3**: has no `required` fields, so the builder is all-optional — there is
  nothing to enforce. `hasXxx()` is generated **only** for message fields and
  fields declared with the explicit `optional` keyword. Implicit-presence proto3
  scalars have no `hasXxx()` and are left alone.
- **`oneof`**: gets a `getXxxCase()` accessor returning protobuf's generated
  `XxxCase` enum plus a `clearXxx()` that resets the whole group — both reachable
  through the fluent chain. The synthetic oneofs backing proto3 `optional` fields
  are **not** treated as groups, so no spurious case accessor is generated.

## Gotchas
- Run `mvn clean …` after removing a `.proto` source, so stale builders in
  `target/` cannot mask the change (repo-wide clean-after-codegen rule).
- The plugin needs the compiled protobuf classes on the runtime classpath
  (`requiresDependencyResolution = RUNTIME`); generate the `*.proto` first.

## References
- `README.md` — *Code generation* (worked proto2 example + generated chain)
- `docs/compile-time-safe-builders.md` — visual walkthrough of the chain
- `code/protogen-maven-plugin-test/pom.xml` — a working end-to-end configuration
- `AGENTS.md` — *Code generation* summary (source of truth)
