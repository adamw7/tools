package io.github.adamw7.tools.mcp;

/**
 * Selects the MCP transport from the command line and primes the Spring runtime
 * before the application context starts. The transport is read from
 * {@code --transport.mode} (defaulting to stdio). In stdio mode the web server is
 * disabled so nothing but JSON-RPC reaches the channel; every HTTP transport
 * ({@code streamable-http}, {@code stateless-http} and {@code sse}) keeps the web
 * server so its servlet can be exposed. The banner is always suppressed because it
 * would otherwise corrupt the stdio stream. Both MCP servers share this so their
 * {@code Main} entry points stay trivial.
 */
public final class TransportConfigurer {

	private static final String TRANSPORT_MODE_PREFIX = "--transport.mode=";

	private static final String STDIO = "stdio";

	private TransportConfigurer() {
	}

	public static void configure(String[] args) {
		String transportMode = resolveTransportMode(args);
		System.setProperty("transport.mode", transportMode);
		if (STDIO.equals(transportMode)) {
			System.setProperty("spring.main.web-application-type", "none");
		}
		System.setProperty("spring.main.banner-mode", "off");
	}

	public static String resolveTransportMode(String[] args) {
		for (String arg : args) {
			if (arg.startsWith(TRANSPORT_MODE_PREFIX)) {
				return arg.substring(TRANSPORT_MODE_PREFIX.length());
			}
		}
		return STDIO;
	}
}
