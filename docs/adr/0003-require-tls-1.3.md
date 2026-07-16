# 3. Require TLS 1.3+ for HTTPS transports

- **Status:** Accepted
- **Date:** 2026-07-16
- **Deciders:** Project maintainers
- **Tags:** security, transport, tls
- **Supersedes:** —
- **Superseded by:** —

Refines the transport-security pillar of
[ADR 0002](0002-security-policy-and-supply-chain-posture.md).

## Context

The MCP servers can serve their streamable-HTTP transport over HTTPS (SSL enabled
through Spring Boot's `server.ssl.*` properties on the embedded Tomcat). Left to
defaults, the server negotiates whatever protocols the JDK and configuration
permit, which can include older, weaker TLS versions (TLS 1.0/1.1/1.2). Older TLS
versions carry known weaknesses and are being deprecated across the ecosystem;
allowing them to be negotiated undermines the point of enabling TLS at all.

## Decision

**When a transport is served over HTTPS, the only enabled TLS protocol is
`TLSv1.3`.** This is enforced in code rather than left to deployment
configuration: a Spring `WebServerFactoryCustomizer`
(`enforceTls13()`) pins the embedded Tomcat's enabled protocols to `TLSv1.3`
whenever SSL is enabled, regardless of what the configuration requested, so a
weaker protocol can never be negotiated.

- Production implementation:
  [`code/context/src/main/java/io/github/adamw7/context/mcp/TlsConfiguration.java`](../../code/context/src/main/java/io/github/adamw7/context/mcp/TlsConfiguration.java).
- **Plain HTTP and disabled SSL are left untouched** — there is no TLS to
  constrain in those cases; the customiser is a no-op.
- The behaviour is pinned by `TlsConfigurationTest`, which asserts that an enabled
  HTTPS factory ends up with exactly `{ "TLSv1.3" }` even when TLS 1.2 was
  requested, and that plain-HTTP / disabled-SSL factories are not modified.

"TLS 1.3+" is expressed today as "TLS 1.3 only" because 1.3 is the current
highest version; a future 1.4 would be added to the enabled set by updating the
customiser, not by re-allowing 1.2.

## Consequences

**Positive**

- HTTPS transports can never silently downgrade to a weak protocol, independent of
  how the deployment sets `server.ssl.*`.
- The requirement is executable and regression-tested, not just documented.

**Negative / trade-offs**

- Clients that cannot speak TLS 1.3 cannot connect over HTTPS. This is intentional
  — such clients should use a modern TLS stack — but it is a hard cutoff.
- The enforcement currently lives with the context MCP server. If another
  HTTPS-serving surface is added, it must apply the same customiser (or a shared
  one) to inherit the guarantee.

## Follow-up

**Extract the customiser to `mcp-common` before a second HTTPS surface ships.**
`enforceTls13()` lives in the context module today, so the TLS 1.3 guarantee is
not automatically inherited by any future HTTPS-serving MCP server (the data
server, for example). The tracked follow-up is to move the
`WebServerFactoryCustomizer` into `mcp-common`'s shared server scaffolding (see
[ADR 0009](0009-mcp-servers-on-spring-boot.md)) so every server that enables SSL
gets the pinned protocol set for free, and to add an ArchUnit/enforcer check that
no MCP server enables SSL without the shared customiser on the classpath. Until
then, any new HTTPS endpoint must apply the customiser explicitly and this ADR is
the record of that obligation.
