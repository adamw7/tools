package io.github.adamw7.tools.enforcer.settings;

import static io.github.adamw7.tools.enforcer.rule.TestFiles.assumeSymlink;
import static io.github.adamw7.tools.enforcer.rule.TestFiles.createDirectory;
import static io.github.adamw7.tools.enforcer.rule.TestFiles.writeString;
import static io.github.adamw7.tools.test.ExpectedFailures.assertFailure;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.enforcer.rule.CapturingLogger;
import io.github.adamw7.tools.enforcer.rule.TestFiles;

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
		assertFailure(EnforcerRuleException.class, new HooksFormatRule()::execute, "not configured");
	}

	@Test
	void failsWhenScriptHasNoShebang() {
		writeScript("session-start.sh", "echo hi\n", true);

		assertFailure(EnforcerRuleException.class, ruleFor()::execute, "shebang");
	}

	@Test
	void failsWhenScriptIsNotExecutable() {
		assumeTrue(supportsExecutableBit(), "filesystem has no executable bit to clear");
		writeScript("session-start.sh", "#!/bin/sh\n", false);

		assertFailure(EnforcerRuleException.class, ruleFor()::execute, "not executable");
	}

	/**
	 * The kernel reads bytes: a mark before the {@code #!} makes the script
	 * unrunnable, however well formed the text behind the mark reads.
	 */
	@Test
	void failsWhenAByteOrderMarkPrecedesTheShebang() {
		writeScript("session-start.sh", (char) 0xFEFF + "#!/bin/sh\necho hi\n", true);

		assertFailure(EnforcerRuleException.class, ruleFor()::execute, "byte-order mark", "session-start.sh");
	}

	/** A script with no shebang is named for the shebang it lacks, mark or no mark. */
	@Test
	void reportsTheMissingShebangRatherThanTheMarkWhenThereIsNoShebangAtAll() {
		writeScript("session-start.sh", (char) 0xFEFF + "echo hi\n", true);

		EnforcerRuleException exception = assertFailure(EnforcerRuleException.class, ruleFor()::execute, "shebang");
		assertFalse(exception.getMessage().contains("byte-order mark"), exception.getMessage());
	}

	@Test
	void passesForAByteOrderMarkWhenTheShebangCheckIsOff() {
		writeScript("session-start.sh", (char) 0xFEFF + "#!/bin/sh\n", true);
		HooksFormatRule rule = ruleFor();
		rule.setRequireShebang(false);
		rule.setRequireExecutable(false);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenScriptIsEmpty() {
		writeScript("session-start.sh", "   \n", true);

		assertFailure(EnforcerRuleException.class, ruleFor()::execute, "empty");
	}

	@Test
	void failsWhenExtensionIsNotAllowed() {
		writeScript("notes.txt", "#!/bin/sh\n", true);
		HooksFormatRule rule = ruleFor();
		rule.setAllowedExtensions(List.of("sh"));

		assertFailure(EnforcerRuleException.class, rule::execute, "disallowed extension");
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

		assertFailure(EnforcerRuleException.class, rule::execute, "references a missing hook script", "gone.sh");
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

		assertFailure(EnforcerRuleException.class, rule::execute, "not referenced", "orphan.sh");
	}

	/**
	 * A settings.json is as likely to wire a hook by the repository-relative path
	 * Claude Code resolves against the project directory. Reading only the
	 * {@code $CLAUDE_PROJECT_DIR} spelling left the script it really does reference
	 * reported as referenced by nothing.
	 */
	@Test
	void treatsARelativeReferenceAsReferencing() {
		writeScript("session-start.sh", "#!/bin/sh\n", true);
		HooksFormatRule rule = ruleFor();
		rule.setSettingsFile(settingsReferencing(".claude/hooks/session-start.sh"));
		rule.setProjectDir(tempDir.toFile());
		rule.setReportUnreferencedScripts(true);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenSettingsReferencesAMissingScriptRelatively() {
		HooksFormatRule rule = ruleFor();
		rule.setSettingsFile(settingsReferencing(".claude/hooks/gone.sh"));
		rule.setProjectDir(tempDir.toFile());

		assertFailure(EnforcerRuleException.class, rule::execute, "references a missing hook script", "gone.sh");
	}

	/** A hook chaining two scripts references both, whichever spelling each is written with. */
	@Test
	void treatsEveryScriptOfAMixedChainedCommandAsReferenced() {
		writeScript("first.sh", "#!/bin/sh\n", true);
		writeScript("second.sh", "#!/bin/sh\n", true);
		HooksFormatRule rule = ruleFor();
		rule.setSettingsFile(settingsReferencing(
				"$CLAUDE_PROJECT_DIR/.claude/hooks/first.sh && .claude/hooks/second.sh"));
		rule.setProjectDir(tempDir.toFile());
		rule.setReportUnreferencedScripts(true);

		assertDoesNotThrow(rule::execute);
	}

	/** A hook wired as {@code bash <script>} references that script just as plainly. */
	@Test
	void treatsTheScriptAnInterpreterIsHandedAsReferenced() {
		writeScript("session-start.sh", "#!/bin/sh\n", true);
		HooksFormatRule rule = ruleFor();
		rule.setSettingsFile(settingsReferencing("bash $CLAUDE_PROJECT_DIR/.claude/hooks/session-start.sh"));
		rule.setProjectDir(tempDir.toFile());
		rule.setReportUnreferencedScripts(true);

		assertDoesNotThrow(rule::execute);
	}

	/**
	 * An output path is an argument whichever spelling names it. Expanding every token
	 * that mentioned the variable made the hooks directory's own scripts look
	 * referenced by a path the hook was only about to write.
	 */
	@Test
	void doesNotTreatAVariableRootedArgumentAsAScript() {
		writeScript("session-start.sh", "#!/bin/sh\n", true);
		HooksFormatRule rule = ruleFor();
		rule.setSettingsFile(settingsReferencing(
				".claude/hooks/session-start.sh --out $CLAUDE_PROJECT_DIR/.claude/hooks/generated.log"));
		rule.setProjectDir(tempDir.toFile());

		assertDoesNotThrow(rule::execute);
	}

	/**
	 * A hook run under {@code set -euo pipefail} hands {@code pipefail} to the
	 * {@code -o} ending the cluster. Reading that word as the script left the script
	 * behind it unread, and so reported a script the hook really does run as
	 * referenced by nothing.
	 */
	@Test
	void treatsTheScriptBehindAnOptionValueAsReferenced() {
		writeScript("session-start.sh", "#!/bin/sh\n", true);
		HooksFormatRule rule = ruleFor();
		rule.setSettingsFile(settingsReferencing("bash -euo pipefail .claude/hooks/session-start.sh"));
		rule.setProjectDir(tempDir.toFile());
		rule.setReportUnreferencedScripts(true);

		assertDoesNotThrow(rule::execute);
	}

	/** An argument is not a script, so it cannot mark one referenced or be required to exist. */
	@Test
	void doesNotTreatAnArgumentThatLooksLikeAPathAsAScript() {
		writeScript("build.sh", "#!/bin/sh\n", true);
		HooksFormatRule rule = ruleFor();
		rule.setSettingsFile(settingsReferencing(".claude/hooks/build.sh --out .claude/hooks/absent.sh"));
		rule.setProjectDir(tempDir.toFile());
		rule.setReportUnreferencedScripts(true);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void treatsAQuotedReferenceAsReferencing() {
		writeScript("session-start.sh", "#!/bin/sh\n", true);
		HooksFormatRule rule = ruleFor();
		rule.setSettingsFile(settingsReferencing("\\\"$CLAUDE_PROJECT_DIR/.claude/hooks/session-start.sh\\\""));
		rule.setProjectDir(tempDir.toFile());
		rule.setReportUnreferencedScripts(true);

		// The quotes are stripped before expansion, so the quoted reference marks
		// the existing script as referenced rather than leaving it an orphan.
		assertDoesNotThrow(rule::execute);
	}

	@Test
	void treatsEveryScriptAChainedCommandReferencesAsReferenced() {
		writeScript("first.sh", "#!/bin/sh\n", true);
		writeScript("second.sh", "#!/bin/sh\n", true);
		HooksFormatRule rule = ruleFor();
		rule.setSettingsFile(settingsReferencing("$CLAUDE_PROJECT_DIR/.claude/hooks/first.sh"
				+ " && $CLAUDE_PROJECT_DIR/.claude/hooks/second.sh"));
		rule.setProjectDir(tempDir.toFile());
		rule.setReportUnreferencedScripts(true);

		// Both references count. Stopping at the first one left second.sh looking
		// like an orphan even though the very same command runs it.
		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenTheSecondScriptOfAChainedCommandIsMissing() {
		writeScript("first.sh", "#!/bin/sh\n", true);
		HooksFormatRule rule = ruleFor();
		rule.setSettingsFile(settingsReferencing("$CLAUDE_PROJECT_DIR/.claude/hooks/first.sh"
				+ " && $CLAUDE_PROJECT_DIR/.claude/hooks/gone.sh"));
		rule.setProjectDir(tempDir.toFile());

		assertFailure(EnforcerRuleException.class, rule::execute, "missing hook script", "gone.sh");
	}

	@Test
	void doesNotTreatASymlinkedScriptPointingOutsideAsInsideTheHooksDirectory() {
		Path outside = tempDir.resolve("outside.sh");
		writeString(outside, "#!/bin/sh\n");
		setExecutable(outside.toFile(), true);
		Path insideLink = hooksDir().resolve("session-start.sh");
		assumeSymlink(insideLink, outside);
		HooksFormatRule rule = ruleFor();
		rule.setSettingsFile(settingsReferencing("$CLAUDE_PROJECT_DIR/.claude/hooks/session-start.sh"));
		rule.setProjectDir(tempDir.toFile());
		rule.setReportUnreferencedScripts(true);

		assertFailure(EnforcerRuleException.class, rule::execute, "not referenced");
	}

	@Test
	void passesWhenTheExtensionIsOnTheAllowedList() {
		writeScript("session-start.sh", "#!/bin/sh\n", true);
		HooksFormatRule rule = ruleFor();
		rule.setAllowedExtensions(List.of("sh", "py"));

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenAScriptHasNoExtensionAtAll() {
		writeScript("session-start", "#!/bin/sh\n", true);
		HooksFormatRule rule = ruleFor();
		rule.setAllowedExtensions(List.of("sh"));

		assertFailure(EnforcerRuleException.class, rule::execute, "disallowed extension");
	}

	@Test
	void failsWhenTheConfiguredSettingsFileDoesNotExist() {
		writeScript("session-start.sh", "#!/bin/sh\n", true);
		HooksFormatRule rule = ruleFor();
		rule.setSettingsFile(tempDir.resolve("absent.json").toFile());

		assertFailure(EnforcerRuleException.class, rule::execute, "settings.json does not exist");
	}

	/**
	 * A configured settings file that is not there is a build-setup mistake, so warn
	 * severity must not turn it — and with it the whole wiring cross-check — into a
	 * line in the log.
	 */
	@Test
	void failsForAnAbsentSettingsFileEvenAtWarnSeverity() {
		writeScript("session-start.sh", "#!/bin/sh\n", true);
		HooksFormatRule rule = ruleFor();
		rule.setSettingsFile(tempDir.resolve("absent.json").toFile());
		rule.setSeverity("warn");

		assertFailure(EnforcerRuleException.class, rule::execute, "settings.json does not exist");
	}

	/**
	 * A settings file that is there but is not text is reported, not thrown: the
	 * rules that own settings.json fail on it in their own right, so this one
	 * aborting the build as an internal error would only lose the script violations
	 * beside it.
	 */
	@Test
	void reportsASettingsFileThatCannotBeDecodedAsText() {
		writeScript("session-start.sh", "#!/bin/sh\n", true);
		Path settings = tempDir.resolve(".claude/settings.json");
		TestFiles.writeBytes(settings, new byte[] { (byte) 0xFF, (byte) 0xFE, 0x00, (byte) 0x80 });
		HooksFormatRule rule = ruleFor();
		rule.setSettingsFile(settings.toFile());

		assertFailure(EnforcerRuleException.class, rule::execute, "settings.json cannot be read as text");
	}

	@Test
	void failsWhenTheSettingsFileIsNotValidJson() {
		writeScript("session-start.sh", "#!/bin/sh\n", true);
		Path settings = tempDir.resolve(".claude/settings.json");
		writeString(settings, "{ not json");
		HooksFormatRule rule = ruleFor();
		rule.setSettingsFile(settings.toFile());

		assertFailure(EnforcerRuleException.class, rule::execute, "not valid JSON");
	}

	@Test
	void namesTheHooksDirectoryInItsDescription() {
		HooksFormatRule rule = ruleFor();

		assertTrue(rule.toString().contains("hooks"), rule.toString());
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

	@Test
	void reportsAFileThatCannotBeDecodedAsTextInsteadOfFailingTheBuildOutright() {
		writeBytes("logo.png", new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, (byte) 0xFF, (byte) 0xFE });

		EnforcerRuleException exception = assertFailure(EnforcerRuleException.class, ruleFor()::execute,
				"cannot be read as a text script", "logo.png");
		assertFalse(exception.getMessage().contains("is empty"), exception.getMessage());
	}

	@Test
	void keepsCheckingTheOtherScriptsAfterAFileThatIsNotText() {
		writeBytes("logo.png", new byte[] { (byte) 0xFF, (byte) 0xFE, (byte) 0x00 });
		writeScript("session-start.sh", "echo hi\n", true);

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor()::execute);
		// Both are reported: the undecodable file no longer aborts the run before
		// the script after it is looked at.
		assertTrue(exception.getMessage().contains("logo.png"), exception.getMessage());
		assertTrue(exception.getMessage().contains("shebang"), exception.getMessage());
		assertTrue(exception.getMessage().contains("session-start.sh"), exception.getMessage());
	}

	@Test
	void reportsScriptsInASortedOrderRatherThanTheFilesystemOrder() {
		for (String name : List.of("zeta.sh", "alpha.sh", "mid.sh", "beta.sh")) {
			writeScript(name, "echo hi\n", true);
		}

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor()::execute);
		assertEquals(List.of("alpha.sh", "beta.sh", "mid.sh", "zeta.sh"), reportedScripts(exception.getMessage()));
	}

	/** The script names named by the shebang violations, in the order the rule reported them. */
	private List<String> reportedScripts(String message) {
		return message.lines()
				.filter(line -> line.contains("shebang"))
				.map(line -> line.substring(line.lastIndexOf(File.separatorChar) + 1))
				.toList();
	}

	/** A hook written across two lines runs both scripts, so neither is unreferenced. */
	@Test
	void countsEveryLineOfAMultiLineCommandAsAReference() {
		writeScript("a.sh", "#!/bin/sh\necho a\n", true);
		writeScript("b.sh", "#!/bin/sh\necho b\n", true);
		HooksFormatRule rule = ruleFor();
		rule.setSettingsFile(settingsReferencing(".claude/hooks/a.sh\\n.claude/hooks/b.sh"));
		rule.setReportUnreferencedScripts(true);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void countsAScriptPrefixedByAVariableAssignmentAsAReference() {
		writeScript("a.sh", "#!/bin/sh\necho a\n", true);
		HooksFormatRule rule = ruleFor();
		rule.setSettingsFile(settingsReferencing("LOG_LEVEL=debug .claude/hooks/a.sh"));
		rule.setReportUnreferencedScripts(true);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void countsAScriptRunThroughExecAsAReference() {
		writeScript("a.sh", "#!/bin/sh\necho a\n", true);
		HooksFormatRule rule = ruleFor();
		rule.setSettingsFile(settingsReferencing("exec .claude/hooks/a.sh"));
		rule.setReportUnreferencedScripts(true);

		assertDoesNotThrow(rule::execute);
	}

	/** The other half of reading {@code exec}: the script behind it is resolved, so a rename is caught. */
	@Test
	void failsWhenExecRunsAMissingHookScript() {
		HooksFormatRule rule = ruleFor();
		rule.setSettingsFile(settingsReferencing("exec $CLAUDE_PROJECT_DIR/.claude/hooks/gone.sh"));

		assertFailure(EnforcerRuleException.class, rule::execute, "references a missing hook script", "gone.sh");
	}

	@Test
	void countsAScriptRunInASubshellAsAReference() {
		writeScript("a.sh", "#!/bin/sh\necho a\n", true);
		HooksFormatRule rule = ruleFor();
		rule.setSettingsFile(settingsReferencing("(.claude/hooks/a.sh)"));
		rule.setReportUnreferencedScripts(true);

		assertDoesNotThrow(rule::execute);
	}

	/**
	 * A hook that guards its script with a condition still runs it. Reading
	 * {@code then} as the program left the script the hook really does run reported
	 * as referenced by nothing.
	 */
	@Test
	void treatsAScriptInsideAConditionalAsReferenced() {
		writeScript("session-start.sh", "#!/bin/sh\n", true);
		HooksFormatRule rule = ruleFor();
		rule.setSettingsFile(settingsReferencing("if [ -n \\\"$CI\\\" ]; then .claude/hooks/session-start.sh; fi"));
		rule.setProjectDir(tempDir.toFile());
		rule.setReportUnreferencedScripts(true);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenAConditionalHookReferencesAMissingScript() {
		HooksFormatRule rule = ruleFor();
		rule.setSettingsFile(settingsReferencing("if true; then .claude/hooks/gone.sh; fi"));
		rule.setProjectDir(tempDir.toFile());

		assertFailure(EnforcerRuleException.class, rule::execute, "references a missing hook script", "gone.sh");
	}

	private HooksFormatRule ruleFor() {
		HooksFormatRule rule = new HooksFormatRule();
		rule.setHooksDir(hooksDir().toFile());
		return rule;
	}

	private void writeBytes(String name, byte[] content) {
		TestFiles.writeBytes(hooksDir().resolve(name), content);
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
		createDirectory(dir);
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
}
