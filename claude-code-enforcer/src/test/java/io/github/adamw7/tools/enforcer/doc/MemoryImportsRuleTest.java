package io.github.adamw7.tools.enforcer.doc;

import static io.github.adamw7.tools.enforcer.rule.TestFiles.writeString;
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

import io.github.adamw7.tools.enforcer.rule.CapturingLogger;

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

	/** An import an author commented out is one the document no longer makes. */
	@Test
	void ignoresImportsInsideAnHtmlComment() {
		assertDoesNotThrow(ruleFor("# CLAUDE.md\n\n<!--\nWas: @docs/absent.md\n-->\n")::execute);
	}

	/** A mention is a word, not a path, so it names no file the build could be failed over. */
	@Test
	void ignoresABareMentionThatNamesNoPath() {
		assertDoesNotThrow(ruleFor("# CLAUDE.md\n\nAn @claude-mention workflow, opened by @adamw7.\n")::execute);
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

	@Test
	void passesWhenALongChainReachesAFileThatIsAlsoImportedDirectly() {
		writeString(tempDir.resolve("shallow.md"), "See @leaf.md\n");
		writeString(tempDir.resolve("leaf.md"), "# Leaf\n");
		writeString(tempDir.resolve("a.md"), "See @b.md\n");
		writeString(tempDir.resolve("b.md"), "See @c.md\n");
		writeString(tempDir.resolve("c.md"), "See @d.md\n");
		writeString(tempDir.resolve("d.md"), "See @shallow.md\n");
		MemoryImportsRule rule = ruleFor("# CLAUDE.md\n\nSee @shallow.md and @a.md\n");
		rule.setMaxDepth(5);

		// leaf.md is two hops away down the direct chain, so Claude Code loads it;
		// the fact that the a-b-c-d chain also reaches it six hops out does not
		// make it unloadable.
		assertDoesNotThrow(rule::execute);
	}

	@Test
	void reportsADeepChainWhateverOrderTheImportsAreWrittenIn() {
		writeString(tempDir.resolve("shallow.md"), "See @leaf.md\n");
		writeString(tempDir.resolve("leaf.md"), "# Leaf\n");
		writeString(tempDir.resolve("a.md"), "See @b.md\n");
		writeString(tempDir.resolve("b.md"), "See @c.md\n");
		writeString(tempDir.resolve("c.md"), "See @d.md\n");
		writeString(tempDir.resolve("d.md"), "See @shallow.md\n");
		MemoryImportsRule rule = ruleFor("# CLAUDE.md\n\nSee @a.md\n");
		rule.setMaxDepth(5);

		// Same files, but nothing imports shallow.md directly any more, so its
		// only chain is six hops long and leaf.md really is out of reach.
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("nested deeper than 5 hops"), exception.getMessage());
	}

	@Test
	void countsDepthAlongTheShortestChainRegardlessOfImportOrder() {
		writeString(tempDir.resolve("shallow.md"), "See @leaf.md\n");
		writeString(tempDir.resolve("leaf.md"), "# Leaf\n");
		writeString(tempDir.resolve("a.md"), "See @b.md\n");
		writeString(tempDir.resolve("b.md"), "See @c.md\n");
		writeString(tempDir.resolve("c.md"), "See @d.md\n");
		writeString(tempDir.resolve("d.md"), "See @shallow.md\n");
		MemoryImportsRule rule = ruleFor("# CLAUDE.md\n\nSee @a.md and @shallow.md\n");
		rule.setMaxDepth(5);

		// The long chain is written first here and the direct import second; the
		// verdict must not depend on which one the traversal walks into first.
		assertDoesNotThrow(rule::execute);
	}

	@Test
	void stillChecksAnImportThatIsNotOnTheIgnoreList() {
		MemoryImportsRule rule = ruleFor("# CLAUDE.md\n\nSee @docs/absent.md\n");
		rule.setIgnoredImports(List.of("something/else.md"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("imports a missing file"), exception.getMessage());
	}

	@Test
	void acceptsAnImportTargetThatIsNotText() throws IOException {
		Files.write(tempDir.resolve("logo.md"), new byte[] { (byte) 0xC3, (byte) 0x28, (byte) 0xA0 });
		MemoryImportsRule rule = ruleFor("# CLAUDE.md\n\nSee @logo.md\n");
		rule.setLog(new CapturingLogger());

		// An imported file may be any format, so an unreadable one is a leaf that
		// is noted in the debug log rather than a violation.
		assertDoesNotThrow(rule::execute);
	}

	@Test
	void namesTheFileInItsDescription() {
		MemoryImportsRule rule = ruleFor("# CLAUDE.md\n");

		assertTrue(rule.toString().contains("CLAUDE.md"), rule.toString());
	}

	private MemoryImportsRule ruleFor(String content) {
		Path file = tempDir.resolve("CLAUDE.md");
		writeString(file, content);
		MemoryImportsRule rule = new MemoryImportsRule();
		rule.setClaudeMdFile(file.toFile());
		return rule;
	}

}
