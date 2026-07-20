# Maven Conventions

**Load**: `view .claude/skills/maven-conventions/SKILL.md`

---

## Description

Helps Claude edit `pom.xml` files and run builds the way this repo expects:
versions only in the root pom, version-free module poms, the right profiles, and
clean-after-codegen.

---

## Use Cases

- "Add a dependency to the data module"
- "Bump the version of X"
- "Which command runs the integration tests?"

---

## Examples

```
> view .claude/skills/maven-conventions/SKILL.md
> "Bump the log4j2 version"
→ Change it in root <dependencyManagement>; module poms stay version-free
```

---

## Notes / Tips

- Always ask before adding a brand-new dependency.
