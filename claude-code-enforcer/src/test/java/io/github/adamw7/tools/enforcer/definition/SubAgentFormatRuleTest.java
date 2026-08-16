package io.github.adamw7.tools.enforcer.definition;

import static io.github.adamw7.tools.test.TestFiles.createDirectory;
import static io.github.adamw7.tools.test.TestFiles.readString;
import static io.github.adamw7.tools.test.TestFiles.writeBytes;
import static io.github.adamw7.tools.test.TestFiles.writeString;
import static io.github.adamw7.tools.test.ExpectedFailures.assertFailure;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.enforcer.rule.CapturingLogger;

class SubAgentFormatRuleTest {

	@TempDir
	private Path tempDir;

	@Test
	void passesWhenEveryDefinitionIsWellFormed() {
		createAgent("reviewer", "claude-opus-4-8");
		createAgent("planner", "claude-sonnet-4-6");

		assertDoesNotThrow(ruleFor(tempDir)::execute);
	}

	@Test
	void passesWhenNoDefinitionsExist() {
		assertDoesNotThrow(ruleFor(tempDir)::execute);
	}

	@Test
	void ignoresNonMarkdownFiles() {
		writeString(tempDir.resolve("notes.txt"), "not an agent");

		assertDoesNotThrow(ruleFor(tempDir)::execute);
	}

	@Test
	void ignoresSubdirectoriesNamedLikeMarkdown() {
		createDirectory(tempDir.resolve("nested.md"));

		assertDoesNotThrow(ruleFor(tempDir)::execute);
	}

	@Test
	void reportsEveryDefinitionProblemTogether() {
		writeString(tempDir.resolve("reviewer.md"), "# Reviewer\nNo front matter.");
		writeString(tempDir.resolve("planner.md"), "# Planner\nNo front matter either.");

		assertFailure(EnforcerRuleException.class, ruleFor(tempDir)::execute, "reviewer.md", "planner.md");
	}

	@Test
	void failsWhenNotConfigured() {
		assertFailure(EnforcerRuleException.class, new SubAgentFormatRule()::execute, "not configured");
	}

	@Test
	void failsWhenDirectoryIsMissing() {
		assertFailure(EnforcerRuleException.class, ruleFor(tempDir.resolve("absent"))::execute, "does not exist");
	}

	@Test
	void failsWhenDefinitionHasNoFrontMatter() {
		writeString(tempDir.resolve("reviewer.md"), "# Reviewer\nNo front matter.");

		assertFailure(EnforcerRuleException.class, ruleFor(tempDir)::execute, "front matter", "reviewer.md");
	}

	@Test
	void failsWhenADescriptionIsMissing() {
		writeString(tempDir.resolve("reviewer.md"), """
				---
				name: reviewer
				---
				# Reviewer
				""");

		assertFailure(EnforcerRuleException.class, ruleFor(tempDir)::execute, "missing 'description:'");
	}

	@Test
	void failsWhenADescriptionIsBlank() {
		writeString(tempDir.resolve("reviewer.md"), """
				---
				name: reviewer
				description:
				---
				# Reviewer
				""");

		assertFailure(EnforcerRuleException.class, ruleFor(tempDir)::execute, "description must not be empty");
	}

	@Test
	void failsWhenNameDoesNotMatchFileName() {
		writeString(tempDir.resolve("reviewer.md"), """
				---
				name: code-reviewer
				description: Reviews code.
				---
				""");

		assertFailure(EnforcerRuleException.class, ruleFor(tempDir)::execute, "must match 'reviewer'");
	}

	@Test
	void failsWhenModelIsNotAllowed() {
		createAgent("reviewer", "claud-opus");
		SubAgentFormatRule rule = ruleFor(tempDir);
		rule.setAllowedModels(List.of("claude-opus-4-8", "claude-sonnet-4-6"));

		assertFailure(EnforcerRuleException.class, rule::execute, "unsupported model 'claud-opus'");
	}

	@Test
	void passesWhenModelIsAllowed() {
		createAgent("reviewer", "claude-opus-4-8");
		SubAgentFormatRule rule = ruleFor(tempDir);
		rule.setAllowedModels(List.of("claude-opus-4-8", "claude-sonnet-4-6"));

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void autoFixRepairsAMissingClosingDelimiter() {
		Path file = tempDir.resolve("reviewer.md");
		writeString(file, """
				---
				name: reviewer
				description: Reviews code.
				# Reviewer
				""");
		SubAgentFormatRule rule = ruleFor(tempDir);
		rule.setAutoFix(true);
		rule.setLog(new CapturingLogger());

		assertDoesNotThrow(rule::execute);
		assertTrue(readString(file).contains("---\n# Reviewer"), readString(file));
	}

	/**
	 * Auto-fix writes the author's file, so it may only write one it has made
	 * readable. This block's delimiters are mendable and its value — quotes that do
	 * not wrap it — is not, and mending the delimiters alone left the rule failing on
	 * the file it had just rewritten, under a log line saying the front matter had
	 * been auto-fixed. The build has to say the same thing it says with auto-fix off,
	 * over the file the author still has.
	 */
	@Test
	void autoFixLeavesADefinitionItCannotMakeReadableAlone() {
		Path file = tempDir.resolve("reviewer.md");
		String original = """
				----
				name: reviewer
				description: "a" and "b"
				----
				# Reviewer
				""";
		writeString(file, original);
		SubAgentFormatRule rule = ruleFor(tempDir);
		CapturingLogger log = new CapturingLogger();
		rule.setAutoFix(true);
		rule.setLog(log);

		assertFailure(EnforcerRuleException.class, rule::execute, "front matter");
		assertEquals(original, readString(file), "the file must be left as its author wrote it");
		assertTrue(log.infos().isEmpty(), () -> "nothing was fixed, so nothing may be logged as fixed: " + log.infos());
	}

	@Test
	void malformedFrontMatterStillFailsWithoutAutoFix() {
		writeString(tempDir.resolve("reviewer.md"), """
				---
				name: reviewer
				description: Reviews code.
				# Reviewer
				""");

		assertFailure(EnforcerRuleException.class, ruleFor(tempDir)::execute, "front matter");
	}

	/** Quoting a value is ordinary YAML, so the name inside the quotes is the one checked. */
	@Test
	void acceptsAQuotedNameAndModel() {
		writeString(tempDir.resolve("reviewer.md"), """
				---
				name: "reviewer"
				description: "Reviews code. Use when: reviewing."
				model: "opus"
				---
				# Reviewer
				""");
		SubAgentFormatRule rule = ruleFor(tempDir);
		rule.setAllowedModels(List.of("opus"));

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void reportsADefinitionThatCannotBeReadAsText() {
		writeBytes(tempDir.resolve("binary.md"), new byte[] { (byte) 0xC3, (byte) 0x28 });

		assertFailure(EnforcerRuleException.class, ruleFor(tempDir)::execute, "cannot be read as text");
	}

	private void createAgent(String name, String model) {
		writeString(tempDir.resolve(name + ".md"), """
				---
				name: %s
				description: A sub-agent named %s.
				model: %s
				---
				# %s
				""".formatted(name, name, model, name));
	}

	private SubAgentFormatRule ruleFor(Path agentsDir) {
		SubAgentFormatRule rule = new SubAgentFormatRule();
		rule.setAgentsDir(agentsDir.toFile());
		return rule;
	}
}
