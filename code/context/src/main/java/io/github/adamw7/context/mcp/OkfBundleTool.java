package io.github.adamw7.context.mcp;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.adamw7.context.Language;
import io.github.adamw7.context.okf.OkfBundle;
import io.github.adamw7.context.okf.OkfBundler;
import io.github.adamw7.context.tree.ProjectTreeNode;
import io.github.adamw7.tools.mcp.ToolDefinition;

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
public class OkfBundleTool extends AbstractProjectScanTool {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	public OkfBundleTool(PathPolicy pathPolicy) {
		super(pathPolicy);
	}

	private final ToolDefinition toolDefinition = new ToolDefinition("okf_bundle",
			"Scan a Java, Kotlin or Scala project into an Open Knowledge Format (OKF) bundle of markdown documents",
			ContextToolSchema.project(Map.of()));

	@Override
	public ToolDefinition getToolDefinition() {
		return toolDefinition;
	}

	@Override
	protected String render(ProjectTreeNode tree, Language language, Map<String, Object> arguments) {
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
