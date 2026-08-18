package io.github.adamw7.tools.adopt.step;

import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.github.adamw7.tools.adopt.AdoptionFiles;

/**
 * Appends a {@code CLAUDE.md} guard task to a Gradle build script, so the adopted
 * repository fails its build when the generated {@code CLAUDE.md} is missing or
 * empty. Gradle has no {@code claude-code-enforcer} equivalent, so the guard is a
 * dependency-free presence-and-non-empty check rather than the full format rule
 * the Maven path wires in.
 *
 * <p>The syntax is chosen from the script's extension, since the Kotlin
 * ({@code build.gradle.kts}) and Groovy ({@code build.gradle}) DSLs differ, and
 * the block is appended rather than parsed in because Gradle scripts are code, not
 * data. A script that already declares the {@value #GUARD_TASK} task is left
 * untouched.
 *
 * <p>The file to check is resolved while the task is being <em>configured</em> and
 * only read inside {@code doLast}, so the task body captures a plain
 * {@link java.io.File} rather than reaching back into the {@code Project} at
 * execution time: Gradle's configuration cache rejects a task holding a script or
 * {@code Project} reference, which would break {@code check} for every contributor
 * afterwards.
 *
 * <p>Registering the task is not enough on its own: a task nothing depends on is a
 * guard the project's ordinary build never runs. It is hung from
 * {@value #LIFECYCLE_TASK} through a live {@code matching}/{@code configureEach}
 * view, so a plugin applied further down the script still wires it in — and a
 * project that ends up with no {@value #LIFECYCLE_TASK} at all is given one. An
 * Android or aggregator root script routinely declares a {@code clean} task and
 * nothing else, and the guard was left registered but unreachable from any build a
 * contributor or CI would run.
 *
 * <p>That fallback registration is deferred to {@code afterEvaluate} rather than
 * made inline, because the name has to be free: claiming {@value #LIFECYCLE_TASK}
 * while the script is still being read would collide with a plugin applied below
 * this block. Applying Gradle's {@code base} plugin to obtain the task instead was
 * rejected for the same reason — it also registers {@code clean}.
 */
public class GradleGuardInstaller {

	static final String GUARD_TASK = "enforceClaudeMd";

	private static final String BUILD_FILE_DESCRIPTION = "Gradle build file";
	private static final String KOTLIN_SUFFIX = ".kts";
	private static final String LINE_COMMENT = "//";
	private static final char SINGLE_QUOTE = '\'';
	private static final char DOUBLE_QUOTE = '"';
	private static final char ESCAPE = '\\';

	/**
	 * The shapes that actually register the task in either DSL:
	 * {@code tasks.register('enforceClaudeMd')}, its {@code create} and
	 * double-quoted variants, and the legacy {@code task enforceClaudeMd}.
	 */
	private static final Pattern DECLARATION = Pattern.compile(
			"(?:register|create)\\s*\\(\\s*[\"']" + GUARD_TASK + "[\"']|\\btask\\s+" + GUARD_TASK + "\\b");

	/**
	 * A terminated {@code /*} … {@code *}{@code /} comment, however many lines it spans.
	 * The shortest match is taken so two comments on one line stay two, and an
	 * unterminated one is left in the script: it is a syntax error either way, and
	 * swallowing the rest of the file would read every real declaration below it as
	 * commented out.
	 */
	private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

	/** The lifecycle task the guard is hung from, and the one created when the project has none. */
	static final String LIFECYCLE_TASK = "check";

	private static final String GROOVY_BLOCK = """

			// Added by claude-code-adopt: fail the build when CLAUDE.md is missing or empty.
			tasks.register('%1$s') {
			    def claudeMd = project.file('CLAUDE.md')
			    doLast {
			        if (!claudeMd.isFile() || claudeMd.text.trim().isEmpty()) {
			            throw new GradleException('CLAUDE.md is missing or empty')
			        }
			    }
			}
			tasks.matching { it.name == '%2$s' }.configureEach { it.dependsOn('%1$s') }
			project.afterEvaluate {
			    if (!tasks.names.contains('%2$s')) {
			        tasks.register('%2$s') { it.dependsOn('%1$s') }
			    }
			}
			""".formatted(GUARD_TASK, LIFECYCLE_TASK);

