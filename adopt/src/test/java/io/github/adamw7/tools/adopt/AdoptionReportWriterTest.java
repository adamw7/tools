package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class AdoptionReportWriterTest {

	private static final String PR_URL = "https://github.com/owner/repo/pull/1";

	private final AdoptionContext context = new AdoptionContext("https://github.com/owner/repo.git",
			Path.of("/tmp/workspace"), "claude/adopt-claude-code");
	private final AdoptionReportWriter writer = new AdoptionReportWriter();
	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	void serialisesTheRunOutcome() throws IOException {
		AdoptionReport report = new AdoptionReport();
		report.recordStep("clone");
		report.recordStep("pull-request");
		report.recordPullRequestUrl(PR_URL);
		JsonNode node = mapper.readTree(writer.toJson(context, report));
		assertEquals("https://github.com/owner/repo.git", node.get("repositoryUrl").asText());
		assertEquals("claude/adopt-claude-code", node.get("branch").asText());
		assertEquals(PR_URL, node.get("pullRequestUrl").asText());
		assertEquals(List.of("clone", "pull-request"), List.of(node.get("completedSteps").get(0).asText(),
				node.get("completedSteps").get(1).asText()));
	}

	@Test
	void serialisesAMissingPullRequestUrlAsNull() throws IOException {
		JsonNode node = mapper.readTree(writer.toJson(context, new AdoptionReport()));
		assertTrue(node.get("pullRequestUrl").isNull());
		assertTrue(node.get("completedSteps").isEmpty());
	}

	@Test
	void writesTheReportToAFile(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("nested/report.json");
		AdoptionReport report = new AdoptionReport();
		report.recordPullRequestUrl(PR_URL);
		writer.write(file, context, report);
		JsonNode node = mapper.readTree(Files.readString(file));
		assertEquals(PR_URL, node.get("pullRequestUrl").asText());
	}

	@Test
	void failsWithAdoptionExceptionWhenTheFileCannotBeWritten(@TempDir Path dir) throws IOException {
		Path blockingFile = Files.createFile(dir.resolve("not-a-directory"));
		Path file = blockingFile.resolve("report.json");
		assertThrows(AdoptionException.class, () -> writer.write(file, context, new AdoptionReport()));
	}
}
