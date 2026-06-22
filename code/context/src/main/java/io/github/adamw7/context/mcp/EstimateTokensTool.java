package io.github.adamw7.context.mcp;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import io.github.adamw7.context.ClassContainer;
import io.github.adamw7.context.Finder;
import io.github.adamw7.context.HeuristicTokenEstimator;
import io.github.adamw7.context.Language;
import io.github.adamw7.context.ProjectSources;
import io.github.adamw7.context.TokenEstimator;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * MCP tool that estimates the LLM token cost of the context assembled for a
 * class: the class itself plus the classes it depends on, to a bounded depth. It
 * reports a per-class token breakdown and the total, letting an agent decide
 * whether the context fits a model's budget before sending it. An unknown class
 * is reported as an error result rather than an exception so the agent gets a
 * clear, actionable message.
 */
public class EstimateTokensTool implements ContextTool {

	private static final Logger log = LogManager.getLogger(EstimateTokensTool.class.getName());

	private static final int DEFAULT_DEPTH = 1;
	private static final int MAX_DEPTH = 10;

	private final PathPolicy pathPolicy;
	private final TokenEstimator estimator;

	public EstimateTokensTool(PathPolicy pathPolicy) {
		this(pathPolicy, new HeuristicTokenEstimator());
	}

	public EstimateTokensTool(PathPolicy pathPolicy, TokenEstimator estimator) {
		this.pathPolicy = pathPolicy;
		this.estimator = estimator;
	}

	private final Tool toolDefinition = Tool.builder("estimate_tokens",
			Map.of(
					"type", "object",
					"properties", Map.of(
							"path", Map.of("type", "string",
									"description", "absolute path to the project root directory"),
							"class_name", Map.of("type", "string",
									"description", "simple name of the class to inspect, e.g. Foo or Foo.java"),
							"language", Map.of("type", "string",
									"description", "source language: java (default) or kotlin"),
							"depth", Map.of("type", "integer",
									"description", "how many levels of transitive dependencies to include (default 1)")),
					"required", List.of("path", "class_name")))
			.description("Estimate the LLM token cost of the context assembled for a class and its dependencies")
			.build();

	@Override
	public Tool getToolDefinition() {
		return toolDefinition;
	}

	@Override
	public CallToolResult apply(Map<String, Object> arguments) {
		log.info("Calling MCP estimate_tokens tool for {}", arguments);
		Path root = pathPolicy.resolve(ToolArguments.requiredString(arguments, "path"));
		Language language = ToolArguments.optionalLanguage(arguments, "language", Language.JAVA);
		int depth = ToolArguments.optionalBoundedInt(arguments, "depth", DEFAULT_DEPTH, 0, MAX_DEPTH);
		Set<ClassContainer> containers = Set.copyOf(new ProjectSources(language).load(root).values());

		ClassContainer target = findTarget(containers, arguments, language);
		if (target == null) {
			return error("Class not found: " + ToolArguments.requiredString(arguments, "class_name"));
		}
		return success(estimate(containers, target, language, depth));
	}

	private ClassContainer findTarget(Set<ClassContainer> containers, Map<String, Object> arguments, Language language) {
		String fileName = fileNameOf(ToolArguments.requiredString(arguments, "class_name"), language);
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

	private String estimate(Set<ClassContainer> containers, ClassContainer target, Language language, int depth) {
		List<ClassContainer> context = assembleContext(containers, target, language, depth);
		JSONArray classes = new JSONArray();
		int total = 0;
		for (ClassContainer container : context) {
			int tokens = estimator.estimate(container.originalCode());
			total += tokens;
			classes.put(classEntry(container, tokens));
		}
		return report(total, classes).toString();
	}

	private List<ClassContainer> assembleContext(Set<ClassContainer> containers, ClassContainer target,
			Language language, int depth) {
		List<ClassContainer> context = new ArrayList<>();
		context.add(target);
		context.addAll(new Finder(containers, language).find(target, depth));
		return context;
	}

	private JSONObject classEntry(ClassContainer container, int tokens) {
		JSONObject entry = new JSONObject();
		entry.put("class", container.className());
		entry.put("tokens", tokens);
		return entry;
	}

	private JSONObject report(int total, JSONArray classes) {
		JSONObject report = new JSONObject();
		report.put("total", total);
		report.put("classes", classes);
		return report;
	}

	private CallToolResult success(String reportJson) {
		return CallToolResult.builder()
				.content(List.of(TextContent.builder(reportJson).build()))
				.isError(false)
				.build();
	}

	private CallToolResult error(String message) {
		return CallToolResult.builder()
				.content(List.of(TextContent.builder(message).build()))
				.isError(true)
				.build();
	}
}
