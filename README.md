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

# Quality

The project maintains a high standard of software quality through multiple complementary testing strategies:

- **Unit tests** — comprehensive coverage of individual components, focusing on behaviour, edge cases, and error paths.
- **Integration tests** — end-to-end scenarios verify that modules work correctly together (e.g. data sources with uniqueness checks, gRPC client/server, MCP server).
- **Mutation testing** — [PIT](https://pitest.org/) is used to validate the effectiveness of the test suite. Mutations are introduced into the bytecode and the tests must kill them, ensuring the tests actually catch defects rather than merely executing code.

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
