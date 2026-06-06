# grpc-example

An end-to-end gRPC example that reuses this project's proto-to-Java code
generation. Two generators run from [`greeter.proto`](src/main/proto/greeter.proto):

1. **`protobuf-maven-plugin`** (ascopes) compiles the proto2 definitions into
   the protobuf message classes (`HelloRequest`, `HelloReply`) and, via the
   `protoc-gen-grpc-java` plugin, the gRPC service stub (`GreeterGrpc`).
2. **`protogen-maven-plugin`** (this repo) scans the generated message package
   and emits compile-time-safe builders (`HelloRequestBuilder`,
   `HelloReplyBuilder`) that refuse to `build()` until every `required` field is
   set — shifting the missing-field check from runtime to compile time.

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
