# tools

Library of tooling for various purposes.

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
	<version>1.5.0</version>
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
where ClassContiner contains the path of the class and its sources.
The depth param tells the finder how deep we want to go in the tree of usages of the class.
Of course when the depth is growing the tree grows very fast.

### Project tree

A single class is rarely enough context — an agent usually needs to see how a
whole project is laid out and how its files relate. `ProjectTreeBuilder` walks a
Java project directory and produces a tree of its **folders, files and
dependencies** in one structure:

```java
ProjectTreeNode root = new ProjectTreeBuilder(/* depth */ 1).build(Path.of("my-project"));
System.out.println(new ProjectTreePrinter().print(root));
```

The building blocks are:

- **`ProjectTreeNode`** — a node in the tree. Each node is either a *directory*
  (mirroring a project folder and holding child nodes) or a *file*. File nodes
  additionally carry the set of project classes they depend on, so folders,
  files and dependencies are all described by the same tree.
- **`ProjectTreeBuilder`** — scans the project directory recursively. Every
  folder becomes a directory node and every file a file node (directories are
  listed before files, then alphabetically). For each `.java` file it resolves
  the project classes it uses with the same regex-based `Context`, skipping the
  file's own class. `depth` controls how deep the usage search goes, exactly as
  in the `Context` interface above.
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
  - Transport: stdio over JSON-RPC (Spring Boot, no HTTP server started)
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

The rules are wired into the root `pom.xml` and run at the repository root only.
The check is **opt-in** via the `enforceClaudeMd` property, so ordinary builds
are unaffected:
```
mvn -pl claude-code-enforcer -am install   # install the rule jar once
mvn package -DenforceClaudeMd            # build with the checks enabled
```

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
