package io.github.adamw7.context.mcp;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.adamw7.context.Language;
import io.github.adamw7.context.okf.OkfBundle;
import io.github.adamw7.context.okf.OkfBundler;
import io.github.adamw7.context.tree.ProjectTreeBuilder;
import io.github.adamw7.context.tree.ProjectTreeNode;
import io.github.adamw7.tools.mcp.ToolArguments;
import io.github.adamw7.tools.mcp.ToolDefinition;
import io.github.adamw7.tools.mcp.ToolResult;

/**
 * MCP tool that scans a Java, Kotlin or Scala project and returns it as a bundle
 * in Google's Open Knowledge Format — a directory of markdown concept documents
 * with YAML frontmatter — so an agent that already consumes OKF can take this
 * project's structure and class dependencies without knowing anything about the
 * tree behind it.
 * <p>
 * The bundle is returned as JSON mapping each bundle-relative path to its
 * document, rather than written to disk: the server stays read-only, so a client
 * cannot use it to create files anywhere on the host.
 */
public class OkfBundleTool implements ContextTool {

	private static final Logger log = LogManager.getLogger(OkfBundleTool.class.getName());

	private static final int DEFAULT_DEPTH = 1;
	private static final int MAX_DEPTH = 10;
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final PathPolicy pathPolicy;

	public OkfBundleTool(PathPolicy pathPolicy) {
		this.pathPolicy = pathPolicy;
	}

	private final ToolDefinition toolDefinition = new ToolDefinition("okf_bundle",
			"Scan a Java, Kotlin or Scala project into an Open Knowledge Format (OKF) bundle of markdown documents",
			Map.of(
					"type", "object",
					"properties", Map.of(
							"path", Map.of("type", "string",
									"description", "absolute path to the project root directory"),
							"language", Map.of("type", "string",
									"description", "source language: java (default), kotlin or scala"),
							"depth", Map.of("type", "integer",
									"description", "how many levels of transitive dependencies to resolve (default 1)")),
					"required", List.of("path")));

	@Override
	public ToolDefinition getToolDefinition() {
		return toolDefinition;
	}

	@Override
	public ToolResult apply(Map<String, Object> arguments) {
		log.info("Calling MCP okf_bundle tool for {}", arguments);
		return ToolResult.success(buildBundle(arguments));
	}

	private String buildBundle(Map<String, Object> arguments) {
		Path root = pathPolicy.resolve(ToolArguments.requiredString(arguments, "path"));
		Language language = LanguageArguments.optionalLanguage(arguments, "language", Language.JAVA);
		int depth = ToolArguments.optionalBoundedInt(arguments, "depth", DEFAULT_DEPTH, 0, MAX_DEPTH);
		ProjectTreeNode tree = new ProjectTreeBuilder(language, depth).build(root);
		return toJson(new OkfBundler(language).bundle(tree));
	}

	private String toJson(OkfBundle bundle) {
		ObjectNode result = MAPPER.createObjectNode();
		result.put("okf_version", OkfBundle.VERSION);
		ObjectNode documents = result.putObject("documents");
		bundle.documents().forEach(document -> documents.put(document.path(), document.content()));
		return result.toString();
	}
}
