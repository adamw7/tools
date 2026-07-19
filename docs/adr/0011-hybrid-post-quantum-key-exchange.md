# 11. Prefer the X25519MLKEM768 hybrid key exchange for TLS 1.3

- **Status:** Accepted
- **Date:** 2026-07-19
- **Deciders:** Project maintainers
- **Tags:** security, transport, tls, post-quantum
- **Supersedes:** —
- **Superseded by:** —

Refines the TLS 1.3 transport decision in
[ADR 0003](0003-require-tls-1.3.md).

## Context

[ADR 0003](0003-require-tls-1.3.md) pins the HTTPS transport to TLS 1.3 but says
nothing about the *key exchange*: left to defaults, the SunJSSE provider
negotiates a classical elliptic-curve group (X25519, secp256r1, …). Classical
Diffie-Hellman is vulnerable to a "harvest now, decrypt later" attack — an
adversary records today's handshake and derives the session key once a
cryptographically relevant quantum computer exists. TLS 1.3 already supports
*hybrid* key-exchange groups that pair a classical exchange with a NIST
post-quantum KEM; `X25519MLKEM768` combines X25519 with ML-KEM-768 (FIPS 203) so
the derived secret stays secure as long as **either** primitive holds.

## Decision

**When a transport is served over HTTPS, the server prefers the post-quantum
`X25519MLKEM768` hybrid group for the TLS 1.3 key exchange, keeping classical
groups as a graceful fallback.** As with the TLS 1.3 pin, this is enforced in
code, not left to deployment configuration: the same
`WebServerFactoryCustomizer` (`enforceTls13()`) that pins the protocol also sets
the JVM-wide `jdk.tls.namedGroups` property to
`X25519MLKEM768,x25519,secp256r1,secp384r1` whenever SSL is enabled.

- The hybrid group is listed **first**, so it is preferred when both peers
  support it; the classical groups follow, so a peer that cannot do the hybrid
  exchange still completes the handshake.
- Production implementation:
  [`code/context/src/main/java/io/github/adamw7/context/mcp/TlsConfiguration.java`](../../code/context/src/main/java/io/github/adamw7/context/mcp/TlsConfiguration.java).
- **Plain HTTP and disabled SSL are left untouched** — the property is set only
  when SSL is enabled, before the connector starts its TLS stack, so SunJSSE
  reads it when initialising its supported groups.
- `TlsConfigurationTest` asserts that an enabled HTTPS factory sets
  `jdk.tls.namedGroups` to the hybrid-first list and that plain-HTTP /
  disabled-SSL factories leave it unset; `McpStreamableHttpsIT` asserts the
  preference is in force against a live HTTPS server that still completes a real
  TLS 1.3 MCP call.

### JDK support and forward compatibility

`X25519MLKEM768` is not yet shipped by the SunJSSE provider in **JDK 25** (the
ML-KEM primitives from JEP 496 are present, but the TLS 1.3 named group is not).
Setting `jdk.tls.namedGroups` to the hybrid group **alone** would throw at
provider initialisation ("contains no supported named groups") and break all
HTTPS. Listing supported classical groups alongside it avoids that: SunJSSE
silently drops the unknown group and negotiates a classical one, so **today the
handshake falls back to X25519**. The moment the JVM's TLS provider gains
`X25519MLKEM768` — a newer JDK, or a provider such as BouncyCastle — the group is
already first in the preference list and the hybrid exchange activates
automatically, with no code change. This makes the configuration a safe,
forward-looking default rather than a hard dependency on a specific JDK.

## Consequences

**Positive**

- HTTPS handshakes prefer a post-quantum-secure key exchange as soon as the
  runtime can provide it, mitigating "harvest now, decrypt later" without
  waiting for a code change or a new dependency.
- The preference is executable and regression-tested, not just documented.
- No new dependency: the change rides on the JDK's own TLS provider and property.

**Negative / trade-offs**

- On JDK 25 the exchange is still classical; the post-quantum benefit is latent
  until the provider supports the group. The configuration documents intent and
  removes the future code change, but does not by itself make today's traffic
  quantum-resistant.
- `jdk.tls.namedGroups` is JVM-wide, so it also influences any other TLS client
  or server in the same process. This is acceptable for the single-purpose MCP
  server and mirrors the process-wide nature of the TLS 1.3 pin's intent.
- As with ADR 0003, the enforcement currently lives with the context MCP server;
  a second HTTPS-serving surface must apply the same customiser to inherit the
  preference (see the follow-up in [ADR 0003](0003-require-tls-1.3.md)).
