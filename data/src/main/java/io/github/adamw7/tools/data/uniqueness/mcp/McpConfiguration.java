package io.github.adamw7.tools.data.uniqueness.mcp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import io.github.adamw7.tools.data.source.file.AllowedPaths;
import io.github.adamw7.tools.mcp.AbstractMcpConfiguration;
import io.github.adamw7.tools.mcp.McpTool;

/**
 * Wires the uniqueness MCP server. It exposes a single uniqueness-check tool,
 * confined to the configured {@code data.allowed-base-dir} (defaulting to the
 * server's working directory) so a client cannot steer the tool at arbitrary
 * files such as {@code /etc/passwd}. The boundary is handed to the tool as an
 * {@link AllowedPaths} rather than set process-wide, so it confines this server's
 * tools and nothing else; all transport wiring is inherited from
 * {@link AbstractMcpConfiguration}.
 */
@Configuration
public class McpConfiguration extends AbstractMcpConfiguration {

	@Value("${data.allowed-base-dir:}")
	String allowedBaseDir;

	@Override
	protected String serverName() {
		return "uniqueness-server";
	}

	@Override
	protected List<McpTool> tools() {
		return List.of(new UniquenessTool(confinement()));
	}

	/**
	 * The boundary this server's tools read within. It belongs to the tools built here
	 * and to nothing else in the JVM, so a host running a second server &mdash; or a test
	 * running beside this one &mdash; keeps its own.
	 */
	AllowedPaths confinement() {
		Path baseDir = resolveBaseDir();
		try {
			return AllowedPaths.under(baseDir);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not confine MCP file access to " + baseDir, e);
		}
	}

	private Path resolveBaseDir() {
		if (allowedBaseDir == null || allowedBaseDir.isBlank()) {
			return Path.of(System.getProperty("user.dir"));
		}
		return Path.of(allowedBaseDir.trim());
	}
}
