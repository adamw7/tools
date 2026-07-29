package io.github.adamw7.tools.enforcer.doc;

import static io.github.adamw7.tools.enforcer.rule.TestFiles.writeString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
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

	@Test
	void failsWhenAModuleNameOnlyAppearsInsideALongerWord() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor(POM, "The context module stores metadata somewhere.\n")::execute);

		// "metadata" is not a mention of the module called 'data'.
		assertTrue(exception.getMessage().contains("does not mention module 'data'"), exception.getMessage());
	}

	@Test
	void failsWhenAModuleNameOnlyAppearsInsideAHyphenatedName() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("<project><modules><module>code</module></modules></project>",
						"The claude-code-enforcer module.\n")::execute);
		assertTrue(exception.getMessage().contains("does not mention module 'code'"), exception.getMessage());
	}

	@Test
	void acceptsAModuleNamedInsideMarkdownPunctuation() {
		assertDoesNotThrow(ruleFor(POM, "Modules: `data`, and `code/context` for the finder.\n")::execute);
	}

	@Test
	void writesAnHtmlReportExplainingHowToFixTheModuleMap() throws IOException {
		ModuleMapConsistencyRule rule = ruleFor(POM, "Only the data module.\n");
		File report = tempDir.resolve("report.html").toFile();
		rule.setReportFile(report);

		assertThrows(EnforcerRuleException.class, rule::execute);
		String html = Files.readString(report.toPath());
		assertTrue(html.contains("does not mention module &#39;context&#39;"), html);
		assertTrue(html.contains("ignoredModules"), html);
	}

	@Test
	void namesThePomAndDocsInItsDescription() {
		ModuleMapConsistencyRule rule = ruleFor(POM, "The data module and the context module.\n");

		assertTrue(rule.toString().contains("pom.xml"), rule.toString());
		assertTrue(rule.toString().contains("CLAUDE.md"), rule.toString());
	}

	private ModuleMapConsistencyRule ruleFor(String pomContent, String docContent) {
		ModuleMapConsistencyRule rule = new ModuleMapConsistencyRule();
		rule.setPomFile(write("pom.xml", pomContent).toFile());
		rule.setDocFiles(List.of(write("CLAUDE.md", docContent).toFile()));
		return rule;
	}

	private Path write(String name, String content) {
		return writeString(tempDir.resolve(name), content);
	}
}
