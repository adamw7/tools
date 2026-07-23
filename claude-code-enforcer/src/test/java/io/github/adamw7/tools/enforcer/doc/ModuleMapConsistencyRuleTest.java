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

class ModuleMapConsistencyRuleTest {

	private static final String POM = """
			<project>
			  <modules>
			    <module>data</module>
			    <module>code/context</module>
			    <!-- <module>disabled</module> -->
			  </modules>
			</project>
			""";

	@TempDir
	private Path tempDir;

	@Test
	void passesWhenEveryModuleIsMentioned() {
		assertDoesNotThrow(ruleFor(POM, "The data module and the context module.\n")::execute);
	}

	@Test
	void failsWhenNotConfigured() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				new ModuleMapConsistencyRule()::execute);
		assertTrue(exception.getMessage().contains("not configured"), exception.getMessage());
	}

	@Test
	void failsWhenDocFilesIsEmpty() {
		ModuleMapConsistencyRule rule = new ModuleMapConsistencyRule();
		rule.setPomFile(write("pom.xml", POM).toFile());
		rule.setDocFiles(List.of());

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("at least one file"), exception.getMessage());
	}

	@Test
	void failsWhenThePomDeclaresNoModules() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("<project></project>", "docs")::execute);
		assertTrue(exception.getMessage().contains("declares no <module> entries"), exception.getMessage());
	}

	@Test
	void failsWhenAModuleIsNotMentioned() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor(POM, "Only the data module.\n")::execute);
		assertTrue(exception.getMessage().contains("does not mention module 'context'"), exception.getMessage());
	}

	@Test
	void looksUpANestedModuleByItsLastSegment() {
		assertDoesNotThrow(ruleFor(POM, "data and context are documented.\n")::execute);
	}

	@Test
	void ignoresCommentedOutModules() {
		assertDoesNotThrow(ruleFor(POM, "data and context, but never the disabled one by name.\n")::execute);
	}

	@Test
	void skipsIgnoredModules() {
		ModuleMapConsistencyRule rule = ruleFor(POM, "Only the data module.\n");
		rule.setIgnoredModules(List.of("context"));

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void checksEveryConfiguredDoc() {
		ModuleMapConsistencyRule rule = ruleFor(POM, "The data module and the context module.\n");
		Path second = write("README.md", "Only data here.\n");
		rule.setDocFiles(List.of(tempDir.resolve("CLAUDE.md").toFile(), second.toFile()));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("README.md"), exception.getMessage());
		assertTrue(exception.getMessage().contains("'context'"), exception.getMessage());
	}

	private ModuleMapConsistencyRule ruleFor(String pomContent, String docContent) {
		ModuleMapConsistencyRule rule = new ModuleMapConsistencyRule();
		rule.setPomFile(write("pom.xml", pomContent).toFile());
		rule.setDocFiles(List.of(write("CLAUDE.md", docContent).toFile()));
		return rule;
	}

	private Path write(String name, String content) {
		Path file = tempDir.resolve(name);
		try {
			Files.writeString(file, content);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not write " + file, e);
		}
		return file;
	}
}
