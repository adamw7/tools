# tools

Library of tooling for various purposes.

## Table of Contents

- [Claude Code files Maven enforcer](#claude-code-files-maven-enforcer)
- [Code generation](#code-generation)
- [gRPC example](#grpc-example)
- [Context engineering](#context-engineering)
  - [Java code context build up](#java-code-context-build-up)
  - [Kotlin code context build up](#kotlin-code-context-build-up)
  - [Scala code context build up](#scala-code-context-build-up)
  - [Project tree](#project-tree)
  - [Output formats](#output-formats)
  - [Token-budget-aware context](#token-budget-aware-context)
- [Data](#data)
  - [Open-addressing map](#open-addressing-map)
  - [Open-addressing set](#open-addressing-set)
  - [Primitive int-keyed map](#primitive-int-keyed-map)
  - [Network kill-switch](#network-kill-switch)
- [Architecture tests (ArchUnit)](#architecture-tests-archunit)
- [Building](#building)
- [Releasing](#releasing)
- [License](#license)

## Claude Code files Maven enforcer

The `claude-code-enforcer` module is a set of custom
[`maven-enforcer-plugin`](https://maven.apache.org/enforcer/maven-enforcer-plugin/)
rules that **fail the build** when the repository's Claude Code files are
missing or malformed, keeping `CLAUDE.md`, `AGENTS.md`, `.claude/settings.json`,
the sub-agents under `.claude/agents`, and the skills under `.claude/skills`
consistent and in their expected shape:

- **`claudeMdFormat`** (`ClaudeMdFormatRule`) — checks that `CLAUDE.md` exists
  and is non-empty, starts with the `# CLAUDE.md` title (a leading UTF-8 BOM is
  tolerated), references `AGENTS.md`, and contains every required section
  heading.
- **`agentsMdFormat`** (`AgentsMdFormatRule`) — applies the same structural
  checks to `AGENTS.md`: it must start with the `# AGENTS.md` title and contain
  every required section heading.
- **`skillFilesExist`** (`SkillFilesExistRule`) — checks that every skill
  directory under `.claude/skills` contains a non-empty `SKILL.md` that opens
  with a YAML front matter block declaring every required key (`name`,
  `description` by default). The `name` must follow the Claude Code naming
  convention (lower-case kebab-case, bounded length) and match the skill's
  directory name; the `description` must be non-empty and within
  `maxDescriptionLength`. An optional `allowedFrontMatterKeys` whitelist catches
  typos such as `descripton`.
- **`subAgentFormat`** (`SubAgentFormatRule`) — treats every `*.md` file in the
  configured agents directory as a sub-agent: it must be non-empty, open with a
  YAML front matter block declaring every required key, and carry a `name` that
  follows the naming convention and matches its file name. An optional
  `allowedModels` whitelist rejects a mistyped `model` such as `claud-opus`.
- **`commandFormat`** (`CommandFormatRule`) — treats every `*.md` file in the
  configured commands directory (e.g. `.claude/commands`) as a custom slash
  command: it must be non-empty and carry a file name that follows the Claude
  Code naming convention, because the command's name comes from its file name.
  Front matter is optional, but when present a `description` must be non-empty, a
  `model` must be one of `allowedModels` when that whitelist is configured, and
  an optional `allowedFrontMatterKeys` whitelist catches typos such as
  `argument-hnt`.
- **`settingsJsonValid`** (`SettingsJsonValidRule`) — checks that
  `.claude/settings.json` exists, is non-empty, and parses as JSON. It can also
  assert policy on `permissions.allow`: `requiredPermissions` must all be
  present and `forbiddenPermissions` must all be absent, so a project can mandate
  a permission it relies on or ban an over-broad wildcard such as `Bash(*)`.
- **`hookCommandsValid`** (`HookCommandsValidRule`) — validates the `hooks`
  section of `.claude/settings.json`: every event must map to an array of groups,
  each group must carry a `hooks` array, and every hook must declare a non-blank
  `type` (a `command` hook also a non-blank `command`). A command that points at
  a project-local script through `$CLAUDE_PROJECT_DIR` is resolved against
  `projectDir` and must exist on disk, so a renamed or missing hook script is
  caught. An optional `allowedEvents` whitelist rejects a mistyped event such as
  `SessionSart`, and `validateScriptReferences` can switch the script check off.
- **`mcpServersValid`** (`McpServersValidRule`) — validates the project's
  `.mcp.json`. A project-level MCP file is optional, so an absent file passes;
  when present it must be non-empty and parse as JSON, and every entry under
  `mcpServers` must be a JSON object with a well-formed transport. A `stdio`
  server (the default when no `type` is declared) needs a non-blank `command`;
  an `sse` or `http` server needs a non-blank `url`. An explicit `type` outside
  the `allowedTypes` whitelist (`stdio`, `sse`, `http` by default) is reported,
  catching a mistyped `htttp`. `requiredServers` must all be present and
  `forbiddenServers` must all be absent, so a project can mandate an MCP server
  it relies on or ban one it does not want committed.
- **`mcpConfigFormat`** (`McpConfigFormatRule`) — validates the details of each
  `.mcp.json` server entry that `mcpServersValid` leaves unchecked: `args` must
  be an array of strings, `env` and `headers` must be objects whose values are
  all strings, a `url` must be a syntactically valid `http`/`https` URL (and
  `https` only when `requireHttps` is set), and a server must not mix transports
  by declaring both a `command` and a `url`. Like `mcpServersValid` it treats an
  absent file as a pass.
- **`hooksFormat`** (`HooksFormatRule`) — validates the hook scripts under a
  configured `hooksDir` (e.g. `.claude/hooks`): every regular file must be
  non-empty, start with a `#!` shebang (`requireShebang`), and carry the
  executable bit (`requireExecutable`), and an optional `allowedExtensions`
  whitelist rejects a stray file. Where `hookCommandsValid` validates the JSON
  shape of the `hooks` section, this rule validates the scripts themselves; when
  a `settingsFile` is configured it also cross-checks the wiring, so a command
  hook whose `$CLAUDE_PROJECT_DIR` path lands in the hooks directory must point
  at a script that exists there, and `reportUnreferencedScripts` flags a script
  no hook references. An absent `hooksDir` is a pass because hooks are optional.
- **`uniqueDescriptions`** (`UniqueDescriptionsRule`) — reads the `description`
  from the front matter of every sub-agent (`*.md`), command (`*.md`), and skill
  (`SKILL.md`) in the configured `commandsDir`, `agentsDir`, and `skillsDir`, and
  fails when one description is used by more than one definition, naming every
  file that uses it. Because Claude routes by matching intent against these
  descriptions, two identical descriptions are ambiguous and one shadows the
  other. Comparison ignores case and runs of whitespace; missing or blank
  descriptions are left to the format rules. As with `uniqueNames`, at least one
  directory must be configured and uniqueness is checked across all of them.
- **`uniqueNames`** (`UniqueNamesRule`) — gathers the names of every command,
  sub-agent, and skill from the configured `commandsDir`, `agentsDir`, and
  `skillsDir` (a command's and a sub-agent's name is its `*.md` file name, a
  skill's name is its directory name) and fails when one name is used more than
  once, naming every file or directory that uses it. At least one directory must
  be configured, and any directory that is configured must exist. Uniqueness is
  checked across all configured directories at once, so a command that clashes
  with a skill is caught just like two commands that clash.
- **`crossDocConsistency`** (`CrossDocConsistencyRule`) — keeps `CLAUDE.md` and
  `AGENTS.md` from contradicting each other. Each configured `consistentPattern`
  is a regular expression with one capturing group; the captured value must
  agree between the two files (or be absent from both). For example
  `Java (\d+)` fails the build if one file says `Java 25` and the other `Java
  24`.

The `claudeMdFormat` and `agentsMdFormat` rules share a `MarkdownFormatRule`
base class that performs the file-existence, BOM, title, and section checks. It
also exposes optional checks, each disabled by default: `forbiddenTokens` that
must not appear outside code fences, `enforceSectionOrder` to require the
sections in the configured order, a `maxLineLength` cap, and
`validateFileReferences` to confirm that Markdown links to local files resolve
to something on disk.

Every rule extends a common `ClaudeCodeEnforcerRule` base that reports all
violations together and honours a `severity` option: the default `error` fails
the build, while `<severity>warn</severity>` downgrades the same violations to a
logged warning so a team can adopt a rule gradually.

The front matter rules (`skillFilesExist`, `subAgentFormat`, `commandFormat`)
also accept an `autoFix` option. When it is enabled and a definition's front
matter is malformed in a way that is safe to repair — a delimiter written with
too many dashes such as `----`, or an opening `---` whose closing delimiter is
missing — the rule rewrites the file in place and continues against the
corrected content instead of failing the build. The repair is conservative: it
only acts when the document opens with a dashes line enclosing real
`key: value` entries, so a lone `---` thematic break is never mistaken for front
matter. `autoFix` is off by default.

The rules are wired into the root `pom.xml` and run at the repository root only.
The check is **opt-in** via the `enforceClaudeMd` property, so ordinary builds
are unaffected:
```
mvn -pl claude-code-enforcer -am install   # install the rule jar once
mvn package -DenforceClaudeMd            # build with the checks enabled
```


## Code generation

Problem:

Generated builder java code for protobuffers detects missing required fields in runtime.

Solution:

Move detection to compile time (shift-left).

Example of the problem:
```proto
syntax = "proto2";

package example;

option java_multiple_files = true;
option java_package = "io.github.adamw7.tools.code.protos";

message Person {
  optional string name = 1;
  required int32 id = 2;
  optional string email = 3;
  required string department = 4;
}
```
and the builder that allows building the object without setting the required field "Id":
```java
Person.Builder personBuilder = Person.newBuilder();

personBuilder.setEmail("email@sth.com");
personBuilder.setName("Adam");

UninitializedMessageException thrown = assertThrows(UninitializedMessageException.class, personBuilder::build, "Expected build method to throw, but it didn't");

assertEquals("Message missing required fields: id, department", thrown.getMessage());
```
Solution:
```xml
<plugin>
	<groupId>io.github.adamw7</groupId>
	<artifactId>protogen-maven-plugin</artifactId>
	<!-- Use the latest release: https://github.com/adamw7/tools/releases/latest -->
	<version>2.1.0</version>
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
that generates builders detecting missing required fields in compile time (some methods are excluded for simplicity of the example):
```java
interface OptionalIfc {
	OptionalIfc setEmail(String email);
	OptionalIfc setName(String name);
	Person build();
}

interface DepartmentIfc {
	OptionalIfc setDepartment(String department);
}

interface IdIfc {
	DepartmentIfc setId(int id);
}

class OptionalImpl implements OptionalIfc {
	
	private final Builder builder;

	public OptionalImpl(Builder builder) {
		this.builder = builder;
	}

	@Override
	public OptionalIfc setEmail(String email) {
		builder.setEmail(email);
		return this;
	}

	@Override
	public OptionalIfc setName(String name) {
		builder.setName(name);
		return this;
	}

	@Override
	public Person build() {
		return builder.build();
	}
}

class DepartmentImpl implements DepartmentIfc {

	private final Builder personOrBuilder;

	public DepartmentImpl(Builder personOrBuilder) {
		this.personOrBuilder = personOrBuilder;
	}

	@Override
	public OptionalIfc setDepartment(String department) {
		personOrBuilder.setDepartment(department);
		return new OptionalImpl(personOrBuilder);
	}	
}

public class ExampleTest {
	
	private static class PersonBuilderExample implements IdIfc {
		private final Builder personBuilder = Person.newBuilder();
		
		@Override
		public DepartmentIfc setId(int id) {
			personBuilder.setId(id);
			return new DepartmentImpl(personBuilder);
		}
	}
	
	@Test
	public void happyPath() {
		PersonBuilderExample builder = new PersonBuilderExample();
		Person person = builder.setId(1).setDepartment("dep").setEmail("sth@sth.net").setName("Adam").build();
		assertEquals(1, person.getId());
		assertEquals("dep", person.getDepartment());
		assertEquals("sth@sth.net", person.getEmail());
		assertEquals("Adam", person.getName());
		
	}
}
```
Since in proto3 there is no concept of required fields - this solution supports only proto2. 

## gRPC example

An end-to-end gRPC example combining standard protobuf/gRPC code generation with the compile-time-safe builder generation from this project.

Given [`greeter.proto`](grpc-example/src/main/proto/greeter.proto):
```proto
syntax = "proto2";

message HelloRequest {
  required string name = 1;
  optional string title = 2;
}

message HelloReply {
  required string message = 1;
}

service Greeter {
  rpc SayHello (HelloRequest) returns (HelloReply);
}
```

Two generators run during the build:
1. **`protobuf-maven-plugin`** compiles the proto definitions into protobuf message classes and gRPC service stubs (`GreeterGrpc`).
2. **`protogen-maven-plugin`** (this repo) generates compile-time-safe builders (`HelloRequestBuilder`, `HelloReplyBuilder`) that refuse to call `build()` until every `required` field is set.

The service implementation uses the generated builder:
```java
HelloReply reply = new HelloReplyBuilder().setMessage(greetingFor(request)).build();
```

> **Note:** All example code (`GreeterServiceImpl`, `GreeterServer`, `GreeterClient`) lives under `src/test/java` because the protogen-generated builders are written to `target/generated-test-sources`. Run the example with `mvn -pl grpc-example -am test`.

See the [grpc-example module](grpc-example/README.md) for full details and how to run the example.

## Context engineering

### Java code context build up

For gen ai agents that work with Java code the context usually starts with one class but may get wider and be extended to the classes used by it etc so on.
In order to build this tree there is a very simple and fast regex based interface:

```java
public interface Context {
    Set<ClassContainer> find(ClassContainer root, int depth);
}
```
where `ClassContainer` contains the path of the class and its sources.
The depth param tells the finder how deep we want to go in the tree of usages of the class.
Of course when the depth is growing the tree grows very fast.

The regex-based `Finder` is the default implementation. Pick the language with
the `Language` enum (`Language.JAVA` is the default):

```java
Context context = new Finder(allContainers, Language.JAVA);
Set<ClassContainer> used = context.find(root, depth);
```

`Language.JAVA` resolves `.java` files when mapping a referenced class name back
to its source file, so the usage-tree building works out of the box for Java
sources.

### Kotlin code context build up

Kotlin is supported with exactly the same features as Java. The regex-based
`Finder` and the `Context` interface are language agnostic; the only difference
is the source-file extension used to resolve a referenced class back to a file.
Pick the language with the `Language` enum:

```java
Context context = new Finder(allContainers, Language.KOTLIN);
Set<ClassContainer> used = context.find(root, depth);
```

`Language.JAVA` (the default) resolves `.java` files and `Language.KOTLIN`
resolves `.kt` files, so the same usage-tree building works for Kotlin sources.

### Scala code context build up

Scala is supported with exactly the same features as Java and Kotlin. The
regex-based `Finder` and the `Context` interface are language agnostic; the only
difference is the source-file extension used to resolve a referenced class back
to a file. Pick the language with the `Language` enum:

```java
Context context = new Finder(allContainers, Language.SCALA);
Set<ClassContainer> used = context.find(root, depth);
```

`Language.SCALA` resolves `.scala` files, so the same usage-tree building works
for Scala sources.

### Project tree

A single class is rarely enough context — an agent usually needs to see how a
whole project is laid out and how its files relate. `ProjectTreeBuilder` walks a
Java (or Kotlin or Scala) project directory and produces a tree of its **folders, files
and dependencies** in one structure:

```java
ProjectTreeNode root = new ProjectTreeBuilder(/* depth */ 1).build(Path.of("my-project"));
System.out.println(new ProjectTreePrinter().print(root));
```

For a Kotlin project just pass the language; everything else stays the same:

```java
ProjectTreeNode root = new ProjectTreeBuilder(Language.KOTLIN, /* depth */ 1).build(Path.of("my-project"));
System.out.println(new ProjectTreePrinter().print(root));
```

The building blocks are:

- **`ProjectTreeNode`** — a node in the tree. Each node is either a *directory*
  (mirroring a project folder and holding child nodes) or a *file*. File nodes
  additionally carry the set of project classes they depend on, so folders,
  files and dependencies are all described by the same tree.
- **`ProjectTreeBuilder`** — scans the project directory recursively. Every
  folder becomes a directory node and every file a file node (directories are
  listed before files, then alphabetically). For each source file (`.java`,
  `.kt` or `.scala`, depending on the configured `Language`) it resolves the
  project classes it uses with the same regex-based `Context`, skipping the
  file's own class.
  `depth` controls how deep the usage search goes, exactly as in the `Context`
  interface above.
- **`ContextFactory`** — decouples the builder from the concrete dependency
  finder (defaulting to `Finder`), so a different resolution strategy can be
  plugged in without changing the builder.
- **`ProjectTreePrinter`** — renders the tree as indented text, with each file's
  dependencies listed beneath it:

```
[dir] pkg
  [file] A.java
  [file] B.java
    -> A.java
```

The result is a compact, human- and LLM-readable view of the project, ready to
be handed to a gen-AI agent as context.

### Output formats

The tree can be rendered in several formats behind a single
`ProjectTreeSerializer` interface, so a consumer depends on the abstraction
rather than a concrete format and new formats can be added without touching the
tree:

```java
ProjectTreeSerializer serializer = new ProjectTreeMarkdownSerializer(); // or JSON / printer
String rendered = serializer.serialize(root);
```

- **`ProjectTreePrinter`** — indented plain text (shown above).
- **`ProjectTreeMarkdownSerializer`** — a nested Markdown bullet list, with each
  file's dependencies as indented child bullets. Well suited to documents and
  chat-based agents.
- **`ProjectTreeJsonSerializer`** — structured JSON (`name`, `type`,
  `dependencies`, `children`) for programmatic consumers; `serializePretty`
  produces indented JSON.

### Token-budget-aware context

A model's context window is finite, so `BudgetedContext` wraps any `Context` and
trims its result to fit a token budget. Because `Finder` returns dependencies in
breadth-first order (closest first), the decorator keeps that priority order and
accepts containers until the next one would exceed the budget:

```java
TokenEstimator estimator = new HeuristicTokenEstimator(); // ~chars/4, no tokenizer dependency
Context budgeted = new BudgetedContext(new Finder(allContainers), estimator, /* token budget */ 8000);
Set<ClassContainer> used = budgeted.find(root, depth);
```

- **`TokenEstimator`** — abstracts how a piece of text is costed in tokens.
- **`HeuristicTokenEstimator`** — a fast, dependency-free estimate from character
  count (configurable characters-per-token, default `4`), rounded up so any
  non-empty text costs at least one token.
- **`BudgetedContext`** — a `Context` decorator that returns the highest-priority
  prefix of the dependency graph that fits the budget.

## Data
It contains:
- data sources
  - support relational data loading
  - in memory and iterative loading
  - CSV, JDBC support
  - JSON (`InMemoryJSONDataSource`, `IterableJSONDataSource`) — nested objects are flattened with dotted-path keys (e.g. `people[0].address.city`)
  - YAML (`InMemoryYAMLDataSource`, `IterableYAMLDataSource`) — same flattening convention; no document-size limit
  - TOON (`InMemoryTOONDataSource`, `IterableTOONDataSource`) — a compact, LLM-friendly format that minimises tokens; supports key-value pairs, primitive arrays, tabular arrays, and nested objects
  - All file-based sources accept either a file path or an `InputStream`
  - GZIP decompression — any file-based source transparently decompresses `.gz` files; no extra configuration needed
- uniqueness checks tool
  - for a given set of data and subset of columns you can ask if these columns are unique (can be used as a key)
  - the tool also tries to find a better (smaller) answer
  - supports in memory and iterative processing
- data structures
  - open addressing hashmap: a simpler alternative to HashMap based only on one array and double hashing, it implements java.util.Map<K, V>
- MCP server
  - Model Context Protocol server exposing uniqueness checking as a tool for AI assistants
  - Compatible with Claude Desktop, Cline, and other MCP clients
  - Transports (select with `--transport.mode`):
    - `stdio` (default) — JSON-RPC over stdin/stdout (Spring Boot, no HTTP server started)
    - `streamable-http` — the modern HTTP transport served at `/mcp`
    - `sse` — the legacy HTTP+SSE transport for older clients: the event stream is served at `/sse` and JSON-RPC messages are POSTed to `/mcp/message`
  - Build: `mvn clean install` produces `data/target/tools.data-<version>.jar`
  - Run: `java -jar data/target/tools.data-<version>.jar --transport.mode=stdio`
  - See [MCP Usage Documentation](data/src/main/java/io/github/adamw7/tools/data/uniqueness/mcp/MCP_USAGE.md) for client configuration (Claude Desktop, Cline) and usage examples
  
Examples:

in memory check:
```java
		AbstractUniqueness check = new InMemoryUniquenessCheck();
		check.setDataSource(new InMemorySQLDataSource(connection, query));
		Result result = check.exec("COLUMN1", "COLUMN2", "COLUMN3");
		log.info(result.isUnique());
		Set<Result> betterOptions = result.getBetterOptions();
		for (Result betterOption : betterOptions) {
			log.info(betterOption);	
		}
```
In order to add a new data source for example for XML, JSON, etc you just need to implement this interface:
```java
public interface IterableDataSource extends AutoCloseable, Closeable {
	public String[] getColumnNames();
	
	public void open();
	
	public String[] nextRow();

	public boolean hasMoreData();
	
	public void reset();

	// default method, loads up to batchSize rows in one operation
	public List<String[]> nextRows(int batchSize);
}
```
`nextRows(int batchSize)` lets callers decide how much data is pulled from the source at once instead of reading row by row. It is a default method built on `hasMoreData()`/`nextRow()`, so every source gets it for free; an empty list signals the source is exhausted. The SQL source additionally applies `batchSize` as the JDBC fetch size so the rows are fetched in a single round-trip.
If you need an in memory source you need to implement one more method:
```java
public interface InMemoryDataSource extends IterableDataSource {
	public List<String[]> readAll();
}
```

Notes:

in memory checks are using in memory sources that load all the data once and run multiple recursive checks to find better options.
Iterative (no memory) checks are keeping only one row at the time so they require very tiny heap size but for the recursive checks need to read the source many times. 

### Open-addressing map

`OpenAddressingMap<K, V>` is a `java.util.Map` implementation that is **simpler
than `java.util.HashMap` because it uses only one array**: entries are stored
directly in a single array via open addressing, instead of `HashMap`'s array of
buckets with linked (or tree-ified) nodes. That makes it an allocation-light
alternative when you want a plain map without the per-entry node objects of
separate chaining.

```java
Map<String, Integer> map = new OpenAddressingMap<>(); // default capacity 64
map.put("a", 1);
map.put("b", 2);
map.get("a");          // 1
map.remove("b");       // 2
map.containsKey("b");  // false
```

How it works:

- **Double hashing** resolves collisions: a key's probe sequence is
  `h1 + i * h2` (modulo the array length), which spreads probes better than
  linear probing and avoids primary clustering. `h1`/`h2` are derived from the
  key's `hashCode()` and a prime chosen as the largest prime smaller than the
  array length.
- **Tombstones for removal**: `remove` marks a slot as removed rather than
  clearing it, so probe sequences that ran *through* that slot still find the
  entries placed after it. `put` reuses the first free slot and a never-used
  (`null`) slot terminates a lookup.
- **Automatic resizing**: when the array is about to fill up, it grows by a
  `1.2` factor and all live entries are re-hashed into the new array (tombstones
  are dropped in the process). The initial capacity can be set via
  `new OpenAddressingMap<>(size)` (minimum effective size is 3); a
  non-positive size is rejected with `IllegalArgumentException`.

Caveats:

- **Null keys are not supported** — `put`/`get` with a `null` key throw
  `IllegalArgumentException`.
- **Null values are not distinguishable from absence**: `get` returns `null`
  for a missing key and `containsKey` is defined as `get(key) != null`, so a key
  mapped to a `null` value is reported as absent. Avoid storing `null` values.
- It is **not thread-safe**; guard external synchronization if shared across
  threads.

### Open-addressing set

`OpenAddressingSet<E>` is a `java.util.Set` backed by an `OpenAddressingMap`, in
the same way that `java.util.HashSet` is backed by a `java.util.HashMap`.
Elements are stored as keys of the underlying map against a shared sentinel
value, so all of the open-addressing behaviour (double hashing, tombstone
removal and automatic resizing) is **reused rather than re-implemented**.

```java
Set<String> set = new OpenAddressingSet<>(); // default capacity 64
set.add("a");          // true  (newly added)
set.add("a");          // false (already present)
set.contains("a");     // true
set.remove("a");       // true
```

It inherits the map's caveats: **null elements are not supported** (they are
rejected with `IllegalArgumentException`) and it is **not thread-safe**. The
initial capacity can be set via `new OpenAddressingSet<>(size)`.

### Primitive int-keyed map

`IntKeyOpenAddressingMap<V>` is a primitive `int`-keyed sibling of
`OpenAddressingMap`. It uses the same double-hashing open-addressing strategy,
but stores keys in an `int[]` so that lookups and inserts **never box the key**.
That makes it an allocation-light choice for large, integer-keyed maps where the
autoboxing of a `Map<Integer, V>` would otherwise dominate.

```java
IntKeyOpenAddressingMap<String> map = new IntKeyOpenAddressingMap<>();
map.put(1, "a");
map.get(1);              // "a"
map.getOrDefault(2, ""); // ""  (absent)
map.remove(1);           // "a"
int[] keys = map.keys(); // live keys, unboxed
```

It deliberately does **not** implement `java.util.Map`, because that interface is
defined in terms of `Object` keys and would reintroduce the very boxing this
class exists to avoid; instead it mirrors the relevant map operations with
primitive `int` keys. Unlike `OpenAddressingMap`, **`null` values are stored
faithfully** and reported by `containsKey(int)` — only `get(int)` cannot tell a
stored `null` from an absent key. It is **not thread-safe**.

### Network kill-switch

`Switch` turns **all JVM outbound network access off** for the lifetime of the
process. It is useful when you want to guarantee that a data-processing run stays
offline — e.g. no accidental calls out while loading and checking local data.

```java
boolean changed = Switch.off(); // true the first time, false if already off
```

`Switch.off()` installs a default `ProxySelector` that refuses every proxy
selection by throwing `UnsupportedOperationException("The network is off")`, so
any subsequent attempt to open an outbound connection fails fast. The method is:

- **One-way** — there is no `on()`; once off, the JVM stays offline. Apply it
  early, only when you really mean to seal the process.
- **Idempotent and thread-safe** — it is `synchronized` and guarded by a
  `volatile` flag; calling it again is a no-op that logs a warning and returns
  `false`.
  
The switch can be used for example if there is a need to make sure no network connections
are open while running unit tests.

## Architecture tests (ArchUnit)

Every production module guards its own package structure with
[ArchUnit](https://www.archunit.org/) rules that run as ordinary JUnit 5 tests,
so an accidental dependency or a broken naming convention **fails the build**
instead of quietly eroding the design. Each module keeps its rules in a single
`*ArchitectureTest` under an `architecture` package, annotated with
`@AnalyzeClasses(..., importOptions = ImportOption.DoNotIncludeTests.class)` so
that only production classes are analysed. The ArchUnit dependency
(`com.tngtech.archunit:archunit-junit5`) is managed centrally in the root
`pom.xml`, and the tests run as part of the normal `mvn install`.

A set of conventions is shared across modules:

- **No cycles between packages** — `slices().matching(...).should().beFreeOfCycles()`
  keeps the package graph acyclic in each module.
- **Loggers are constants** — every `org.apache.logging.log4j.Logger` field must
  be `private static final` (the context module relaxes this to `private final`),
  because a logger is a shared, immutable, class-scoped collaborator.
- **`*Exception` types really are exceptions** — any class whose simple name ends
  with `Exception` must be assignable to `java.lang.Exception`.
- **`Abstract`-prefixed names** — a top-level abstract class must have a simple
  name starting with `Abstract`, so that a type meant to be extended is obvious
  at a glance.
- **Logging goes through log4j2, not the console or the JDK** — ArchUnit's
  `GeneralCodingRules` forbid access to `System.out`/`System.err`, throwing
  generic exceptions, and using `java.util.logging`; libraries additionally must
  never call `System.exit`.

On top of that baseline, each module pins the boundaries specific to its own
design:

- **`data`** — data-source contracts (`source.interfaces`) must stay interfaces
  and must not know their concrete `source.db`/`source.file` implementations;
  `structure.internal` is accessible only from `structure`; the reusable
  `structure` collections must not couple to data sources; every concrete
  `*DataSource` must implement `IterableDataSource`; the uniqueness core must not
  depend on its `mcp` adapter; and a `layeredArchitecture` pins the source layers
  so file and DB sources depend only downwards on their contracts (files may also
  use compression), never on each other.
- **`code/context`** — the finder/tree core must not depend on the `mcp`
  delivery package, and only that `mcp` package may build on the shared MCP
  scaffolding; every concrete `*Serializer` must honour the
  `ProjectTreeSerializer` contract.
- **`code/protogen-maven-plugin`** — the reusable `format` package must not
  depend on the `gen` code generator that builds on it, and every concrete
  `*Mojo` must implement the Maven `Mojo` contract.
- **`mcp-common`** — the `McpTool` SPI must stay an interface, every concrete
  `*Tool` must implement it, and the shared scaffolding must never call
  `System.exit`.
- **`claude-code-enforcer`** — a `layeredArchitecture` pins the module's layers
  (`text` is the foundation, `rule` builds on it, and the feature packages
  `definition`/`doc`/`mcp`/`settings` build on `rule` without reaching sideways
  into one another), and every concrete `*Rule` must extend the shared
  `ClaudeCodeEnforcerRule` base.

Run them for a single module with, for example:
```
mvn -pl data test
```
or across the whole repository as part of `mvn install`.

# Building
```
mvn clean install
```
The clean part is needed since the build contain code generation so if you remove a source of generation and do not use clean then the result of previous build may remain in target. If you do not remove anything you could benefit from faster:
```
mvn install
```
# Releasing
In order to release a new version - X you need to:
1. Change the revision property to X in root pom.xml
2. Commit and push
3. Check if all builds pass
4. Release and mark as latest in GitHub

# License

This project is licensed under the [MIT License](LICENSE).
