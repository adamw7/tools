package io.github.adamw7.tools.enforcer.settings;

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

	private static void writeString(Path file, String content) {
		try {
			Files.writeString(file, content);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not write " + file, e);
		}
	}
}
