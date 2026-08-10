package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.adopt.AdoptionException;

class GradleGuardInstallerTest {

	private final GradleGuardInstaller installer = new GradleGuardInstaller();

	/**
	 * Gradle's configuration cache — on by default from Gradle 9 — rejects a task
	 * whose execution-time body reaches back into the Project, failing the guard
	 * task in the Groovy DSL and the whole build in the Kotlin one. Resolving the
	 * file while the task is configured and only reading it in doLast keeps the task
	 * body holding a plain File.
	 */
	@Test
	void theGroovyGuardResolvesTheFileBeforeDoLast(@TempDir Path dir) throws IOException {
		Path buildFile = write(dir, "build.gradle", "plugins { id 'java' }\n");
		installer.install(buildFile);
		String script = Files.readString(buildFile);
		assertTrue(script.indexOf("project.file('CLAUDE.md')") < script.indexOf("doLast"),
				"the file must be resolved at configuration time, before doLast: " + script);
		assertFalse(script.contains("$projectDir"),
				"a project reference inside the task body breaks the configuration cache: " + script);
	}

	@Test
	void theKotlinGuardResolvesTheFileBeforeDoLast(@TempDir Path dir) throws IOException {
		Path buildFile = write(dir, "build.gradle.kts", "plugins { java }\n");
		installer.install(buildFile);
		String script = Files.readString(buildFile);
		assertTrue(script.indexOf("project.file(\"CLAUDE.md\")") < script.indexOf("doLast"),
				"the file must be resolved at configuration time, before doLast: " + script);
		assertFalse(script.contains("$projectDir"),
				"a project reference inside the task body breaks the configuration cache: " + script);
	}

	private Path write(Path directory, String fileName, String content) throws IOException {
		Path buildFile = directory.resolve(fileName);
		Files.writeString(buildFile, content);
		return buildFile;
	}

	@Test
	void appendsGroovyGuardToBuildGradle(@TempDir Path directory) throws IOException {
		Path buildFile = write(directory, "build.gradle", "plugins { id 'java' }\n");
		assertTrue(installer.install(buildFile));
		String content = Files.readString(buildFile);
		assertTrue(content.contains("tasks.register('enforceClaudeMd')"));
		assertTrue(content.contains("throw new GradleException('CLAUDE.md is missing or empty')"));
		assertTrue(content.startsWith("plugins { id 'java' }"));
	}

	@Test
	void appendsKotlinGuardToBuildGradleKts(@TempDir Path directory) throws IOException {
		Path buildFile = write(directory, "build.gradle.kts", "plugins { java }\n");
		assertTrue(installer.install(buildFile));
		String content = Files.readString(buildFile);
		assertTrue(content.contains("tasks.register(\"enforceClaudeMd\")"));
		assertTrue(content.contains("val claudeMd = project.file(\"CLAUDE.md\")"));
	}

	@Test
	void matchesCarriageReturnLineEndingsWhenAppendingTheGuard(@TempDir Path directory) throws IOException {
		Path buildFile = write(directory, "build.gradle", "plugins { id 'java' }\r\n");
		assertTrue(installer.install(buildFile));
		String content = Files.readString(buildFile);
		assertTrue(content.contains("tasks.register('enforceClaudeMd')"), "the guard must be appended");
		assertFalse(content.replace("\r\n", "").contains("\n"),
				"a CRLF build script must stay CRLF; the appended block must not introduce bare LF endings");
	}

	@Test
	void unreadableBuildScriptAbortsAdoption(@TempDir Path directory) throws IOException {
		Path buildFile = directory.resolve("build.gradle");
		Files.createDirectory(buildFile);
		assertThrows(AdoptionException.class, () -> installer.install(buildFile));
	}

	@Test
	void leavesAnAlreadyGuardedBuildUnchanged(@TempDir Path directory) throws IOException {
		Path buildFile = write(directory, "build.gradle", "plugins { id 'java' }\n");
		installer.install(buildFile);
		String afterFirstInstall = Files.readString(buildFile);
		assertFalse(installer.install(buildFile));
		assertEquals(afterFirstInstall, Files.readString(buildFile));
	}

	/**
	 * Matching the bare task name anywhere in the script let a comment stand in for
	 * the task itself, so the guard was never installed and the adoption then failed
	 * running a verification task that did not exist.
	 */
	@Test
	void installsTheGuardWhenTheScriptOnlyMentionsTheTaskInAComment(@TempDir Path directory) throws IOException {
		Path buildFile = write(directory, "build.gradle",
				"// TODO: one day add an enforceClaudeMd task\nplugins { id 'java' }\n");
		assertTrue(installer.install(buildFile));
		assertTrue(Files.readString(buildFile).contains("tasks.register('enforceClaudeMd')"));
	}

	@Test
	void installsTheGuardWhenAnEarlierDeclarationWasCommentedOut(@TempDir Path directory) throws IOException {
		Path buildFile = write(directory, "build.gradle.kts",
				"// tasks.register(\"enforceClaudeMd\") { }\nplugins { java }\n");
		assertTrue(installer.install(buildFile));
		assertTrue(Files.readString(buildFile).contains("tasks.register(\"enforceClaudeMd\")"));
	}

	/**
	 * The block comment is the form a whole declaration is commented out with, since it
	 * takes the registration's braces and its body in one go. Reading only the line form
	 * left the task name plainly in the text, so the script was taken to declare the
	 * task already and the guard was never appended — and
	 * {@link GradleBuildSystem#verifyCommand(Path)} then ran a task the build has not.
	 */
	@Test
	void installsTheGuardWhenADeclarationIsInsideABlockComment(@TempDir Path directory) throws IOException {
		Path buildFile = write(directory, "build.gradle",
				"/*\ntasks.register('enforceClaudeMd') {\n    doLast { }\n}\n*/\nplugins { id 'java' }\n");
		assertTrue(installer.install(buildFile));
		assertTrue(Files.readString(buildFile).contains("tasks.register('enforceClaudeMd')"));
	}

	/**
	 * An unterminated block comment is a syntax error whichever way it is read, so the
	 * script is left as it is rather than having everything below the {@code /*} treated
	 * as commented out — which would append a second registration to a script that
	 * plainly declares one.
	 */
	@Test
	void stillSeesADeclarationBelowAnUnterminatedBlockComment(@TempDir Path directory) throws IOException {
		Path buildFile = directory.resolve("build.gradle");
		String own = "/* work in progress\ntasks.register('enforceClaudeMd') {\n    doLast { }\n}\n";
		Files.writeString(buildFile, own);
		assertFalse(installer.install(buildFile));
		assertEquals(own, Files.readString(buildFile));
	}

	/**
	 * A project that registers the task itself keeps its own version: appending a
	 * second registration of the same name would fail the Gradle build outright.
	 */
	@Test
	void leavesAScriptThatRegistersTheTaskItselfUnchanged(@TempDir Path directory) throws IOException {
		Path buildFile = directory.resolve("build.gradle");
		String own = "tasks.register('enforceClaudeMd') {\n    doLast { }\n}\n";
		Files.writeString(buildFile, own);
		assertFalse(installer.install(buildFile));
		assertEquals(own, Files.readString(buildFile));
	}

	@Test
	void recognisesARegistrationSpreadOverSeveralLines(@TempDir Path directory) throws IOException {
		Path buildFile = directory.resolve("build.gradle");
		String own = "tasks.register(\n    'enforceClaudeMd'\n) {\n    doLast { }\n}\n";
		Files.writeString(buildFile, own);
		assertFalse(installer.install(buildFile));
		assertEquals(own, Files.readString(buildFile));
	}
}
