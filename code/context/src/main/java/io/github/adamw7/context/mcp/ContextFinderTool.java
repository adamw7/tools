package io.github.adamw7.context.mcp;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.github.adamw7.context.ClassContainer;
import io.github.adamw7.context.Language;
import io.github.adamw7.context.PackageAwareFinder;
import io.github.adamw7.tools.mcp.ToolDefinition;

/**
 * MCP tool that resolves the classes a single source file depends on. It loads
 * every source of a Java, Kotlin or Scala project, locates the requested class by
 * its simple name, and runs the package-aware {@link PackageAwareFinder} to a
 * bounded depth so that classes sharing a simple name in different packages are
 * told apart. The dependency class names are returned as a JSON array. An unknown
 * class is reported as an error result rather than an exception so the agent gets
 * a clear, actionable message.
 */
public class ContextFinderTool extends AbstractClassContextTool {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	public ContextFinderTool(PathPolicy pathPolicy) {
		super(pathPolicy);
	}

	private final ToolDefinition toolDefinition = new ToolDefinition("find_context",
			"Find the classes a given class depends on, within a Java, Kotlin or Scala project",
			ContextToolSchema.singleClass());

	@Override
	public ToolDefinition getToolDefinition() {
		return toolDefinition;
	}

	@Override
	protected String result(Set<ClassContainer> containers, ClassContainer target, Language language, int depth) {
		List<String> dependencies = new PackageAwareFinder(containers, language).find(target, depth).stream()
				.map(ClassContainer::className)
				.sorted()
				.toList();
		ArrayNode array = MAPPER.createArrayNode();
		dependencies.forEach(array::add);
		return array.toString();
	}
}
