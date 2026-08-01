package io.github.adamw7.tools.enforcer.settings;

import static io.github.adamw7.tools.enforcer.rule.TestFiles.writeString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.enforcer.rule.CapturingLogger;

class HookCommandsValidRuleTest {

	private static final String NO_HOOKS = """
			{ "permissions": { "allow": [ "Edit" ] } }
			""";

	@TempDir
	private Path tempDir;

	@Test
	void passesWhenNoHooksDeclared() {
		assertDoesNotThrow(ruleFor(NO_HOOKS)::execute);
	}

	@Test
	void passesWhenHookScriptExists() {
		writeString(tempDir.resolve("session-start.sh"), "#!/bin/sh\necho hi\n");
		HookCommandsValidRule rule = ruleFor(hooksReferencing("$CLAUDE_PROJECT_DIR/session-start.sh"));
		rule.setProjectDir(tempDir.toFile());

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void passesWhenCommandOnlyChangesIntoProjectDir() {
		writeString(tempDir.resolve("run.sh"), "#!/bin/sh\necho hi\n");
		HookCommandsValidRule rule = ruleFor(hooksReferencing("cd $CLAUDE_PROJECT_DIR && bash run.sh"));
		rule.setProjectDir(tempDir.toFile());

		// $CLAUDE_PROJECT_DIR here resolves to the project directory itself, which
		// exists, so it must not be reported as a missing script.
		assertDoesNotThrow(rule::execute);
	}

	@Test
	void passesWhenTheScriptReferenceIsQuoted() {
		writeString(tempDir.resolve("session-start.sh"), "#!/bin/sh\necho hi\n");
		HookCommandsValidRule rule = ruleFor(
				hooksReferencing("\\\"$CLAUDE_PROJECT_DIR/session-start.sh\\\""));
		rule.setProjectDir(tempDir.toFile());

		// The shell removes the quotes after expansion, so a quoted reference
		// points at the same existing script as its unquoted spelling.
		assertDoesNotThrow(rule::execute);
	}

	@Test
	void passesWhenTheScriptReferenceIsSingleQuoted() {
		writeString(tempDir.resolve("session-start.sh"), "#!/bin/sh\necho hi\n");
		HookCommandsValidRule rule = ruleFor(hooksReferencing("'$CLAUDE_PROJECT_DIR/session-start.sh'"));
		rule.setProjectDir(tempDir.toFile());

		// Single quotes are stripped just like double quotes, so the reference
		// still points at the same existing script.
		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenAQuotedScriptReferenceIsMissing() {
		HookCommandsValidRule rule = ruleFor(hooksReferencing("\\\"$CLAUDE_PROJECT_DIR/gone.sh\\\""));
		rule.setProjectDir(tempDir.toFile());

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("references a missing script"), exception.getMessage());
	}

	@Test
	void failsWhenNotConfigured() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, new HookCommandsValidRule()::execute);
		assertTrue(exception.getMessage().contains("not configured"), exception.getMessage());
	}

	@Test
	void failsWhenFileIsMissing() {
		HookCommandsValidRule rule = new HookCommandsValidRule();
		rule.setSettingsFile(tempDir.resolve("absent.json").toFile());

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("does not exist"), exception.getMessage());
	}

	@Test
	void failsWhenJsonIsMalformed() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor("{ \"hooks\": ")::execute);
		assertTrue(exception.getMessage().contains("not valid JSON"), exception.getMessage());
	}

	@Test
	void failsWhenHookScriptIsMissing() {
		HookCommandsValidRule rule = ruleFor(hooksReferencing("$CLAUDE_PROJECT_DIR/gone.sh"));
		rule.setProjectDir(tempDir.toFile());

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("references a missing script"), exception.getMessage());
		assertTrue(exception.getMessage().contains("gone.sh"), exception.getMessage());
	}

	@Test
	void failsWhenCommandIsEmpty() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor(hooksReferencing(""))::execute);
		assertTrue(exception.getMessage().contains("empty 'command'"), exception.getMessage());
	}

	@Test
	void failsWhenHookTypeIsMissing() {
		String content = """
				{ "hooks": { "SessionStart": [ { "hooks": [ { "command": "echo hi" } ] } ] } }
				""";

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor(content)::execute);
		assertTrue(exception.getMessage().contains("missing 'type'"), exception.getMessage());
	}

	@Test
	void failsWhenGroupIsMissingHooksArray() {
		String content = """
				{ "hooks": { "SessionStart": [ { "matcher": "*" } ] } }
				""";

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor(content)::execute);
		assertTrue(exception.getMessage().contains("missing a 'hooks' array"), exception.getMessage());
	}

	@Test
	void failsWhenEventIsNotAllowed() {
		writeString(tempDir.resolve("session-start.sh"), "#!/bin/sh\n");
		HookCommandsValidRule rule = ruleFor(hooksReferencing("$CLAUDE_PROJECT_DIR/session-start.sh"));
		rule.setProjectDir(tempDir.toFile());
		rule.setAllowedEvents(List.of("PreToolUse", "PostToolUse"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("is not an allowed event"), exception.getMessage());
	}

	@Test
	void passesWhenScriptReferenceValidationIsDisabled() {
		HookCommandsValidRule rule = ruleFor(hooksReferencing("$CLAUDE_PROJECT_DIR/gone.sh"));
		rule.setProjectDir(tempDir.toFile());
		rule.setValidateScriptReferences(false);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void warnSeverityLogsInsteadOfFailing() {
		HookCommandsValidRule rule = ruleFor(hooksReferencing("$CLAUDE_PROJECT_DIR/gone.sh"));
		rule.setProjectDir(tempDir.toFile());
		rule.setSeverity("warn");
		CapturingLogger logger = new CapturingLogger();
		rule.setLog(logger);

		assertDoesNotThrow(rule::execute);
		assertTrue(logger.warnings().stream().anyMatch(w -> w.contains("gone.sh")), logger.warnings().toString());
	}

	@Test
	void failsWhenHooksIsNotAnObject() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("{ \"hooks\": [ { \"SessionStart\": [] } ] }")::execute);

		// A 'hooks' of the wrong shape used to look exactly like an absent one, so
		// the whole section went unvalidated instead of being reported.
		assertTrue(exception.getMessage().contains("'hooks' must be a JSON object"), exception.getMessage());
	}

	@Test
	void failsWhenHooksIsAString() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("{ \"hooks\": \"SessionStart\" }")::execute);
		assertTrue(exception.getMessage().contains("'hooks' must be a JSON object"), exception.getMessage());
	}

	@Test
	void failsWhenTheSecondOfTwoChainedScriptsIsMissing() {
		writeString(tempDir.resolve("first.sh"), "#!/bin/sh\necho hi\n");
		HookCommandsValidRule rule = ruleFor(
				hooksReferencing("$CLAUDE_PROJECT_DIR/first.sh && $CLAUDE_PROJECT_DIR/second.sh"));
		rule.setProjectDir(tempDir.toFile());

		// Every reference in the command is checked, not just the first one.
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("second.sh"), exception.getMessage());
	}

	@Test
	void passesWhenAQuotedScriptPathContainsASpace() {
		writeString(tempDir.resolve("my hook.sh"), "#!/bin/sh\necho hi\n");
		HookCommandsValidRule rule = ruleFor(hooksReferencing("\\\"$CLAUDE_PROJECT_DIR/my hook.sh\\\""));
		rule.setProjectDir(tempDir.toFile());

		// The quotes hold the path together, so it is not split at the space and
		// then reported as two missing scripts.
		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenAnEventDoesNotMapToAnArray() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("{ \"hooks\": { \"SessionStart\": { \"type\": \"command\" } } }")::execute);
		assertTrue(exception.getMessage().contains("must be a JSON array"), exception.getMessage());
	}

	@Test
	void failsWhenAGroupIsNotAnObject() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("{ \"hooks\": { \"SessionStart\": [ \"not-a-group\" ] } }")::execute);
		assertTrue(exception.getMessage().contains("entry that is not a JSON object"), exception.getMessage());
	}

	@Test
	void failsWhenAHookEntryIsNotAnObject() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("{ \"hooks\": { \"SessionStart\": [ { \"hooks\": [ \"echo hi\" ] } ] } }")::execute);
		assertTrue(exception.getMessage().contains("hook that is not a JSON object"), exception.getMessage());
	}

	@Test
	void passesWhenTheEventIsOnTheAllowedList() {
		HookCommandsValidRule rule = ruleFor(hooksReferencing("echo hi"));
		rule.setAllowedEvents(List.of("SessionStart", "PreToolUse"));

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void leavesAHookOfAnotherTypeAlone() {
		String content = """
				{ "hooks": { "SessionStart": [ { "hooks": [ { "type": "output" } ] } ] } }
				""";

		// Only a command hook carries a 'command', so a hook of another type must
		// not be held to that requirement.
		assertDoesNotThrow(ruleFor(content)::execute);
	}

	@Test
	void passesWhenAChainedCommandEndsAScriptPathWithASemicolon() {
		writeString(tempDir.resolve("first.sh"), "#!/bin/sh\n");
		writeString(tempDir.resolve("second.sh"), "#!/bin/sh\n");
		HookCommandsValidRule rule = ruleFor(
				hooksReferencing("$CLAUDE_PROJECT_DIR/first.sh; $CLAUDE_PROJECT_DIR/second.sh"));
		rule.setProjectDir(tempDir.toFile());

		// The semicolon separates the two commands; keeping it on the first path
		// would look for a script literally named 'first.sh;'.
		assertDoesNotThrow(rule::execute);
	}

	@Test
	void leavesAVariableThatOnlyStartsWithTheProjectDirNameAlone() {
		HookCommandsValidRule rule = ruleFor(hooksReferencing("bash $CLAUDE_PROJECT_DIR_BACKUP/run.sh"));
		rule.setProjectDir(tempDir.toFile());

		// $CLAUDE_PROJECT_DIR_BACKUP is a different variable, so nothing here names a
		// project-local script for the rule to resolve.
		assertDoesNotThrow(rule::execute);
	}

	@Test
	void stillResolvesTheBracedSpellingFollowedByMoreOfThePath() {
		HookCommandsValidRule rule = ruleFor(hooksReferencing("bash ${CLAUDE_PROJECT_DIR}/absent.sh"));
		rule.setProjectDir(tempDir.toFile());

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("references a missing script"), exception.getMessage());
		assertTrue(exception.getMessage().endsWith("absent.sh"), exception.getMessage());
	}

	/**
	 * A hook is as often wired by the repository-relative path Claude Code resolves
	 * against the project directory as by the {@code $CLAUDE_PROJECT_DIR} spelling,
	 * and a script renamed out from under either one is the failure this rule exists
	 * to catch.
	 */
	@Test
	void failsWhenARelativeScriptReferenceIsMissing() {
		HookCommandsValidRule rule = ruleFor(hooksReferencing(".claude/hooks/gone.sh"));
		rule.setProjectDir(tempDir.toFile());

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("references a missing script"), exception.getMessage());
	}

	@Test
	void passesWhenARelativeScriptReferenceExists() {
		writeString(tempDir.resolve(".claude/hooks/session-start.sh"), "#!/bin/sh\necho hi\n");
		HookCommandsValidRule rule = ruleFor(hooksReferencing(".claude/hooks/session-start.sh"));
		rule.setProjectDir(tempDir.toFile());

		assertDoesNotThrow(rule::execute);
	}

	/**
	 * Only the program of a command is a script; an argument that happens to look
	 * like a path is not, and requiring it to exist would fail the build over a file
	 * the hook is about to write.
	 */
	@Test
	void doesNotRequireAnArgumentThatLooksLikeAPath() {
		writeString(tempDir.resolve(".claude/hooks/build.sh"), "#!/bin/sh\necho hi\n");
		HookCommandsValidRule rule = ruleFor(hooksReferencing(".claude/hooks/build.sh --out target/log.txt"));
		rule.setProjectDir(tempDir.toFile());

		assertDoesNotThrow(rule::execute);
	}

	/** A bare program name is looked up on the PATH, so it is no repository file to require. */
	@Test
	void doesNotReadABareProgramNameAsAProjectScript() {
		HookCommandsValidRule rule = ruleFor(hooksReferencing("npx some-linter"));
		rule.setProjectDir(tempDir.toFile());

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void passesWhenScriptReferenceValidationIsDisabledForARelativeScript() {
		HookCommandsValidRule rule = ruleFor(hooksReferencing(".claude/hooks/gone.sh"));
		rule.setProjectDir(tempDir.toFile());
		rule.setValidateScriptReferences(false);

		assertDoesNotThrow(rule::execute);
	}

	/** One script named both ways is one script, so it is not reported — or baselined — twice. */
	@Test
	void reportsAScriptNamedBothWaysOnlyOnce() {
		HookCommandsValidRule rule = ruleFor(
				hooksReferencing("$CLAUDE_PROJECT_DIR/.claude/hooks/gone.sh && .claude/hooks/gone.sh"));
		rule.setProjectDir(tempDir.toFile());

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertEquals(1, exception.getMessage().lines().filter(line -> line.contains("gone.sh")).count(),
				exception.getMessage());
	}

	@Test
	void doesNotRequireAnAbsoluteProgramToLiveInTheProject() {
		HookCommandsValidRule rule = ruleFor(hooksReferencing("/usr/local/bin/absent-tool"));
		rule.setProjectDir(tempDir.toFile());

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void namesTheSettingsFileInItsDescription() {
		HookCommandsValidRule rule = ruleFor(hooksReferencing("echo hi"));

		assertTrue(rule.toString().contains("settings.json"), rule.toString());
	}

	private String hooksReferencing(String command) {
		return """
				{ "hooks": { "SessionStart": [ { "hooks": [ { "type": "command", "command": "%s" } ] } ] } }
				""".formatted(command);
	}

	private HookCommandsValidRule ruleFor(String content) {
		Path file = tempDir.resolve("settings.json");
		writeString(file, content);
		HookCommandsValidRule rule = new HookCommandsValidRule();
		rule.setSettingsFile(file.toFile());
		return rule;
	}

}
