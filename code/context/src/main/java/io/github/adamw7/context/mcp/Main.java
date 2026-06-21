package io.github.adamw7.context.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the context-engineering MCP server. It selects the transport
 * from {@code --transport.mode} (defaulting to stdio) before starting Spring so
 * that stdio mode runs without a web server and never prints a banner that would
 * corrupt the stdio JSON-RPC channel.
 */
@SpringBootApplication
public class Main {

	public static void main(String[] args) {
		configureRuntime(args);
		SpringApplication.run(Main.class, args);
	}

	static void configureRuntime(String[] args) {
		String transportMode = resolveTransportMode(args);
		System.setProperty("transport.mode", transportMode);
		if ("stdio".equals(transportMode)) {
			System.setProperty("spring.main.web-application-type", "none");
		}
		System.setProperty("spring.main.banner-mode", "off");
	}

	static String resolveTransportMode(String[] args) {
		String prefix = "--transport.mode=";
		for (String arg : args) {
			if (arg.startsWith(prefix)) {
				return arg.substring(prefix.length());
			}
		}
		return "stdio";
	}
}
