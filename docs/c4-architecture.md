# C4 Architecture — `tools`

This document describes the architecture of the `tools` repository using the
[C4 model](https://c4model.com/) (Context → Container → Component). Diagrams are
written in [Mermaid](https://mermaid.js.org/) C4 syntax and render directly on
GitHub.

`tools` is a multi-module Maven library of Java tooling. Its notable
capabilities are compile-time-safe protobuf **code generation**, **context
engineering** for gen-AI agents working with Java code, and a **data** toolkit
(data sources, a uniqueness/key finder, and data structures). Two of the modules
also ship **MCP servers** (Spring Boot apps) so AI assistants can call the tools
directly.

---

## Level 1 — System Context

How the `tools` system relates to its users and the external systems it depends
on.

```mermaid
C4Context
    title System Context — tools

    Person(dev, "Java Developer", "Builds apps using the library, the protogen Maven plugin and the gRPC example")
    Person(agent, "AI Agent / Assistant", "Calls the MCP servers to analyse Java projects and check data uniqueness")

    System_Boundary(tools, "tools (multi-module Maven project)") {
        System(toolsSys, "tools", "Java tooling: code generation, context engineering, data sources & uniqueness, CLAUDE.md enforcement")
    }

    System_Ext(mavenCentral, "Maven Central", "Publishes & resolves released artifacts")
    System_Ext(projectSrc, "Java Project Sources", "A target project's source tree, scanned for context")
    System_Ext(db, "Relational Database", "JDBC-accessible data source")
    System_Ext(files, "Data Files", "CSV / JSON / YAML / TOON / GZip inputs")
    System_Ext(ci, "GitHub Actions CI", "Builds, tests and runs the CLAUDE.md enforcer")

    Rel(dev, toolsSys, "Adds as dependency / runs the Maven plugin", "Maven")
    Rel(agent, toolsSys, "Invokes tools", "MCP (stdio / streamable HTTP)")
    Rel(toolsSys, mavenCentral, "Resolves deps / is published to", "HTTPS")
    Rel(toolsSys, projectSrc, "Scans classes & builds project tree", "File I/O")
    Rel(toolsSys, db, "Reads rows", "JDBC")
    Rel(toolsSys, files, "Reads / streams records", "File I/O")
    Rel(ci, toolsSys, "Builds & enforces", "mvn -B package -DenforceClaudeMd")
```

---

## Level 2 — Containers (Maven modules)

Each Maven module is treated as a container. The two MCP servers are runnable
Spring Boot applications; the other modules are libraries/plugins.

```mermaid
C4Container
    title Container View — tools modules

    Person(dev, "Java Developer")
    Person(agent, "AI Agent / Assistant")

    System_Boundary(tools, "tools") {
        Container(enforcer, "claude-code-enforcer", "Java / maven-enforcer rules", "Fails the build when CLAUDE.md / AGENTS.md / skills / settings are missing or malformed")

        Container(protogen, "code/protogen-maven-plugin", "Maven plugin (Java 25)", "Generates proto2 builders that detect missing required fields at compile time")

        Container(context, "code/context", "Java library + Spring Boot MCP server", "Regex-based class-usage finder + ProjectTreeBuilder; exposes project_tree, find_context, estimate_tokens")

        Container(data, "data", "Java library + Spring Boot MCP server", "Data sources, uniqueness/key finder, OpenAddressingMap; exposes the uniqueness checker as a tool")

        Container(grpc, "grpc-example", "Java example", "End-to-end gRPC demo using the compile-time-safe generated builders")

        Container(assembly, "assembly", "Executable jar-with-dependencies", "Bundles SampleApp (io.github.adamw7.tools.data.SampleApp)")
    }

    System_Ext(projectSrc, "Java Project Sources")
    System_Ext(db, "Relational Database")
    System_Ext(files, "Data Files")

    Rel(dev, protogen, "Configures in pom / runs", "Maven")
    Rel(dev, grpc, "Studies / runs the example")
    Rel(dev, assembly, "Runs SampleApp")
    Rel(agent, context, "project_tree / find_context / estimate_tokens", "MCP")
    Rel(agent, data, "check uniqueness", "MCP")

    Rel(protogen, grpc, "Generates builders consumed by", "compile time")
    Rel(assembly, data, "Bundles", "Maven")
    Rel(context, projectSrc, "Scans", "File I/O")
    Rel(data, db, "Reads", "JDBC")
    Rel(data, files, "Reads / streams", "File I/O")
```

---

## Level 3 — Components: `data` module

Key components inside the `data` module and how they collaborate.

```mermaid
C4Component
    title Component View — data module

    Person(agent, "AI Agent / Assistant")

    Container_Boundary(data, "data") {
        Component(mcpMain, "Main + McpConfiguration", "Spring Boot", "Boots the uniqueness MCP server (stdio / streamable-http)")
        Component(uniqTool, "UniquenessTool", "MCP tool", "Adapts the uniqueness checker to an MCP tool call")

        Component(uniqApi, "Uniqueness / AbstractUniqueness", "Interface + base", "Contract for uniqueness checks")
        Component(inMem, "InMemoryUniquenessCheck", "Component", "Checks key uniqueness holding data in memory")
        Component(noMem, "NoMemoryUniquenessCheck", "Component", "Streaming uniqueness check, no full in-memory load")
        Component(keyFinder, "KeyFinder", "Component", "Searches for a smaller column subset that is still a key")
        Component(result, "Key / Result", "Value objects", "Outcome of uniqueness checks")

        Component(srcIfc, "IterableDataSource / InMemoryDataSource", "Interfaces", "Abstractions over record sources")
        Component(fileSrc, "File sources", "CSV / JSON / YAML / TOON", "In-memory & iterative file readers")
        Component(dbSrc, "SQL sources", "JDBC", "InMemory / Iterable SQL data sources")
        Component(compression, "ZipUtils", "Component", "GZip support")
        Component(structure, "OpenAddressingMap", "Data structure", "Open-addressing Map implementation")
    }

    System_Ext(db, "Relational Database")
    System_Ext(files, "Data Files")

    Rel(agent, mcpMain, "Connects", "MCP")
    Rel(mcpMain, uniqTool, "Exposes")
    Rel(uniqTool, uniqApi, "Invokes")
    Rel(uniqApi, inMem, "Implemented by")
    Rel(uniqApi, noMem, "Implemented by")
    Rel(uniqApi, keyFinder, "Used by")
    Rel(uniqApi, result, "Produces")
    Rel(inMem, srcIfc, "Reads from")
    Rel(noMem, srcIfc, "Reads from")
    Rel(srcIfc, fileSrc, "Implemented by")
    Rel(srcIfc, dbSrc, "Implemented by")
    Rel(fileSrc, compression, "Uses")
    Rel(fileSrc, files, "Reads")
    Rel(dbSrc, db, "Reads", "JDBC")
```

---

## Level 3 — Components: `code/context` module

Key components inside the context-engineering module.

```mermaid
C4Component
    title Component View — code/context module

    Person(agent, "AI Agent / Assistant")

    Container_Boundary(context, "code/context") {
        Component(mcpMain, "Main + McpConfiguration", "Spring Boot", "Boots the context MCP server (stdio / streamable-http), with PathPolicy & TLS config")
        Component(treeTool, "ProjectTreeTool", "MCP tool", "Exposes project_tree")
        Component(finderTool, "ContextFinderTool", "MCP tool", "Exposes find_context")
        Component(tokenTool, "EstimateTokensTool", "MCP tool", "Exposes estimate_tokens")

        Component(finder, "Finder / AbstractFinder / PackageAwareFinder", "Component", "Regex-based class-usage finder")
        Component(context_, "Context / BudgetedContext / ContextFactory", "Component", "Assembles (budgeted) context for a class")
        Component(treeBuilder, "ProjectTreeBuilder", "Component", "Scans a project into a tree of folders, files & dependencies")
        Component(treeNode, "ProjectTreeNode", "Model", "Tree node")
        Component(serializers, "ProjectTree*Serializer", "Component", "JSON / Markdown / DOT / printer output")
        Component(tokens, "TokenEstimator impls", "Component", "Heuristic & subword token estimation")
        Component(sources, "ProjectSources / Language", "Component", "Source discovery & language detection")
    }

    System_Ext(projectSrc, "Java Project Sources")

    Rel(agent, mcpMain, "Connects", "MCP")
    Rel(mcpMain, treeTool, "Exposes")
    Rel(mcpMain, finderTool, "Exposes")
    Rel(mcpMain, tokenTool, "Exposes")

    Rel(treeTool, treeBuilder, "Uses")
    Rel(finderTool, context_, "Uses")
    Rel(finderTool, finder, "Uses")
    Rel(tokenTool, tokens, "Uses")

    Rel(treeBuilder, treeNode, "Builds")
    Rel(treeBuilder, serializers, "Serialized by")
    Rel(treeBuilder, sources, "Uses")
    Rel(finder, sources, "Uses")
    Rel(context_, finder, "Uses")

    Rel(finder, projectSrc, "Scans", "File I/O")
    Rel(treeBuilder, projectSrc, "Scans", "File I/O")
```

---

## Notes

- **Base package:** `io.github.adamw7` (`io.github.adamw7.context` for the
  context module, `io.github.adamw7.tools.*` elsewhere).
- **MCP servers:** both are Spring Boot apps whose entry point is `Main.java`
  and support stdio (default) or `--transport.mode=streamable-http`.
- **Build:** Java 25 + Maven 3.9.x; `mvn clean install` from the root. CI runs
  `mvn -B package -DenforceClaudeMd`, which also runs the `claude-code-enforcer`
  rules.
- The `data-test` module is built separately and is intentionally not in the
  root reactor `<modules>` list, so it is omitted from the container view.

See [AGENTS.md](../AGENTS.md) and [README.md](../README.md) for full detail.
