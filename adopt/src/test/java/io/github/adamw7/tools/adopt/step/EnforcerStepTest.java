package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionContexts;
import io.github.adamw7.tools.adopt.command.RecordingCommandRunner;

class EnforcerStepTest {

	private static final String RULE_VERSION = "1.0.0";

	private static final String POM = """
			<project xmlns="http://maven.apache.org/POM/4.0.0">
			  <modelVersion>4.0.0</modelVersion>
			  <groupId>com.example</groupId>
			  <artifactId>demo</artifactId>
			  <version>1.0.0</version>
			</project>
			""";

	/**
	 * The Maven path wires a rule dependency whose version comes from the running
	 * build, and {@link PomEnforcerInstaller} refuses a snapshot. Pinning a released
	 * version here keeps these tests about the wiring rather than about whichever
	 * version the module happens to be built at.
	 */
	private EnforcerStep mavenStep() {
		return new EnforcerStep(List.of(new MavenBuildSystem(new PomEnforcerInstaller(RULE_VERSION))));
	}

	@Test
	void wiresEnforcerIntoCheckoutPom(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		AdoptionContexts.write(context, "pom.xml", POM);
		mavenStep().execute(context, new RecordingCommandRunner());
		assertTrue(Files.readString(context.repositoryDirectory().resolve("pom.xml"))
				.contains("maven-enforcer-plugin"));
	}

	@Test
	void wiresGuardIntoAGroovyGradleBuild(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		Path buildFile = context.repositoryDirectory().resolve("build.gradle");
		Files.writeString(buildFile, "plugins { id 'java' }\n");
		new EnforcerStep().execute(context, new RecordingCommandRunner());
		assertTrue(Files.readString(buildFile).contains("enforceClaudeMd"));
	}

	@Test
	void wiresGuardIntoAKotlinGradleBuild(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		Path buildFile = context.repositoryDirectory().resolve("build.gradle.kts");
		Files.writeString(buildFile, "plugins { java }\n");
		new EnforcerStep().execute(context, new RecordingCommandRunner());
		assertTrue(Files.readString(buildFile).contains("enforceClaudeMd"));
	}

	@Test
	void wiresTheFallbackGuardWhenNoBuildFileIsPresent(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		new EnforcerStep().execute(context, new RecordingCommandRunner());
		assertTrue(Files.exists(context.repositoryDirectory().resolve(WorkflowGuardInstaller.WORKFLOW_FILE)));
		assertFalse(Files.exists(context.repositoryDirectory().resolve("pom.xml")));
	}

	@Test
	void skipsWhenNoConfiguredBuildSystemMatches(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		EnforcerStep step = new EnforcerStep(List.of(new MavenBuildSystem(), new GradleBuildSystem()));
		assertDoesNotThrow(() -> step.execute(context, new RecordingCommandRunner()));
		assertFalse(Files.exists(context.repositoryDirectory().resolve(WorkflowGuardInstaller.WORKFLOW_FILE)));
	}

	@Test
	void leavesAnAlreadyWiredPomByteForByteUnchanged(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		Path pom = context.repositoryDirectory().resolve("pom.xml");
		Files.writeString(pom, POM);
		EnforcerStep step = mavenStep();
		step.execute(context, new RecordingCommandRunner());
		String afterFirstWiring = Files.readString(pom);
		step.execute(context, new RecordingCommandRunner());
		assertEquals(afterFirstWiring, Files.readString(pom));
	}

	@Test
	void isNamedEnforcer() {
		assertEquals("enforcer", new EnforcerStep().name());
	}
}
