package io.github.adamw7.tools.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The version every MCP server here reports in its handshake: the version of the
 * {@code tools} build the server was packaged from, read from metadata Maven
 * filters at build time rather than from a literal in the source that has to be
 * remembered at release time.
 *
 * <p>A version that cannot be read is answered with {@value #UNKNOWN} and a
 * warning, because the version is descriptive: a client that cannot be told which
 * release it is talking to is better served by a server that still starts than by
 * one that refuses to.
 */
final class ServerVersion {

	static final String BUILD_PROPERTIES = "/mcp-common-build.properties";
	static final String VERSION_KEY = "mcp.server.version";
	static final String UNKNOWN = "unknown";

	private static final Logger log = LogManager.getLogger(ServerVersion.class);
	private static final String CURRENT = fromBuildMetadata();

	private ServerVersion() {
	}

	/**
	 * The version to advertise. Resolved once at class initialization, since the
	 * metadata is fixed for the life of the process and every transport asks for the
	 * same answer.
	 */
	static String current() {
		return CURRENT;
	}

	/**
	 * Reads the version from the metadata Maven filters into
	 * {@value #BUILD_PROPERTIES} at build time, so it tracks the project's
	 * {@code revision} property.
	 */
	static String fromBuildMetadata() {
		try (InputStream stream = ServerVersion.class.getResourceAsStream(BUILD_PROPERTIES)) {
			return read(stream);
		} catch (IOException e) {
			log.warn("Could not read build metadata {}; advertising {} to MCP clients", BUILD_PROPERTIES, UNKNOWN, e);
			return UNKNOWN;
		}
	}

	/**
	 * Falls back to {@value #UNKNOWN} when the metadata is missing from the classpath
	 * or reached the classpath unfiltered — an unsubstituted token would otherwise be
	 * advertised verbatim, which is a worse answer than admitting the version is not
	 * known.
	 */
	static String read(InputStream stream) throws IOException {
		if (stream == null) {
			log.warn("Build metadata {} is not on the classpath; advertising {} to MCP clients", BUILD_PROPERTIES,
					UNKNOWN);
			return UNKNOWN;
		}
		Properties properties = new Properties();
		properties.load(stream);
		String version = properties.getProperty(VERSION_KEY, "").strip();
		if (version.isEmpty() || version.startsWith("@") || version.startsWith("${")) {
			log.warn("{} was not filtered into {} (found: '{}'); advertising {} to MCP clients", VERSION_KEY,
					BUILD_PROPERTIES, version, UNKNOWN);
			return UNKNOWN;
		}
		return version;
	}
}
