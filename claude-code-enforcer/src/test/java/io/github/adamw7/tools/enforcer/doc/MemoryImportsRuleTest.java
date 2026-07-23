package io.github.adamw7.tools.enforcer.doc;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MemoryImportsRuleTest {

	@TempDir
	private Path tempDir;

	@Test
	void passesWhenThereAreNoImports() {
		assertDoesNotThrow(ruleFor("# CLAUDE.md\n\nNo imports here.\n")::execute);
	}

	@Test
	void passesWhenImportsResolve() {
		writeString(tempDir.resolve("docs.md"), "# Docs\n");
		assertDoesNotThrow(ruleFor("# CLAUDE.md\n\nSee @docs.md for details.\n")::execute);
	}

	@Test
	void failsWhenNotConfigured() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, new MemoryImportsRule()::execute);
		assertTrue(exception.getMessage().contains("not configured"), exception.getMessage());
	}

	@Test
	void failsWhenTheFileIsMissing() {
		MemoryImportsRule rule = new MemoryImportsRule();
		rule.setClaudeMdFile(tempDir.resolve("absent.md").toFile());

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("does not exist"), exception.getMessage());
	}

	@Test
	void failsForAMissingImportTarget() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("# CLAUDE.md\n\nSee @docs/absent.md\n")::execute);
		assertTrue(exception.getMessage().contains("imports a missing file: @docs/absent.md"),
				exception.getMessage());
	}

	@Test
	void dropsSentencePunctuationFromTheImportPath() {
		writeString(tempDir.resolve("docs.md"), "# Docs\n");
		assertDoesNotThrow(ruleFor("# CLAUDE.md\n\nSee @docs.md.\n")::execute);
	}

	@Test
	void ignoresImportsInFencedCodeBlocks() {
		assertDoesNotThrow(ruleFor("# CLAUDE.md\n\n```\n@docs/absent.md\n```\n")::execute);
	}

	@Test
	void ignoresImportsInInlineCodeSpans() {
		assertDoesNotThrow(ruleFor("# CLAUDE.md\n\nan `@claude`-mention workflow\n")::execute);
	}

	@Test
	void ignoresTokensNotPrecededByWhitespace() {
		assertDoesNotThrow(ruleFor("# CLAUDE.md\n\nMail adam@example.com about it.\n")::execute);
	}

	@Test
	void ignoresHomeRelativeImports() {
		assertDoesNotThrow(ruleFor("# CLAUDE.md\n\nAlso @~/personal/prefs.md is loaded.\n")::execute);
	}

	@Test
	void skipsExplicitlyIgnoredImports() {
		MemoryImportsRule rule = ruleFor("# CLAUDE.md\n\nSee @docs/absent.md\n");
		rule.setIgnoredImports(List.of("docs/absent.md"));

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void followsImportsRecursively() {
		writeString(tempDir.resolve("first.md"), "See @second.md\n");
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("# CLAUDE.md\n\nSee @first.md\n")::execute);
		assertTrue(exception.getMessage().contains("imports a missing file: @second.md"), exception.getMessage());
	}

	@Test
	void failsForACircularImport() {
		writeString(tempDir.resolve("loop.md"), "Back to @CLAUDE.md\n");
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("# CLAUDE.md\n\nSee @loop.md\n")::execute);
		assertTrue(exception.getMessage().contains("circular import: @CLAUDE.md"), exception.getMessage());
	}

	@Test
	void failsWhenTheChainExceedsMaxDepth() {
		writeString(tempDir.resolve("first.md"), "See @second.md\n");
		writeString(tempDir.resolve("second.md"), "# Deep\n");
		MemoryImportsRule rule = ruleFor("# CLAUDE.md\n\nSee @first.md\n");
		rule.setMaxDepth(1);

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("nested deeper than 1 hops"), exception.getMessage());
	}

	@Test
	void resolvesImportsRelativeToTheImportingFile() {
		Path nested = tempDir.resolve("docs");
		writeString(nested.resolve("inner.md"), "See @sibling.md\n");
		writeString(nested.resolve("sibling.md"), "# Sibling\n");
		assertDoesNotThrow(ruleFor("# CLAUDE.md\n\nSee @docs/inner.md\n")::execute);
	}

	private MemoryImportsRule ruleFor(String content) {
		Path file = tempDir.resolve("CLAUDE.md");
		writeString(file, content);
		MemoryImportsRule rule = new MemoryImportsRule();
		rule.setClaudeMdFile(file.toFile());
		return rule;
	}

	private static void writeString(Path file, String content) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, content);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not write " + file, e);
		}
	}
}
