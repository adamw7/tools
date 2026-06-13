# grpc-example

An end-to-end gRPC example that reuses this project's proto-to-Java code
generation. Two generators run from [`greeter.proto`](src/main/proto/greeter.proto):

1. **`protobuf-maven-plugin`** (ascopes) compiles the proto2 definitions into
   the protobuf message classes (`Address`, `Person`, `HelloRequest`,
   `HelloReply`) and, via the `protoc-gen-grpc-java` plugin, the gRPC service
   stub (`GreeterGrpc`).
2. **`protogen-maven-plugin`** (this repo) scans the generated message package
   and emits compile-time-safe builders (`AddressBuilder`, `PersonBuilder`,
   `HelloRequestBuilder`, `HelloReplyBuilder`) that refuse to `build()` until
   every `required` field is set — shifting the missing-field check from runtime
   to compile time.

The messages are composed: a `HelloRequest` points to a `Person`, which in turn
points to an `Address`. The generated builders compose the same way, so a
request is assembled bottom-up through a three-level chain:

```java
Address address = new AddressBuilder().setCity("London").setCountry("UK").build();
Person person = new PersonBuilder().setName("Smith").setTitle("Dr.").setAddress(address).build();
HelloRequest request = new HelloRequestBuilder().setPerson(person).build();
```

The builders are generated into `target/generated-test-sources` (the protogen
plugin reads the already-compiled message classes off the classpath), so the
example server, client, and test live under `src/test/java`.

## What's here

- `GreeterServiceImpl` — the service; builds the response with `HelloReplyBuilder`.
- `GreeterServer` / `GreeterClient` — runnable Netty-backed main classes.
- `GreeterExampleTest` — an in-process server/client round trip that constructs
  the request and response through the generated builders.

## Build and test

```bash
mvn -pl grpc-example -am test
```

## Run

The server and client live under `src/test/java`, so `exec:java` is configured
with `classpathScope=test`. Build the module first so the generated builders are
on the classpath, then start the server (the default `mainClass`):

```bash
mvn -pl grpc-example -am test-compile
mvn -pl grpc-example exec:java
```

In another terminal, drive it with the client, overriding the main class:

```bash
mvn -pl grpc-example exec:java -Dexec.mainClass=io.github.adamw7.tools.grpc.GreeterClient -Dexec.args=Smith
```
