# tools

Library of tooling for various purposes.

## Code generation

Poblem:

Generated builder java code for protobuffers detects missing required fields in runtime.

Solution:

Move detection to compile time.

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
	<configuration>
		<generatedSourcesDir>${project.basedir}/target/generated-sources/</generatedSourcesDir>
	</configuration>
		<executions>
			<execution>
				<phase>generate-sources</phase>
				<goals>
					<goal>code-generator</goal>
				<goals>
			</execution>
		</executions>
</plugin>
```
that generetes builders detecting missing required fields compile time (some methods are excluded for simplicity of the example):
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

## Data
It contains:
- data sources
  - support relational data loading
  - in memory and iterative loading
  - CSV, GZip, JDBC support
- uniqueness checks tool 
  - for a given set of data and subset of columns you can ask if these columns are unique
  - the tool also tries to find a better (smaller) answer
  - supports in memory and iterative processing
- data structures
  - open addressing hashmap: a simplier alternative to HashMap based only on one array and double hashing, it implements java.util.Map<K, V>
  
Examples:

in memory check:
```java
		AbstractUniqueness check = new InMemoryUniquenessCheck();
		check.setDataSource(new InMemorySQLDataSource(connection, query));
		Result result = check.exec("COLUM1", "COLUMN2", "COLUMN3");
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
}
```
If you would need a in memory source you need to implement one more method:
```java
public interface InMemoryDataSource extends IterableDataSource {
	public List<String[]> readAll();
}
```

Notes:

in memory checks are using in memory sources that load all the data once and run multiple recursive checks to find better options.
Iterative (no memory) checks are keeping only one row at the time so they require very tiny heapsize but for the recursive checks need to read the source many times. 

# Building
```
mvn clean install
```
