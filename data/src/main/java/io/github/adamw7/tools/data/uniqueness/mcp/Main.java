package io.github.adamw7.tools.data.uniqueness.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.github.adamw7.tools.mcp.TransportConfigurer;

/**
 * Entry point of the uniqueness MCP server. It selects the transport from
 * {@code --transport.mode} (defaulting to stdio) before starting Spring so that
 * stdio mode runs without a web server and never prints a banner that would
 * corrupt the stdio JSON-RPC channel.
 */
@SpringBootApplication
public class Main {

	public static void main(String[] args) {
		TransportConfigurer.configure(args);
		SpringApplication.run(Main.class, args);
	}
}
