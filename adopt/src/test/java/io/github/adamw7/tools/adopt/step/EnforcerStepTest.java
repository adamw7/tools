package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.command.RecordingCommandRunner;

class EnforcerStepTest {

	private static final String POM = """
			<project xmlns="http://maven.apache.org/POM/4.0.0">
			  <modelVersion>4.0.0</modelVersion>
			  <groupId>com.example</groupId>
			  <artifactId>demo</artifactId>
			  <version>1.0.0</version>
			</project>
			""";

	private AdoptionContext context(Path workspace) throws IOException {
		AdoptionContext context = new AdoptionContext("https://github.com/adamw7/demo.git", workspace);
		Files.createDirectories(context.repositoryDirectory());
		return context;
	}

	@Test
	void wiresEnforcerIntoCheckoutPom(@TempDir Path workspace) throws IOException {
		AdoptionContext context = context(workspace);
		Files.writeString(context.repositoryDirectory().resolve("pom.xml"), POM);
		new EnforcerStep().execute(context, new RecordingCommandRunner());
		assertTrue(Files.readString(context.repositoryDirectory().resolve("pom.xml"))
				.contains("maven-enforcer-plugin"));
	}

	@Test
	void skipsWhenNotAMavenProject(@TempDir Path workspace) throws IOException {
		AdoptionContext context = context(workspace);
		assertDoesNotThrow(() -> new EnforcerStep().execute(context, new RecordingCommandRunner()));
		assertFalse(Files.exists(context.repositoryDirectory().resolve("pom.xml")));
	}

	@Test
	void leavesAnAlreadyWiredPomByteForByteUnchanged(@TempDir Path workspace) throws IOException {
		AdoptionContext context = context(workspace);
		Path pom = context.repositoryDirectory().resolve("pom.xml");
		Files.writeString(pom, POM);
		EnforcerStep step = new EnforcerStep();
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
