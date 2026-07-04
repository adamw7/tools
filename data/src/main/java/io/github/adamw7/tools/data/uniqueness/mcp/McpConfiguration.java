package io.github.adamw7.tools.data.uniqueness.mcp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import io.github.adamw7.tools.data.source.file.PathValidator;
import io.github.adamw7.tools.mcp.AbstractMcpConfiguration;
import io.github.adamw7.tools.mcp.McpTool;

/**
 * Wires the uniqueness MCP server. It exposes a single uniqueness-check tool,
 * confined to the configured {@code data.allowed-base-dir} (defaulting to the
 * server's working directory) so a client cannot steer the tool at arbitrary
 * files such as {@code /etc/passwd}; all transport wiring is inherited from
 * {@link AbstractMcpConfiguration}.
 */
@Configuration
public class McpConfiguration extends AbstractMcpConfiguration {

	private static final Logger log = LogManager.getLogger(McpConfiguration.class);

	@Value("${data.allowed-base-dir:}")
	String allowedBaseDir;

	@Override
	protected String serverName() {
		return "custom-server";
	}

	@Override
	protected List<McpTool> tools() {
		confineFileAccess();
		return List.of(new UniquenessTool());
	}

	private void confineFileAccess() {
		Path baseDir = resolveBaseDir();
		try {
			PathValidator.setAllowedBaseDir(baseDir);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not confine MCP file access to " + baseDir, e);
		}
		log.info("Confining MCP file access to base directory: {}", baseDir);
	}

	private Path resolveBaseDir() {
		if (allowedBaseDir == null || allowedBaseDir.isBlank()) {
			return Path.of(System.getProperty("user.dir"));
		}
		return Path.of(allowedBaseDir.trim());
	}
}