	private static final String KOTLIN_BLOCK = """

			// Added by claude-code-adopt: fail the build when CLAUDE.md is missing or empty.
			tasks.register("%1$s") {
			    val claudeMd = project.file("CLAUDE.md")
			    doLast {
			        if (!claudeMd.isFile || claudeMd.readText().trim().isEmpty()) {
			            throw GradleException("CLAUDE.md is missing or empty")
			        }
			    }
			}
			tasks.matching { it.name == "%2$s" }.configureEach { dependsOn("%1$s") }
			project.afterEvaluate {
			    if (!tasks.names.contains("%2$s")) {
			        tasks.register("%2$s") { dependsOn("%1$s") }
			    }
			}
			""".formatted(GUARD_TASK, LIFECYCLE_TASK);

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
	 * than left without the one {@link GradleBuildSystem#verifyCommand(Path)} runs.
	 */
	private boolean declaresGuard(String script) {
		return DECLARATION.matcher(withoutComments(script)).find();
	}

	/**
	 * Both ways either DSL lets a registration be commented out. The block form is
	 * removed first and by span rather than by line, because it is the form a whole
	 * declaration is commented out with, and leaving it made the script read as
	 * already declaring the task — so the guard was never appended and
	 * {@link VerifyStep} ran a task the build does not have. The line form is cut
	 * afterwards, and the lines are rejoined rather than matched one at a time so a
	 * registration spread over several lines is still recognised.
	 *
	 * <p>A line comment is cut from its delimiter rather than taking the whole line,
	 * because it does not have to start one: a registration written at the end of a
	 * line of real code — {@code plugins { id 'java' } // tasks.register('enforceClaudeMd')}
	 * — was left in the text, so the script read as already declaring the task and got
	 * no guard. The line is kept in place, blank where it was wholly a comment, rather
	 * than dropped: dropping it would join the lines either side of it, which is how
	 * two halves of no declaration at all come to read as one.
	 */
	private String withoutComments(String script) {
		String withoutBlocks = BLOCK_COMMENT.matcher(script).replaceAll("");
		return withoutBlocks.lines()
				.map(GradleGuardInstaller::withoutLineComment)
				.collect(Collectors.joining("\n"));
	}

	/**
	 * The line up to its first {@value #LINE_COMMENT} outside a string literal. The
	 * literals are honoured because a Gradle script writes {@code url 'https://…'}
	 * more readily than it writes anything else, and cutting there would drop the rest
	 * of a line that may carry the declaration being looked for.
	 */
	private static String withoutLineComment(String line) {
		char quote = 0;
		int index = 0;
		while (index < line.length()) {
			if (quote == 0 && line.startsWith(LINE_COMMENT, index)) {
				return line.substring(0, index);
			}
			char character = line.charAt(index);
			quote = quoteAfter(quote, character);
			index += quote != 0 && character == ESCAPE ? 2 : 1;
		}
		return line;
	}

	/**
	 * The string literal still open after this character: the one it closes, the one
	 * it opens, or none. A backslash inside a literal escapes the character after it,
	 * a closing quote included, which is why {@link #withoutLineComment} steps over
	 * two characters there.
	 */
	private static char quoteAfter(char quote, char character) {
		if (quote != 0) {
			return quote == character ? 0 : quote;
		}
		return character == SINGLE_QUOTE || character == DOUBLE_QUOTE ? character : 0;
	}

	private String blockFor(Path buildFile) {
		return buildFile.getFileName().toString().endsWith(KOTLIN_SUFFIX) ? KOTLIN_BLOCK : GROOVY_BLOCK;
	}

	/** The block's LF terminators are rewritten to the script's own, so a CRLF file stays CRLF throughout. */
	private void append(Path buildFile, String existing, String block) {
		AdoptionFiles.write(buildFile, existing + LineTerminators.matching(block, existing), BUILD_FILE_DESCRIPTION);
	}
}
