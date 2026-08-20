package io.github.adamw7.tools.mcp;

/**
 * Selects the MCP transport from the command line and primes the Spring runtime
 * before the application context starts. The transport is read from
 * {@code --transport.mode} (defaulting to stdio) and must name one of the
 * {@link TransportMode known modes}; anything else is refused here, at the argument
 * that carries the typo, rather than starting a server no request can reach. In stdio
 * mode the web server is disabled so nothing but JSON-RPC reaches the channel; every
 * HTTP transport ({@code streamable-http} and {@code stateless-http}) keeps the web
 * server so its servlet can be exposed. The banner is always suppressed because it
 * would otherwise corrupt the stdio stream. The MCP servers share this so their
 * {@code Main} entry points stay trivial.
 */
public final class TransportConfigurer {

	private static final String TRANSPORT_MODE_PREFIX = "--transport.mode=";

	private TransportConfigurer() {
	}

	/**
	 * @throws IllegalArgumentException when {@code --transport.mode} names no known
	 *         transport, before any Spring runtime property is set
	 */
	public static void configure(String[] args) {
		TransportMode transportMode = resolveTransportMode(args);
		System.setProperty("transport.mode", transportMode.value());
		if (transportMode == TransportMode.STDIO) {
			System.setProperty("spring.main.web-application-type", "none");
		}
		System.setProperty("spring.main.banner-mode", "off");
	}

	/**
	 * @return the transport the arguments name, or {@link TransportMode#STDIO} when
	 *         they name none
	 * @throws IllegalArgumentException when they name one that does not exist
	 */
	public static TransportMode resolveTransportMode(String[] args) {
		for (String arg : args) {
			if (arg.startsWith(TRANSPORT_MODE_PREFIX)) {
				return TransportMode.of(arg.substring(TRANSPORT_MODE_PREFIX.length()));
			}
		}
		return TransportMode.STDIO;
	}
}
