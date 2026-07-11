# C4 Architecture — `tools`

This document describes the architecture of the `tools` repository using the
[C4 model](https://c4model.com/) (Context → Container → Component). Diagrams are
written in [Mermaid](https://mermaid.js.org/) flowchart syntax, styled with the
standard C4 colour scheme, and render directly on GitHub.

`tools` is a multi-module Maven library of Java tooling. Its notable
capabilities are compile-time-safe protobuf **code generation**, **context
engineering** for gen-AI agents working with Java code, and a **data** toolkit
(data sources, a uniqueness/key finder, and data structures). Two of the modules
also ship **MCP servers** (Spring Boot apps) so AI assistants can call the tools
directly.

> **Legend** —
> 🟦 dark&nbsp;blue = person ·
> 🔵 blue = the `tools` system ·
> 🔹 light&nbsp;blue = container (Maven module / app) ·
> 🟪 purple = MCP server ·
> ⬜ grey = external system

---

## Level 1 — System Context

How the `tools` system relates to its users and the external systems it depends
on.

```mermaid
flowchart TB
    dev["👤 Java Developer<br/><i>Builds apps with the library,<br/>the protogen plugin & the gRPC example</i>"]
    agent["👤 AI Agent / Assistant<br/><i>Calls the MCP servers</i>"]

    subgraph sys [" "]
        tools["<b>tools</b><br/>Java tooling: code generation,<br/>context engineering, data &amp;<br/>uniqueness, CLAUDE.md enforcement"]
    end

    mavenCentral["📦 Maven Central<br/><i>Resolves / publishes artifacts</i>"]
    projectSrc["🗂️ Java Project Sources<br/><i>Scanned for context</i>"]
    db["🛢️ Relational Database<br/><i>JDBC data source</i>"]
    files["📄 Data Files<br/><i>CSV / JSON / YAML / TOON / GZip</i>"]
    ci["⚙️ GitHub Actions CI<br/><i>Builds, tests, enforces CLAUDE.md</i>"]

    dev -->|"Adds as dependency /<br/>runs the Maven plugin"| tools
    agent -->|"Invokes tools<br/>(MCP: stdio / HTTP)"| tools
    tools -->|"Resolves deps /<br/>published to (HTTPS)"| mavenCentral
    tools -->|"Scans classes &amp;<br/>builds project tree"| projectSrc
    tools -->|"Reads rows (JDBC)"| db
    tools -->|"Reads / streams records"| files
    ci -->|"mvn -B package<br/>-DenforceClaudeMd"| tools

    classDef person fill:#08427b,stroke:#052e56,color:#fff
    classDef system fill:#1168bd,stroke:#0b4884,color:#fff
    classDef ext fill:#999999,stroke:#6b6b6b,color:#fff
    class dev,agent person
    class tools system
    class mavenCentral,projectSrc,db,files,ci ext
    style sys fill:none,stroke:none
```

---

## Level 2 — Containers (Maven modules)

Each Maven module is a container. The two MCP servers are runnable Spring Boot
applications (purple); the other modules are libraries / plugins.

```mermaid
flowchart TB
    dev["👤 Java Developer"]
    agent["👤 AI Agent / Assistant"]

    subgraph tools ["tools  (multi-module Maven project)"]
        direction TB

        enforcer["<b>claude-code-enforcer</b><br/><i>maven-enforcer rules</i><br/>Fails the build on malformed<br/>CLAUDE.md / AGENTS.md / skills / settings"]
        protogen["<b>code/protogen-maven-plugin</b><br/><i>Maven plugin</i><br/>Generates proto2 builders that catch<br/>missing required fields at compile time"]
        grpc["<b>grpc-example</b><br/><i>Java example</i><br/>End-to-end gRPC demo using the<br/>compile-time-safe builders"]
        assembly["<b>assembly</b><br/><i>Executable jar</i><br/>Bundles SampleApp"]

        context["<b>code/context</b><br/><i>Library + MCP server</i><br/>Class-usage finder + ProjectTreeBuilder"]
        contextMcp(["🟪 Context MCP server<br/><i>Spring Boot · stdio / HTTP</i><br/>project_tree · find_context · estimate_tokens"])

        data["<b>data</b><br/><i>Library + MCP server</i><br/>Data sources · uniqueness/key finder ·<br/>OpenAddressingMap · OpenAddressingSet · IntKeyOpenAddressingMap"]
        dataMcp(["🟪 Uniqueness MCP server<br/><i>Spring Boot · stdio / HTTP</i><br/>check uniqueness"])

        mcpCommon["<b>mcp-common</b><br/><i>Shared library</i><br/>MCP scaffolding: AbstractMcpConfiguration ·<br/>McpTool · TransportConfigurer"]

        context --- contextMcp
        data --- dataMcp
        contextMcp -.->|"builds on"| mcpCommon
        dataMcp -.->|"builds on"| mcpCommon
    end

    projectSrc["🗂️ Java Project Sources"]
    db["🛢️ Relational Database"]
    files["📄 Data Files"]

    dev -->|"Configures / runs"| protogen
    dev -->|"Studies / runs"| grpc
    dev -->|"Runs SampleApp"| assembly
    agent -->|"MCP"| contextMcp
    agent -->|"MCP"| dataMcp

    protogen -.->|"generates builders<br/>consumed by"| grpc
    assembly -.->|"bundles"| data
    contextMcp --> context
    dataMcp --> data
    context -->|"scans"| projectSrc
    data -->|"reads (JDBC)"| db
    data -->|"reads / streams"| files

    classDef person fill:#08427b,stroke:#052e56,color:#fff
    classDef container fill:#438dd5,stroke:#2e6295,color:#fff
    classDef mcp fill:#6b3fa0,stroke:#46296b,color:#fff
    classDef ext fill:#999999,stroke:#6b6b6b,color:#fff
    class dev,agent person
    class enforcer,protogen,grpc,assembly,context,data,mcpCommon container
    class contextMcp,dataMcp mcp
    class projectSrc,db,files ext
    style tools fill:#f2f7fc,stroke:#438dd5,color:#08427b
```

---

## Level 3 — Components: `data` module

Key components inside the `data` module and how they collaborate.

```mermaid
flowchart TB
    agent["👤 AI Agent / Assistant"]
    dev["👤 Java Developer"]

    subgraph data ["data module"]
        direction TB

        subgraph mcpLayer ["MCP server"]
            mcpMain["<b>Main + McpConfiguration</b><br/><i>Spring Boot</i><br/>stdio / streamable-http"]
            uniqTool["<b>UniquenessTool</b><br/><i>MCP tool</i>"]
        end

        subgraph uniq ["Uniqueness"]
            uniqApi["<b>Uniqueness / AbstractUniqueness</b><br/><i>contract</i>"]
            inMem["<b>InMemoryUniquenessCheck</b>"]
            noMem["<b>NoMemoryUniquenessCheck</b><br/><i>streaming</i>"]
            keyFinder["<b>KeyFinder</b><br/><i>finds a smaller key</i>"]
            result["<b>Key / Result</b><br/><i>value objects</i>"]
        end

        subgraph srcs ["Data sources"]
            srcIfc["<b>Iterable / InMemory<br/>DataSource</b><br/><i>interfaces</i>"]
            fileSrc["<b>File sources</b><br/>CSV · JSON · YAML · TOON"]
            dbSrc["<b>SQL sources</b><br/><i>JDBC</i>"]
            compression["<b>ZipUtils</b><br/><i>GZip</i>"]
        end

        subgraph struct ["Data structures — standalone public API"]
            structure["<b>OpenAddressingMap</b><br/>OpenAddressingSet · IntKeyOpenAddressingMap"]
        end
    end

    db["🛢️ Relational Database"]
    files["📄 Data Files"]

    agent -->|"MCP"| mcpMain
    mcpMain --> uniqTool
    uniqTool --> uniqApi
    uniqApi --> inMem
    uniqApi --> noMem
    uniqApi --> keyFinder
    uniqApi --> result
    inMem --> srcIfc
    noMem --> srcIfc
    srcIfc --> fileSrc
    srcIfc --> dbSrc
    fileSrc --> compression
    fileSrc --> files
    dbSrc -->|"JDBC"| db
    dev -->|"Uses as collections<br/>(open-addressing)"| structure

    classDef person fill:#08427b,stroke:#052e56,color:#fff
    classDef comp fill:#85bbf0,stroke:#5d82a8,color:#08427b
    classDef ext fill:#999999,stroke:#6b6b6b,color:#fff
    class agent,dev person
    class mcpMain,uniqTool,uniqApi,inMem,noMem,keyFinder,result,srcIfc,fileSrc,dbSrc,compression,structure comp
    class db,files ext
    style data fill:#f2f7fc,stroke:#438dd5,color:#08427b
    style mcpLayer fill:#eef4ec,stroke:#6b3fa0,color:#46296b
    style uniq fill:#fff7ec,stroke:#d59a43,color:#7a5418
    style srcs fill:#fdf0f0,stroke:#d56b6b,color:#7a3030
    style struct fill:#eef4ec,stroke:#6b8e6b,color:#2f5230
```

---

## Level 3 — Components: `code/context` module

Key components inside the context-engineering module.

```mermaid
flowchart TB
    agent["👤 AI Agent / Assistant"]

    subgraph context ["code/context module"]
        direction TB

        subgraph mcpLayer ["MCP server"]
            mcpMain["<b>Main + McpConfiguration</b><br/><i>Spring Boot · PathPolicy · TLS</i>"]
            treeTool["<b>ProjectTreeTool</b><br/><i>project_tree</i>"]
            finderTool["<b>ContextFinderTool</b><br/><i>find_context</i>"]
            tokenTool["<b>EstimateTokensTool</b><br/><i>estimate_tokens</i>"]
        end

        subgraph core ["Core"]
            finder["<b>Finder / AbstractFinder /<br/>PackageAwareFinder</b><br/><i>regex class-usage finder</i>"]
            context_["<b>Context / BudgetedContext /<br/>ContextFactory</b>"]
            treeBuilder["<b>ProjectTreeBuilder</b><br/><i>scans project → tree</i>"]
            treeNode["<b>ProjectTreeNode</b><br/><i>model</i>"]
            serializers["<b>ProjectTree*Serializer</b><br/>JSON · Markdown · DOT · printer"]
            tokens["<b>TokenEstimator impls</b><br/>heuristic · subword"]
            sources["<b>ProjectSources / Language</b>"]
        end
    end

    projectSrc["🗂️ Java Project Sources"]

    agent -->|"MCP"| mcpMain
    mcpMain --> treeTool
    mcpMain --> finderTool
    mcpMain --> tokenTool

    treeTool --> treeBuilder
    finderTool --> context_
    finderTool --> finder
    tokenTool --> tokens

    treeBuilder --> treeNode
    treeBuilder --> serializers
    treeBuilder --> sources
    finder --> sources
    context_ --> finder

    finder -->|"scans"| projectSrc
    treeBuilder -->|"scans"| projectSrc

    classDef person fill:#08427b,stroke:#052e56,color:#fff
    classDef comp fill:#85bbf0,stroke:#5d82a8,color:#08427b
    classDef ext fill:#999999,stroke:#6b6b6b,color:#fff
    class agent person
    class mcpMain,treeTool,finderTool,tokenTool,finder,context_,treeBuilder,treeNode,serializers,tokens,sources comp
    class projectSrc ext
    style context fill:#f2f7fc,stroke:#438dd5,color:#08427b
    style mcpLayer fill:#eef4ec,stroke:#6b3fa0,color:#46296b
    style core fill:#fff7ec,stroke:#d59a43,color:#7a5418
```

---

## Level 3 — Components: `code/protogen-maven-plugin`

How the code-generation pipeline turns compiled proto message classes into
compile-time-safe builders, run in the `generate-sources` phase.

```mermaid
flowchart TB
    dev["👤 Java Developer<br/><i>Configures the plugin in pom.xml</i>"]

    subgraph protogen ["code/protogen-maven-plugin"]
        direction TB

        subgraph entry ["Mojo"]
            mojo["<b>CodeMojo</b><br/><i>@Mojo · generate-sources</i><br/>pkgs · outputpackage · generatedSourcesDir<br/>extends the runtime classpath"]
        end

        subgraph gen ["Generation core"]
            finder["<b>MessagesFinder</b><br/><i>scans pkgs for<br/>GeneratedMessage classes</i>"]
            code["<b>Code</b><br/><i>orchestrates genBuilders</i><br/>proto2/proto3 syntax check"]
            typeMap["<b>TypeMappings</b><br/><i>proto → Java types</i>"]
            clazz["<b>Clazz / ClassInfo</b><br/><i>reads the Descriptor</i>"]
            emit["<b>Interfaces · Methods ·<br/>Implementations · Statements</b><br/><i>emit required-field builder</i>"]
            container["<b>ClassContainer</b><br/><i>generated compilation unit</i>"]
        end

        subgraph fmt ["Formatting"]
            formatter["<b>Formatter /<br/>UnusedImportsRemover</b>"]
        end
    end

    protoClasses["📦 Compiled proto classes<br/><i>com.google.protobuf.GeneratedMessage</i>"]
    genSources["🗂️ Generated sources dir<br/><i>*.java builders (compiled next)</i>"]

    dev -->|"mvn generate-sources"| mojo
    mojo -->|"execute()"| finder
    finder -->|"reflects"| protoClasses
    mojo --> code
    finder --> code
    code --> typeMap
    code --> clazz
    clazz --> emit
    emit --> container
    container --> formatter
    formatter -->|"writes .java"| genSources

    classDef person fill:#08427b,stroke:#052e56,color:#fff
    classDef comp fill:#85bbf0,stroke:#5d82a8,color:#08427b
    classDef ext fill:#999999,stroke:#6b6b6b,color:#fff
    class dev person
    class mojo,finder,code,typeMap,clazz,emit,container,formatter comp
    class protoClasses,genSources ext
    style protogen fill:#f2f7fc,stroke:#438dd5,color:#08427b
    style entry fill:#eef4ec,stroke:#6b3fa0,color:#46296b
    style gen fill:#fff7ec,stroke:#d59a43,color:#7a5418
    style fmt fill:#fdf0f0,stroke:#d56b6b,color:#7a3030
```

---

## Deployment — `k8s/` (uniqueness check on Kubernetes)

An optional C4 deployment view of the assets under `k8s/`, which run `SampleApp`
(the CSV column-uniqueness checker) as a run-to-completion **Job** on a local
minikube cluster.

```mermaid
flowchart TB
    dev["👤 Java Developer<br/><i>./k8s/run-on-minikube.sh</i>"]

    subgraph build ["Build host (docker + Maven)"]
        maven["<b>mvn package</b><br/><i>assembly fat jar</i>"]
        image["<b>tools-k8s image</b><br/><i>k8s/Dockerfile</i><br/>flat classpath + console log4j2"]
    end

    subgraph cluster ["minikube cluster"]
        direction TB
        subgraph job ["Job: tools-uniqueness-check"]
            pod["<b>Pod</b><br/><i>SampleApp (batch, restartPolicy: Never)</i><br/>args: /data/people.csv · country"]
        end
        cm["🗂️ ConfigMap<br/><i>tools-sample-data</i><br/>people.csv → /data"]
    end

    logs["📄 kubectl logs<br/><i>uniqueness result → stdout</i>"]

    dev -->|"build & load"| maven
    maven --> image
    image -->|"minikube image load"| pod
    cm -->|"mounted read-only at /data"| pod
    pod -->|"logs result"| logs

    classDef person fill:#08427b,stroke:#052e56,color:#fff
    classDef comp fill:#85bbf0,stroke:#5d82a8,color:#08427b
    classDef ext fill:#999999,stroke:#6b6b6b,color:#fff
    class dev person
    class maven,image,pod,cm comp
    class logs ext
    style build fill:#f2f7fc,stroke:#438dd5,color:#08427b
    style cluster fill:#eef4ec,stroke:#326ce5,color:#08427b
    style job fill:#fff7ec,stroke:#d59a43,color:#7a5418
```

---

## Notes

- **Base package:** `io.github.adamw7` (`io.github.adamw7.context` for the
  context module, `io.github.adamw7.tools.*` elsewhere).
- **MCP servers:** both are Spring Boot apps whose entry point is `Main.java`
  and support stdio (default) or `--transport.mode=streamable-http`. Both build
  on the shared **`mcp-common`** module (`AbstractMcpConfiguration`, `McpTool`,
  `TransportConfigurer`).
- **Build:** Java 25 + Maven 3.9.x; `mvn clean install` from the root. CI runs
  `mvn -B package -DenforceClaudeMd`, which also runs the `claude-code-enforcer`
  rules.
- The `data-test` module is built separately and is intentionally not in the
  root reactor `<modules>` list, so it is omitted from the container view.
- **Deployment:** `k8s/` packages `SampleApp` into the `tools-k8s` image
  (`k8s/Dockerfile`) and runs it as a Kubernetes **Job**
  (`job-uniqueness-check.yaml`) reading a CSV from a ConfigMap — see the
  Deployment diagram above and `k8s/README.md`.

See [AGENTS.md](../AGENTS.md) and [README.md](../README.md) for full detail.
