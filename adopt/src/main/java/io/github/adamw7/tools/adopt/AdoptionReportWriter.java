package io.github.adamw7.tools.adopt;

import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Renders the outcome of an adoption run as JSON, either to a string or to a
 * file, so it — repository, branch, pull-request URL, whether the run succeeded,
 * and the steps that completed — is consumable by scripts and other tools rather
 * than only readable in the logs. A report without a pull-request URL serialises
 * the {@code pullRequestUrl} field as JSON {@code null}, and so does a successful
 * run's {@code failure}, keeping the document's shape stable for consumers.
 *
 * <p>A run over several repositories is written as a batch document instead: an
 * overall {@code succeeded} — true only when every repository was adopted — and a
 * {@code repositories} array of exactly the per-repository documents above.
 * Adopting a single repository keeps writing that per-repository document
 * unwrapped, so the shape a consumer of a one-repository run already parses is
 * unchanged by the list input.
 */
public class AdoptionReportWriter {

	private final ObjectMapper mapper = new ObjectMapper();

	/** Renders a single repository's outcome, the shape nested in a batch document. */
	public String toJson(AdoptionContext context, AdoptionReport report) {
		return toJson(List.of(new AdoptionRun(context, report)));
	}

	public String toJson(List<AdoptionRun> runs) {
		try {
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(toNode(runs));
		} catch (JsonProcessingException e) {
			throw new AdoptionException("Could not serialise the adoption report", e);
		}
	}

	public void write(Path file, AdoptionContext context, AdoptionReport report) {
		write(file, List.of(new AdoptionRun(context, report)));
	}

	public void write(Path file, List<AdoptionRun> runs) {
		AdoptionFiles.write(file, toJson(runs), "the adoption report");
	}

	private ObjectNode toNode(List<AdoptionRun> runs) {
		return runs.size() == 1 ? toNode(runs.get(0)) : toBatchNode(runs);
	}

	private ObjectNode toBatchNode(List<AdoptionRun> runs) {
		ObjectNode node = mapper.createObjectNode();
		node.put("succeeded", runs.stream().allMatch(AdoptionRun::succeeded));
		ArrayNode repositories = node.putArray("repositories");
		runs.stream().map(this::toNode).forEach(repositories::add);
		return node;
	}

	private ObjectNode toNode(AdoptionRun run) {
		ObjectNode node = mapper.createObjectNode();
		node.put("repositoryUrl", run.repositoryUrl());
		node.put("branch", run.context().branchName());
		node.put("pullRequestUrl", run.report().pullRequestUrl().orElse(null));
		node.put("succeeded", run.succeeded());
		node.put("failure", run.failure().orElse(null));
		ArrayNode steps = node.putArray("completedSteps");
		run.report().completedSteps().forEach(steps::add);
		return node;
	}
}
