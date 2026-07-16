# 9. MCP servers built on Spring Boot

- **Status:** Accepted
- **Date:** 2026-07-16
- **Deciders:** Project maintainers
- **Tags:** mcp, integration, runtime
- **Supersedes:** —
- **Superseded by:** —

Justifies the server runtime chosen for the MCP integration surface described in
[ADR 0001](0001-foundational-architecture.md).

## Context

Two capabilities are exposed to AI assistants as MCP servers: the uniqueness
checker in `data`, and `project_tree` / `find_context` / `estimate_tokens` in
`code/context`. Both need the same plumbing — process/lifecycle management,
dependency injection to assemble tool handlers, and multiple MCP transports
(stdio, streamable HTTP, stateless HTTP, HTTP+SSE), including HTTPS with a pinned
TLS policy ([ADR 0003](0003-require-tls-1.3.md)). Writing that plumbing by hand,
twice, would be duplicative and error-prone; the servers need a common runtime.

## Decision

Build the MCP servers as **Spring Boot applications**, with the shared server
scaffolding — transport wiring, the tool SPI, and the HTTPS/TLS customiser —
factored into **`mcp-common`** so each server module supplies only its tool logic.

Spring Boot is chosen because:

- The MCP Java SDK integrates cleanly with Spring's server model, and Spring Boot
  supplies the embedded HTTP server (Tomcat) the HTTP transports need out of the
  box, including the `WebServerFactoryCustomizer` hook ADR 0003 relies on to pin
  TLS 1.3.
- Its dependency injection is a natural fit for assembling tool handlers and
  transport beans, keeping tool logic decoupled from transport concerns.
- Standardising both servers on one runtime means shared scaffolding in
  `mcp-common` rather than two bespoke setups.

## Consequences

**Positive**

- Both MCP servers share one runtime and one set of transport/TLS scaffolding in
  `mcp-common`; a fix or a new transport is written once.
- The embedded-server model gives the TLS 1.3 enforcement of ADR 0003 a single,
  well-defined place to live (and a clear home to migrate it to — see that ADR's
  follow-up).
- New MCP servers start from `mcp-common` and inherit transports, DI wiring, and
  the security posture for free.

**Negative / trade-offs**

- Spring Boot is a heavyweight runtime for what can be a small stdio tool server;
  the startup cost and dependency footprint are larger than a minimal handwritten
  server would carry.
- The servers inherit Spring Boot's release cadence and its share of the dependency
  tree that the security posture ([ADR 0002](0002-security-policy-and-supply-chain-posture.md))
  must keep current.
