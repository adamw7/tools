package io.github.adamw7.context.mcp;

import java.util.Map;
import java.util.Set;

import io.github.adamw7.context.ClassContainer;
import io.github.adamw7.context.Language;
import io.github.adamw7.context.ProjectSources;
import io.github.adamw7.tools.mcp.ToolArguments;
import io.github.adamw7.tools.mcp.ToolResult;

/**
 * Shared skeleton for the MCP tools that operate on a single class within a
 * project. On top of the arguments {@link AbstractContextTool} resolves for every
 * tool, they load every source of the requested {@link Language} and locate the
 * target class by its simple name; they differ only in what they compute from that
 * class, which subclasses supply through {@link #result}. Keeping those steps here
 * removes the duplication between {@link ContextFinderTool} and
 * {@link EstimateTokensTool} and keeps their class lookup and result envelopes
 * identical.
 */
abstract class AbstractClassContextTool extends AbstractContextTool {

	protected AbstractClassContextTool(PathPolicy pathPolicy) {
		super(pathPolicy);
	}

	@Override
	protected final ToolResult answer(Scan scan, Map<String, Object> arguments) {
		Set<ClassContainer> containers = Set.copyOf(new ProjectSources(scan.language()).load(scan.root()).values());
		String className = ToolArguments.requiredString(arguments, "class_name");
		ClassContainer target = findTarget(containers, className, scan.language());
		if (target == null) {
			return ToolResult.error("Class not found: " + className);
		}
		return ToolResult.success(result(containers, target, scan.language(), scan.depth()));
	}

	/** Computes the tool's textual result from the located class within its project. */
	protected abstract String result(Set<ClassContainer> containers, ClassContainer target,
			Language language, int depth);

	private ClassContainer findTarget(Set<ClassContainer> containers, String className, Language language) {
		String fileName = fileNameOf(className, language);
		return containers.stream()
				.filter(container -> container.className().equals(fileName))
				.findFirst()
				.orElse(null);
	}

	private String fileNameOf(String className, Language language) {
		if (className.endsWith(language.extension())) {
			return className;
		}
		return className + language.extension();
	}
}
