package io.github.adamw7.tools.adopt.step;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.adamw7.tools.adopt.AdoptionException;

/**
 * Appends a {@code CLAUDE.md} guard task to a Gradle build script so the adopted
 * repository fails its build when the generated {@code CLAUDE.md} is missing or
 * empty. Unlike the Maven path — which wires the full {@code claude-code-enforcer}
 * format rule — Gradle has no such rule available, so the guard is a
 * dependency-free presence-and-non-empty check written directly into the build
 * script.
 *
 * <p>The correct syntax is chosen from the script's extension: the Kotlin DSL
 * ({@code build.gradle.kts}) and the Groovy DSL ({@code build.gradle}) differ.
 * The block is appended rather than parsed in because Gradle scripts are code,
 * not data, and a self-contained task needs no structural edit. The install is
 * idempotent: a script that already declares the {@value #GUARD_TASK} task is
 * left untouched.
 */
public class GradleGuardInstaller {

	static final String GUARD_TASK = "enforceClaudeMd";

	private static final String KOTLIN_SUFFIX = ".kts";

	private static final String GROOVY_BLOCK = """

			// Added by claude-code-adopt: fail the build when CLAUDE.md is missing or empty.
			tasks.register('%s') {
			    doLast {
			        def claudeMd = file("$projectDir/CLAUDE.md")
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
			    doLast {
			        val claudeMd = file("$projectDir/CLAUDE.md")
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
		String existing = read(buildFile);
		if (existing.contains(GUARD_TASK)) {
			return false;
		}
		append(buildFile, existing, blockFor(buildFile));
		return true;
	}

	private String blockFor(Path buildFile) {
		return buildFile.getFileName().toString().endsWith(KOTLIN_SUFFIX) ? KOTLIN_BLOCK : GROOVY_BLOCK;
	}

	private String read(Path buildFile) {
		try {
			return Files.readString(buildFile);
		} catch (IOException e) {
			throw new AdoptionException("Could not read Gradle build file: " + buildFile, e);
		}
	}

	private void append(Path buildFile, String existing, String block) {
		try {
			Files.writeString(buildFile, existing + block);
		} catch (IOException e) {
			throw new AdoptionException("Could not write Gradle build file: " + buildFile, e);
		}
	}
}
