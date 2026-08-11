package io.github.adamw7.tools.adopt;

import java.nio.file.Path;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Renders the outcome of an adoption run as JSON, to a string or to a file:
 * repository, branch, the checkout the adoption was made in, pull-request URL,
 * whether the run succeeded, and the steps that completed. An absent checkout or
 * pull-request URL, and a successful run's {@code failure}, are serialised as JSON
 * {@code null} so the document's shape stays stable for consumers.
 *
 * <p>The checkout is in the document because it is the one output a run has that
 * does not reach GitHub: a dry run publishes nothing, and a caller that named no
 * workspace was given a temporary one it has no other way to find.
 *
 * <p>A run over several repositories is wrapped in a batch document instead: an
 * overall {@code succeeded}, true only when every repository was adopted, plus a
 * {@code repositories} array of exactly those per-repository documents. A single
 * repository is still written unwrapped.
 */
public class AdoptionReportWriter {

	private static final Logger log = LogManager.getLogger(AdoptionReportWriter.class);

	private final ObjectMapper mapper = new ObjectMapper();

	public String toJson(List<AdoptionRun> runs) {
		try {
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(toNode(runs));
		} catch (JsonProcessingException e) {
			throw new AdoptionException("Could not serialise the adoption report", e);
		}
	}

	/**
	 * The path is logged because a run that failed writes one too, and the operator
	 * reading the failure on the console is the one who has to be told there is a
	 * document saying how far it got. {@code --report} otherwise confirms nothing:
	 * a run that stopped before the file was named looks exactly like one that
	 * wrote it.
	 */
	public void write(Path file, List<AdoptionRun> runs) {
		AdoptionFiles.write(file, toJson(runs), "the adoption report");
		log.info("Wrote the adoption report to {}", file);
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
		node.put("checkout", run.report().checkout().orElse(null));
		node.put("pullRequestUrl", run.report().pullRequestUrl().orElse(null));
		node.put("succeeded", run.succeeded());
		node.put("failure", run.failure().orElse(null));
		ArrayNode steps = node.putArray("completedSteps");
		run.report().completedSteps().forEach(steps::add);
		return node;
	}
}
