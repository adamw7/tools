package io.github.adamw7.tools.enforcer.settings;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.enforcer.rule.CapturingLogger;

class HooksFormatRuleTest {

	@TempDir
	private Path tempDir;

	@Test
	void passesWhenHooksDirectoryIsAbsent() {
		HooksFormatRule rule = new HooksFormatRule();
		rule.setHooksDir(tempDir.resolve("hooks").toFile());

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void passesForAWellFormedExecutableScript() {
		writeScript("session-start.sh", "#!/bin/sh\necho hi\n", true);

		assertDoesNotThrow(ruleFor()::execute);
	}

	@Test
	void failsWhenNotConfigured() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, new HooksFormatRule()::execute);
		assertTrue(exception.getMessage().contains("not configured"), exception.getMessage());
	}

	@Test
	void failsWhenScriptHasNoShebang() {
		writeScript("session-start.sh", "echo hi\n", true);

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor()::execute);
		assertTrue(exception.getMessage().contains("shebang"), exception.getMessage());
	}

	@Test
	void failsWhenScriptIsNotExecutable() {
		assumeTrue(supportsExecutableBit(), "filesystem has no executable bit to clear");
		writeScript("session-start.sh", "#!/bin/sh\n", false);

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor()::execute);
		assertTrue(exception.getMessage().contains("not executable"), exception.getMessage());
	}

	@Test
	void failsWhenScriptIsEmpty() {
		writeScript("session-start.sh", "   \n", true);

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor()::execute);
		assertTrue(exception.getMessage().contains("empty"), exception.getMessage());
	}

	@Test
	void failsWhenExtensionIsNotAllowed() {
		writeScript("notes.txt", "#!/bin/sh\n", true);
		HooksFormatRule rule = ruleFor();
		rule.setAllowedExtensions(List.of("sh"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("disallowed extension"), exception.getMessage());
	}

	@Test
	void passesWhenScriptChecksAreDisabled() {
		writeScript("session-start.sh", "echo hi\n", false);
		HooksFormatRule rule = ruleFor();
		rule.setRequireShebang(false);
		rule.setRequireExecutable(false);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenSettingsReferencesAMissingHookScript() {
		HooksFormatRule rule = ruleFor();
		rule.setSettingsFile(settingsReferencing("$CLAUDE_PROJECT_DIR/.claude/hooks/gone.sh"));
		rule.setProjectDir(tempDir.toFile());

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("references a missing hook script"), exception.getMessage());
		assertTrue(exception.getMessage().contains("gone.sh"), exception.getMessage());
	}

	@Test
	void passesWhenSettingsReferencesAnExistingScript() {
		writeScript("session-start.sh", "#!/bin/sh\n", true);
		HooksFormatRule rule = ruleFor();
		rule.setSettingsFile(settingsReferencing("$CLAUDE_PROJECT_DIR/.claude/hooks/session-start.sh"));
		rule.setProjectDir(tempDir.toFile());

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenAScriptIsUnreferenced() {
		writeScript("session-start.sh", "#!/bin/sh\n", true);
		writeScript("orphan.sh", "#!/bin/sh\n", true);
		HooksFormatRule rule = ruleFor();
		rule.setSettingsFile(settingsReferencing("$CLAUDE_PROJECT_DIR/.claude/hooks/session-start.sh"));
		rule.setProjectDir(tempDir.toFile());
		rule.setReportUnreferencedScripts(true);

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("not referenced"), exception.getMessage());
		assertTrue(exception.getMessage().contains("orphan.sh"), exception.getMessage());
	}

	@Test
	void warnSeverityLogsInsteadOfFailing() {
		writeScript("session-start.sh", "echo hi\n", true);
		HooksFormatRule rule = ruleFor();
		rule.setSeverity("warn");
		CapturingLogger logger = new CapturingLogger();
		rule.setLog(logger);

		assertDoesNotThrow(rule::execute);
		assertTrue(logger.warnings().stream().anyMatch(w -> w.contains("shebang")), logger.warnings().toString());
	}

	private HooksFormatRule ruleFor() {
		HooksFormatRule rule = new HooksFormatRule();
		rule.setHooksDir(hooksDir().toFile());
		return rule;
	}

	private File settingsReferencing(String command) {
		Path file = tempDir.resolve(".claude").resolve("settings.json");
		writeString(file, """
				{ "hooks": { "SessionStart": [ { "hooks": [ { "type": "command", "command": "%s" } ] } ] } }
				""".formatted(command));
		return file.toFile();
	}

	private Path hooksDir() {
		Path dir = tempDir.resolve(".claude").resolve("hooks");
		createDirectories(dir);
		return dir;
	}

	private void writeScript(String name, String content, boolean executable) {
		Path file = hooksDir().resolve(name);
		writeString(file, content);
		setExecutable(file.toFile(), executable);
	}

	private void setExecutable(File file, boolean executable) {
		if (file.setExecutable(executable) || file.canExecute() == executable) {
			return;
		}
		if (!executable) {
			return; // Windows cannot clear the executable bit; every file stays executable
		}
		throw new IllegalStateException("Could not set executable bit on " + file);
	}

	private boolean supportsExecutableBit() {
		return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
	}

	private void writeString(Path file, String content) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, content);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not write " + file, e);
		}
	}

	private static void createDirectories(Path dir) {
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not create " + dir, e);
		}
	}
}
