package io.github.adamw7.context.mcp;

import java.nio.file.Path;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.context.Language;
import io.github.adamw7.tools.mcp.ToolArguments;
import io.github.adamw7.tools.mcp.ToolResult;

/**
 * Shared skeleton for every MCP tool this server exposes. They all take the same
 * three arguments — the project to look at, the language its sources are written in,
 * and how far to follow dependencies — so confining the path, defaulting the language
 * and bounding the depth happens once here, next to the {@link ContextToolSchema}
 * that advertises them. What a tool does with the {@link Scan} is its own, supplied
 * through {@link #answer}.
 *
 * <p>The depth is bounded below at {@value #MIN_DEPTH} as well as above. A depth of
 * zero resolves nothing and every finder rejects it, so accepting it here only moved
 * the refusal to a message that named no bound; refusing it while the arguments are
 * read tells the caller the range the tools actually take.
 */
abstract class AbstractContextTool implements ContextTool {

	private static final Logger log = LogManager.getLogger(AbstractContextTool.class);

	private static final int DEFAULT_DEPTH = 1;
	private static final int MIN_DEPTH = 1;
	private static final int MAX_DEPTH = 10;

	protected final PathPolicy pathPolicy;

	protected AbstractContextTool(PathPolicy pathPolicy) {
		this.pathPolicy = pathPolicy;
	}

	/** What every context tool is asked to look at: a confined project root, a language and a depth. */
	protected record Scan(Path root, Language language, int depth) {
	}

	@Override
	public final ToolResult apply(Map<String, Object> arguments) {
		log.info("Calling MCP {} tool for {}", getToolDefinition().name(), arguments);
		Scan scan = new Scan(pathPolicy.resolve(ToolArguments.requiredString(arguments, "path")),
				LanguageArguments.optionalLanguage(arguments, "language", Language.JAVA),
				ToolArguments.optionalBoundedInt(arguments, "depth", DEFAULT_DEPTH, MIN_DEPTH, MAX_DEPTH));
		return answer(scan, arguments);
	}

	/**
	 * Answers the call with the arguments every tool takes already resolved.
	 * {@code arguments} is passed on for a tool that reads one of its own beyond them.
	 */
	protected abstract ToolResult answer(Scan scan, Map<String, Object> arguments);
}
