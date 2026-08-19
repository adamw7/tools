package io.github.adamw7.tools.adopt.step;

import static io.github.adamw7.tools.test.ExpectedFailures.assertFailure;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionContexts;
import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.command.RecordingCommandRunner;

/**
 * The question a verification asks that the guard's own command cannot answer: is
 * this repository actually adopted? A build with no guard wired in passes the
 * verification command precisely because nothing ran.
 */
class AdoptionCheckStepTest {

	@Test
	void passesForARepositoryCarryingBothTheDocumentAndAGuard(@TempDir Path workspace) throws IOException {
		AdoptionContext context = adoptedRepository(workspace);

		assertDoesNotThrow(() -> step().execute(context, new RecordingCommandRunner()));
	}

	/** The drift a fleet-wide check exists to find: a later commit deleted the document. */
	@Test
	void failsWhenTheDocumentIsGone(@TempDir Path workspace) throws IOException {
		AdoptionContext context = adoptedRepository(workspace);
		Files.delete(context.repositoryDirectory().resolve("CLAUDE.md"));

		assertFailure(AdoptionException.class, () -> step().execute(context, new RecordingCommandRunner()),
				"no CLAUDE.md");
	}

	/** The other half of the drift: the document is there and nothing checks it. */
	@Test
	void failsWhenTheBuildWiresInNoGuard(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		Files.writeString(context.repositoryDirectory().resolve("pom.xml"), "<project/>");
		Files.writeString(context.repositoryDirectory().resolve("CLAUDE.md"), "# CLAUDE.md");

		assertFailure(AdoptionException.class, () -> step().execute(context, new RecordingCommandRunner()),
				"wires in no CLAUDE.md guard");
	}

	/** Both halves at once, so an operator knows whether to restore a document, a guard, or both. */
	@Test
	void reportsBothWhenBothAreMissing(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		Files.writeString(context.repositoryDirectory().resolve("pom.xml"), "<project/>");

		AdoptionException thrown = assertThrowsAdoption(context);

		assertTrue(thrown.getMessage().contains("no CLAUDE.md"), thrown.getMessage());
		assertTrue(thrown.getMessage().contains("wires in no CLAUDE.md guard"), thrown.getMessage());
	}

	@Test
	void namesTheRepositoryItCheckedWithoutItsCredentials(@TempDir Path workspace) throws IOException {
		AdoptionContext context = new AdoptionContext(
				"https://x-access-token:secret@github.com/owner/repo.git", workspace);
		Files.createDirectories(context.repositoryDirectory());
		Files.writeString(context.repositoryDirectory().resolve("pom.xml"), "<project/>");

		AdoptionException thrown = assertThrowsAdoption(context);

		assertTrue(thrown.getMessage().contains("owner/repo"), thrown.getMessage());
		assertFalse(thrown.getMessage().contains("secret"), thrown.getMessage());
	}

	private AdoptionException assertThrowsAdoption(AdoptionContext context) {
		return assertThrows(AdoptionException.class,
				() -> step().execute(context, new RecordingCommandRunner()));
	}

	private AdoptionCheckStep step() {
		return new AdoptionCheckStep(List.of(new MavenBuildSystem(), new FallbackBuildSystem()));
	}

	/** A Maven checkout with the document and a POM whose build runs the composite guard. */
	private AdoptionContext adoptedRepository(Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		Path checkout = context.repositoryDirectory();
		Files.writeString(checkout.resolve("CLAUDE.md"), "# CLAUDE.md");
		Files.writeString(checkout.resolve("pom.xml"), """
				<project xmlns="http://maven.apache.org/POM/4.0.0">
				  <artifactId>demo</artifactId>
				  <build>
				    <plugins>
				      <plugin>
				        <artifactId>maven-enforcer-plugin</artifactId>
				        <executions>
				          <execution>
				            <configuration>
				              <rules>
				                <claudeCodeProject>
				                  <projectDir>${project.basedir}</projectDir>
				                </claudeCodeProject>
				              </rules>
				            </configuration>
				          </execution>
				        </executions>
				      </plugin>
				    </plugins>
				  </build>
				</project>
				""");
		return context;
	}
}
