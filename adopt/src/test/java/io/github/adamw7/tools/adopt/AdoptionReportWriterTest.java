package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
		JsonNode node = mapper.readTree(writer.toJson(List.of(run(context, report))));
		assertEquals("https://github.com/owner/repo.git", node.get("repositoryUrl").asText());
		assertEquals("claude/adopt-claude-code", node.get("branch").asText());
		assertEquals(PR_URL, node.get("pullRequestUrl").asText());
		assertEquals(List.of("clone", "pull-request"), List.of(node.get("completedSteps").get(0).asText(),
				node.get("completedSteps").get(1).asText()));
	}

	@Test
	void serialisesAMissingPullRequestUrlAsNull() throws IOException {
		JsonNode node = mapper.readTree(writer.toJson(List.of(run(context))));
		assertTrue(node.get("pullRequestUrl").isNull());
		assertTrue(node.get("completedSteps").isEmpty());
	}

	@Test
	void serialisesASuccessfulRunAsSucceededWithNoFailure() throws IOException {
		AdoptionReport report = new AdoptionReport();
		report.recordStep("clone");
		JsonNode node = mapper.readTree(writer.toJson(List.of(run(context, report))));
		assertTrue(node.get("succeeded").asBoolean());
		assertTrue(node.get("failure").isNull());
	}

	/**
	 * Without these fields a run that stopped at "push" would serialise exactly like
	 * a pipeline configured to end there, so a consumer could not tell an abandoned
	 * adoption from a complete one.
	 */
	@Test
	void serialisesAFailedRunAsNotSucceededWithTheReason() throws IOException {
		AdoptionReport report = new AdoptionReport();
		report.recordStep("clone");
		report.recordFailure("push: the requested URL returned error: 403");
		JsonNode node = mapper.readTree(writer.toJson(List.of(run(context, report))));
		assertFalse(node.get("succeeded").asBoolean());
		assertEquals("push: the requested URL returned error: 403", node.get("failure").asText());
		assertEquals("clone", node.get("completedSteps").get(0).asText());
	}

	/**
	 * A batch is read repository by repository, so each keeps the document a
	 * single-repository run produces and the wrapper only adds the run's verdict.
	 */
	@Test
	void serialisesABatchAsOneEntryPerRepository() throws IOException {
		AdoptionReport failed = new AdoptionReport();
		failed.recordFailure("clone: repository not found");
		JsonNode node = mapper.readTree(writer.toJson(List.of(run(context), run(other(), failed))));
		assertFalse(node.get("succeeded").asBoolean());
		assertEquals(2, node.get("repositories").size());
		assertEquals("https://github.com/owner/repo.git", node.get("repositories").get(0).get("repositoryUrl").asText());
		assertTrue(node.get("repositories").get(0).get("succeeded").asBoolean());
		assertEquals("clone: repository not found", node.get("repositories").get(1).get("failure").asText());
	}

	@Test
	void serialisesABatchInWhichEveryRepositoryLandedAsSucceeded() throws IOException {
		JsonNode node = mapper.readTree(writer.toJson(List.of(run(context), run(other()))));
		assertTrue(node.get("succeeded").asBoolean());
	}

	/**
	 * A one-repository run keeps the unwrapped document consumers already parse, so
	 * taking a list of URLs did not change the shape of the runs they were written for.
	 */
	@Test
	void serialisesASingleRunUnwrapped() throws IOException {
		JsonNode node = mapper.readTree(writer.toJson(List.of(run(context))));
		assertEquals("https://github.com/owner/repo.git", node.get("repositoryUrl").asText());
		assertTrue(node.get("repositories") == null);
	}

	@Test
	void writesABatchReportToAFile(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("report.json");
		writer.write(file, List.of(run(context), run(other())));
		JsonNode node = mapper.readTree(Files.readString(file));
		assertEquals(2, node.get("repositories").size());
	}

	@Test
	void writesTheReportToAFile(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("nested/report.json");
		AdoptionReport report = new AdoptionReport();
		report.recordPullRequestUrl(PR_URL);
		writer.write(file, List.of(run(context, report)));
		JsonNode node = mapper.readTree(Files.readString(file));
		assertEquals(PR_URL, node.get("pullRequestUrl").asText());
	}

	@Test
	void failsWithAdoptionExceptionWhenTheFileCannotBeWritten(@TempDir Path dir) throws IOException {
		Path blockingFile = Files.createFile(dir.resolve("not-a-directory"));
		Path file = blockingFile.resolve("report.json");
		assertThrows(AdoptionException.class, () -> writer.write(file, List.of(run(context))));
	}

	private AdoptionContext other() {
		return new AdoptionContext("https://github.com/owner/other.git", Path.of("/tmp/workspace"),
				"claude/adopt-claude-code");
	}

	private AdoptionRun run(AdoptionContext context) {
		return run(context, new AdoptionReport());
	}

	private AdoptionRun run(AdoptionContext context, AdoptionReport report) {
		return new AdoptionRun(context, report);
	}
}
