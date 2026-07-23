package io.github.adamw7.tools.adopt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Renders an {@link AdoptionReport} as JSON, either to a string or to a file, so
 * the adoption's outcome — repository, branch, pull-request URL, and the steps
 * that completed — is consumable by scripts and other tools rather than only
 * readable in the logs. A report without a pull-request URL serialises the
 * {@code pullRequestUrl} field as JSON {@code null}, keeping the document's
 * shape stable for consumers.
 */
public class AdoptionReportWriter {

	private final ObjectMapper mapper = new ObjectMapper();

	public String toJson(AdoptionContext context, AdoptionReport report) {
		try {
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(toNode(context, report));
		} catch (JsonProcessingException e) {
			throw new AdoptionException("Could not serialise the adoption report", e);
		}
	}

	public void write(Path file, AdoptionContext context, AdoptionReport report) {
		try {
			createParentDirectories(file);
			Files.writeString(file, toJson(context, report));
		} catch (IOException e) {
			throw new AdoptionException("Could not write the adoption report to " + file, e);
		}
	}

	private void createParentDirectories(Path file) throws IOException {
		Path parent = file.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
	}

	private ObjectNode toNode(AdoptionContext context, AdoptionReport report) {
		ObjectNode node = mapper.createObjectNode();
		node.put("repositoryUrl", context.repositoryUrl());
		node.put("branch", context.branchName());
		node.put("pullRequestUrl", report.pullRequestUrl().orElse(null));
		ArrayNode steps = node.putArray("completedSteps");
		report.completedSteps().forEach(steps::add);
		return node;
	}
}
