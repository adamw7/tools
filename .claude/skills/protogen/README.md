# Protogen

**Load**: `view .claude/skills/protogen/SKILL.md`

---

## Description

Helps Claude configure and use the `protogen-maven-plugin` — the plugin that
generates protobuf builders which catch missing `required` fields at **compile
time** instead of runtime. Covers the plugin wiring, the generated builder
chain, and the proto2/proto3/`oneof` handling.

---

## Use Cases

- "Wire protogen into this module's pom"
- "Why won't my generated builder compile until I set id?"
- "How is proto3 presence handled?" / "What does a oneof generate?"

---

## Examples

```
> view .claude/skills/protogen/SKILL.md
> "Add the protogen plugin to code/context"
→ Bind the code-generator goal to generate-sources; set generatedSourcesDir,
  pkgs, and outputpackage; add the output dir as a source root
```

---

## Notes / Tips

- Generate the protobuf classes first — the plugin scans the compiled messages.
- `mvn clean` after deleting a `.proto`, so stale builders can't mask the change.
