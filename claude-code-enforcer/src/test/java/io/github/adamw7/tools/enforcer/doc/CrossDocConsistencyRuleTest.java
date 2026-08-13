package io.github.adamw7.tools.enforcer.doc;

import static io.github.adamw7.tools.test.TestFiles.writeString;
import static io.github.adamw7.tools.test.ExpectedFailures.assertFailure;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.nio.file.Path;
import java.util.List;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CrossDocConsistencyRuleTest {

	@TempDir
	private Path tempDir;

	@Test
	void passesWhenCapturedValuesAgree() {
		CrossDocConsistencyRule rule = ruleFor("Java 25 is required.", "We target Java 25.");
		rule.setConsistentPatterns(List.of("Java (\\d+)"));

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenCapturedValuesDiffer() {
		CrossDocConsistencyRule rule = ruleFor("Java 25 is required.", "We target Java 24.");
		rule.setConsistentPatterns(List.of("Java (\\d+)"));

		assertFailure(EnforcerRuleException.class, rule::execute, "'25'", "'24'");
	}

	@Test
	void failsWhenAFactAppearsInOnlyOneDocument() {
		CrossDocConsistencyRule rule = ruleFor("Java 25 is required.", "No version here.");
		rule.setConsistentPatterns(List.of("Java (\\d+)"));

		assertFailure(EnforcerRuleException.class, rule::execute, "<absent>");
	}

	@Test
	void ignoresPatternsThatMatchNeitherDocument() {
		CrossDocConsistencyRule rule = ruleFor("Nothing relevant.", "Also nothing.");
		rule.setConsistentPatterns(List.of("Java (\\d+)"));

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void passesWhenNoPatternsAreConfigured() {
		assertDoesNotThrow(ruleFor("a", "b")::execute);
	}

	@Test
	void failsWithAClearMessageWhenAPatternHasNoCapturingGroup() {
		CrossDocConsistencyRule rule = ruleFor("Java 25.", "Java 25.");
		rule.setConsistentPatterns(List.of("Java \\d+"));

		assertFailure(EnforcerRuleException.class, rule::execute, "must declare a capturing group");
	}

	@Test
	void treatsANonParticipatingOptionalGroupAsAbsent() {
		CrossDocConsistencyRule rule = ruleFor("Uses proto3 here.", "No proto mentioned.");
		rule.setConsistentPatterns(List.of("proto(\\d)?"));

		// The group is optional, so a bare "proto" match captures null. That must be
		// treated as a mismatch against the concrete "3", not throw a NullPointerException.
		assertFailure(EnforcerRuleException.class, rule::execute, "'3'");
	}

	@Test
	void failsWhenAFileIsMissing() {
		CrossDocConsistencyRule rule = new CrossDocConsistencyRule();
		rule.setClaudeMdFile(tempDir.resolve("absent-claude.md").toFile());
		rule.setAgentsMdFile(tempDir.resolve("absent-agents.md").toFile());

		assertFailure(EnforcerRuleException.class, rule::execute, "does not exist");
	}

	@Test
	void failsInsteadOfHangingOnABacktrackingPattern() {
		// A super-linear pattern over a crafted document that never matches: without
		// the step budget the match blows up on the document length instead of failing.
		String craftedContent = "x".repeat(800) + "z";
		CrossDocConsistencyRule rule = ruleFor(craftedContent, craftedContent);
		rule.setConsistentPatterns(List.of("(x+x+)+y"));

		assertFailure(EnforcerRuleException.class, rule::execute, "catastrophic backtracking");
	}

	@Test
	void failsWithAClearMessageForAPatternThatIsNotAValidRegularExpression() {
		CrossDocConsistencyRule rule = ruleFor("# CLAUDE.md\nJava 25\n", "# AGENTS.md\nJava 25\n");
		rule.setConsistentPatterns(List.of("Java (\\d+"));

		// A misconfigured pattern is a build-setup mistake and must name itself,
		// rather than escaping as a PatternSyntaxException the build reports as an
		// internal error.
		assertFailure(EnforcerRuleException.class, rule::execute, "not a valid regular expression", "Java (\\d+");
	}

	private CrossDocConsistencyRule ruleFor(String claudeContent, String agentsContent) {
		Path claude = tempDir.resolve("CLAUDE.md");
		Path agents = tempDir.resolve("AGENTS.md");
		writeString(claude, claudeContent);
		writeString(agents, agentsContent);
		CrossDocConsistencyRule rule = new CrossDocConsistencyRule();
		rule.setClaudeMdFile(claude.toFile());
		rule.setAgentsMdFile(agents.toFile());
		return rule;
	}

}
