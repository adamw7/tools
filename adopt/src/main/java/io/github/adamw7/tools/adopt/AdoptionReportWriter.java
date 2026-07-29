package io.github.adamw7.tools.adopt;

import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Renders the outcome of an adoption run as JSON, to a string or to a file:
 * repository, branch, pull-request URL, whether the run succeeded, and the steps
 * that completed. An absent pull-request URL, and a successful run's
 * {@code failure}, are serialised as JSON {@code null} so the document's shape
 * stays stable for consumers.
 *
 * <p>A run over several repositories is wrapped in a batch document instead: an
 * overall {@code succeeded}, true only when every repository was adopted, plus a
 * {@code repositories} array of exactly those per-repository documents. A single
 * repository is still written unwrapped.
 */
public class AdoptionReportWriter {

	private final ObjectMapper mapper = new ObjectMapper();

	public String toJson(List<AdoptionRun> runs) {
		try {
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(toNode(runs));
		} catch (JsonProcessingException e) {
			throw new AdoptionException("Could not serialise the adoption report", e);
		}
	}

	public void write(Path file, List<AdoptionRun> runs) {
		AdoptionFiles.write(file, toJson(runs), "the adoption report");
	}

	private ObjectNode toNode(List<AdoptionRun> runs) {
		return runs.size() == 1 ? toNode(runs.get(0)) : toBatchNode(runs);
	}

	private ObjectNode toBatchNode(List<AdoptionRun> runs) {
		ObjectNode node = mapper.createObjectNode();
		node.put("succeeded", AdoptionRun.allSucceeded(runs));
		ArrayNode repositories = node.putArray("repositories");
		runs.stream().map(this::toNode).forEach(repositories::add);
		return node;
	}

	private ObjectNode toNode(AdoptionRun run) {
		ObjectNode node = mapper.createObjectNode();
		node.put("repositoryUrl", run.repositoryUrl());
		node.put("branch", run.branchName());
		node.put("pullRequestUrl", run.report().pullRequestUrl().orElse(null));
		node.put("succeeded", run.succeeded());
		node.put("failure", run.failure().orElse(null));
		ArrayNode steps = node.putArray("completedSteps");
		run.report().completedSteps().forEach(steps::add);
		return node;
	}
}
