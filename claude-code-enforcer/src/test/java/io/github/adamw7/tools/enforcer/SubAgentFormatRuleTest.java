package io.github.adamw7.tools.enforcer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SubAgentFormatRuleTest {

	@TempDir
	private Path tempDir;

	@Test
	void passesWhenEveryDefinitionIsWellFormed() throws IOException {
		createAgent("reviewer", "claude-opus-4-8");
		createAgent("planner", "claude-sonnet-4-6");

		assertDoesNotThrow(ruleFor(tempDir)::execute);
	}

	@Test
	void passesWhenNoDefinitionsExist() {
		assertDoesNotThrow(ruleFor(tempDir)::execute);
	}

	@Test
	void ignoresNonMarkdownFiles() throws IOException {
		Files.writeString(tempDir.resolve("notes.txt"), "not an agent");

		assertDoesNotThrow(ruleFor(tempDir)::execute);
	}

	@Test
	void failsWhenNotConfigured() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, new SubAgentFormatRule()::execute);
		assertTrue(exception.getMessage().contains("not configured"), exception.getMessage());
	}

	@Test
	void failsWhenDirectoryIsMissing() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor(tempDir.resolve("absent"))::execute);
		assertTrue(exception.getMessage().contains("does not exist"), exception.getMessage());
	}

	@Test
	void failsWhenDefinitionHasNoFrontMatter() throws IOException {
		Files.writeString(tempDir.resolve("reviewer.md"), "# Reviewer\nNo front matter.");

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor(tempDir)::execute);
		assertTrue(exception.getMessage().contains("front matter"), exception.getMessage());
		assertTrue(exception.getMessage().contains("reviewer.md"), exception.getMessage());
	}

	@Test
	void failsWhenADescriptionIsMissing() throws IOException {
		Files.writeString(tempDir.resolve("reviewer.md"), """
				---
				name: reviewer
				---
				# Reviewer
				""");

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor(tempDir)::execute);
		assertTrue(exception.getMessage().contains("missing 'description:'"), exception.getMessage());
	}

	@Test
	void failsWhenNameDoesNotMatchFileName() throws IOException {
		Files.writeString(tempDir.resolve("reviewer.md"), """
				---
				name: code-reviewer
				description: Reviews code.
				---
				""");

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor(tempDir)::execute);
		assertTrue(exception.getMessage().contains("must match 'reviewer'"), exception.getMessage());
	}

	@Test
	void failsWhenModelIsNotAllowed() throws IOException {
		createAgent("reviewer", "claud-opus");
		SubAgentFormatRule rule = ruleFor(tempDir);
		rule.setAllowedModels(List.of("claude-opus-4-8", "claude-sonnet-4-6"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("unsupported model 'claud-opus'"), exception.getMessage());
	}

	@Test
	void passesWhenModelIsAllowed() throws IOException {
		createAgent("reviewer", "claude-opus-4-8");
		SubAgentFormatRule rule = ruleFor(tempDir);
		rule.setAllowedModels(List.of("claude-opus-4-8", "claude-sonnet-4-6"));

		assertDoesNotThrow(rule::execute);
	}

	private void createAgent(String name, String model) throws IOException {
		Files.writeString(tempDir.resolve(name + ".md"), """
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
