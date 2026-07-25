package io.github.adamw7.tools.adopt.step;

import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.github.adamw7.tools.adopt.AdoptionFiles;

/**
 * Appends a {@code CLAUDE.md} guard task to a Gradle build script so the adopted
 * repository fails its build when the generated {@code CLAUDE.md} is missing or
 * empty. Gradle has no {@code claude-code-enforcer} equivalent, so the guard is a
 * dependency-free presence-and-non-empty check rather than the full format rule
 * the Maven path wires in.
 *
 * <p>The syntax is chosen from the script's extension, since the Kotlin
 * ({@code build.gradle.kts}) and Groovy ({@code build.gradle}) DSLs differ. The
 * block is appended rather than parsed in because Gradle scripts are code, not
 * data. The install is idempotent: a script that already declares the
 * {@value #GUARD_TASK} task is left untouched.
 *
 * <p>The file to check is resolved while the task is being <em>configured</em> and
 * only read inside {@code doLast}, so the task body captures a plain
 * {@link java.io.File} rather than reaching back into the {@code Project} at
 * execution time. Gradle's configuration cache rejects a task that holds a script
 * or {@code Project} reference, which would abort the adoption at
 * {@link GradleBuildSystem#verifyCommand()} and break {@code check} for every
 * contributor afterwards.
 */
public class GradleGuardInstaller {

	static final String GUARD_TASK = "enforceClaudeMd";

	private static final String BUILD_FILE_DESCRIPTION = "Gradle build file";
	private static final String KOTLIN_SUFFIX = ".kts";
	private static final String LINE_COMMENT = "//";

	/**
	 * The shapes that actually register the task in either DSL:
	 * {@code tasks.register('enforceClaudeMd')}, its {@code create} and
	 * double-quoted variants, and the legacy {@code task enforceClaudeMd}.
	 */
	private static final Pattern DECLARATION = Pattern.compile(
			"(?:register|create)\\s*\\(\\s*[\"']" + GUARD_TASK + "[\"']|\\btask\\s+" + GUARD_TASK + "\\b");

	private static final String GROOVY_BLOCK = """

			// Added by claude-code-adopt: fail the build when CLAUDE.md is missing or empty.
			tasks.register('%s') {
			    def claudeMd = project.file('CLAUDE.md')
			    doLast {
			        if (!claudeMd.isFile() || claudeMd.text.trim().isEmpty()) {
			            throw new GradleException('CLAUDE.md is missing or empty')
			        }
			    }
			}
			tasks.matching { it.name == 'check' }.configureEach { it.dependsOn('%s') }
			""".formatted(GUARD_TASK, GUARD_TASK);

	private static final String KOTLIN_BLOCK = """

			// Added by claude-code-adopt: fail the build when CLAUDE.md is missing or empty.
			tasks.register("%s") {
			    val claudeMd = project.file("CLAUDE.md")
			    doLast {
			        if (!claudeMd.isFile || claudeMd.readText().trim().isEmpty()) {
			            throw GradleException("CLAUDE.md is missing or empty")
			        }
			    }
			}
			tasks.matching { it.name == "check" }.configureEach { dependsOn("%s") }
			""".formatted(GUARD_TASK, GUARD_TASK);

	/**
	 * @return {@code true} when the guard was appended, {@code false} when the
	 *         script already declared it and was left unchanged.
	 */
	public boolean install(Path buildFile) {
		String existing = AdoptionFiles.read(buildFile, BUILD_FILE_DESCRIPTION);
		if (declaresGuard(existing)) {
			return false;
		}
		append(buildFile, existing, blockFor(buildFile));
		return true;
	}

	/**
	 * The task name only counts when it appears in a registration outside a comment,
	 * so a script that merely mentions {@value #GUARD_TASK} — in a {@code // TODO},
	 * or in a declaration someone commented out — is still given the task rather
	 * than left without the one {@link GradleBuildSystem#verifyCommand()} runs.
	 */
	private boolean declaresGuard(String script) {
		return DECLARATION.matcher(withoutCommentLines(script)).find();
	}

	/**
	 * The lines are rejoined rather than matched one at a time so a registration
	 * spread over several lines is still recognised.
	 */
	private String withoutCommentLines(String script) {
		return script.lines().filter(line -> !line.strip().startsWith(LINE_COMMENT)).collect(Collectors.joining("\n"));
	}

	private String blockFor(Path buildFile) {
		return buildFile.getFileName().toString().endsWith(KOTLIN_SUFFIX) ? KOTLIN_BLOCK : GROOVY_BLOCK;
	}

	/** The block's LF terminators are rewritten to the script's own, so a CRLF file stays CRLF throughout. */
	private void append(Path buildFile, String existing, String block) {
		AdoptionFiles.write(buildFile, existing + LineTerminators.matching(block, existing), BUILD_FILE_DESCRIPTION);
	}
}
