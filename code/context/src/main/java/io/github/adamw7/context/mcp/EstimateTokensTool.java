package io.github.adamw7.context.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import io.github.adamw7.context.ClassContainer;
import io.github.adamw7.context.Language;
import io.github.adamw7.context.PackageAwareFinder;
import io.github.adamw7.context.SubwordTokenEstimator;
import io.github.adamw7.context.TokenEstimator;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * MCP tool that estimates the LLM token cost of the context assembled for a
 * class: the class itself plus the classes it depends on, to a bounded depth. It
 * reports a per-class token breakdown and the total, letting an agent decide
 * whether the context fits a model's budget before sending it. An unknown class
 * is reported as an error result rather than an exception so the agent gets a
 * clear, actionable message.
 */
public class EstimateTokensTool extends AbstractClassContextTool {

	private final TokenEstimator estimator;

	public EstimateTokensTool(PathPolicy pathPolicy) {
		this(pathPolicy, new SubwordTokenEstimator());
	}

	public EstimateTokensTool(PathPolicy pathPolicy, TokenEstimator estimator) {
		super(pathPolicy);
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
									"description", "source language: java (default), kotlin or scala"),
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
	protected String result(Set<ClassContainer> containers, ClassContainer target, Language language, int depth) {
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
		context.addAll(new PackageAwareFinder(containers, language).find(target, depth));
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
}
