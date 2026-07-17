package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PomEnforcerInstallerTest {

	private static final String POM_WITH_BUILD = """
			<project xmlns="http://maven.apache.org/POM/4.0.0">
			  <modelVersion>4.0.0</modelVersion>
			  <groupId>com.example</groupId>
			  <artifactId>demo</artifactId>
			  <version>1.0.0</version>
			  <build>
			    <plugins>
			      <plugin>
			        <groupId>org.apache.maven.plugins</groupId>
			        <artifactId>maven-compiler-plugin</artifactId>
			      </plugin>
			    </plugins>
			  </build>
			</project>
			""";

	private static final String POM_WITHOUT_BUILD = """
			<project xmlns="http://maven.apache.org/POM/4.0.0">
			  <modelVersion>4.0.0</modelVersion>
			  <groupId>com.example</groupId>
			  <artifactId>demo</artifactId>
			  <version>1.0.0</version>
			</project>
			""";

	private static final String POM_WITH_ENFORCER = """
			<project xmlns="http://maven.apache.org/POM/4.0.0">
			  <modelVersion>4.0.0</modelVersion>
			  <groupId>com.example</groupId>
			  <artifactId>demo</artifactId>
			  <version>1.0.0</version>
			  <build>
			    <plugins>
			      <plugin>
			        <groupId>org.apache.maven.plugins</groupId>
			        <artifactId>maven-enforcer-plugin</artifactId>
			        <executions>
			          <execution>
			            <id>enforce-maven</id>
			            <goals>
			              <goal>enforce</goal>
			            </goals>
			            <configuration>
			              <rules>
			                <requireMavenVersion>
			                  <version>3.9.0</version>
			                </requireMavenVersion>
			              </rules>
			            </configuration>
			          </execution>
			        </executions>
			      </plugin>
			    </plugins>
			  </build>
			</project>
			""";

	private final PomEnforcerInstaller installer = new PomEnforcerInstaller("9.9.9");

	private Path write(Path dir, String content) throws IOException {
		Path pom = dir.resolve("pom.xml");
		Files.writeString(pom, content);
		return pom;
	}

	@Test
	void addsEnforcerPluginToExistingBuild(@TempDir Path dir) throws IOException {
		Path pom = write(dir, POM_WITH_BUILD);
		assertTrue(installer.install(pom));
		String result = Files.readString(pom);
		assertTrue(result.contains("maven-enforcer-plugin"));
		assertTrue(result.contains("tools.claude-code-enforcer"));
		assertTrue(result.contains("9.9.9"));
		assertTrue(result.contains("claudeMdFormat"));
	}

	@Test
	void pinsEnforcerPluginVersionWhenCreatingIt(@TempDir Path dir) throws IOException {
		Path pom = write(dir, POM_WITH_BUILD);
		installer.install(pom);
		String result = Files.readString(pom);
		assertTrue(result.contains("<version>" + PomEnforcerInstaller.ENFORCER_VERSION + "</version>"),
				"a freshly created maven-enforcer-plugin must declare a version so the adopted build validates");
	}

	@Test
	void bindsExecutionToTheRootModuleOnly(@TempDir Path dir) throws IOException {
		Path pom = write(dir, POM_WITH_BUILD);
		installer.install(pom);
		String result = Files.readString(pom);
		assertTrue(result.contains("<inherited>false</inherited>"),
				"CLAUDE.md lives only at the repository root, so child modules must not inherit the execution");
	}

	@Test
	void configuresClaudeMdFileForTheRule(@TempDir Path dir) throws IOException {
		Path pom = write(dir, POM_WITH_BUILD);
		installer.install(pom);
		String result = Files.readString(pom);
		assertTrue(result.contains("<claudeMdFile>"));
		assertTrue(result.contains("${project.basedir}/CLAUDE.md"));
	}

	@Test
	void augmentsExistingEnforcerPluginInsteadOfSkipping(@TempDir Path dir) throws IOException {
		Path pom = write(dir, POM_WITH_ENFORCER);
		assertTrue(installer.install(pom));
		String result = Files.readString(pom);
		assertTrue(result.contains("tools.claude-code-enforcer"));
		assertTrue(result.contains("claudeMdFormat"));
		assertEquals(1, countOccurrences(result, "<artifactId>maven-enforcer-plugin</artifactId>"));
	}

	@Test
	void keepsExistingEnforcerExecution(@TempDir Path dir) throws IOException {
		Path pom = write(dir, POM_WITH_ENFORCER);
		installer.install(pom);
		assertTrue(Files.readString(pom).contains("requireMavenVersion"));
	}

	@Test
	void doesNotReAddRuleToAnEnforcerPluginThatAlreadyHasIt(@TempDir Path dir) throws IOException {
		Path pom = write(dir, POM_WITH_ENFORCER);
		assertTrue(installer.install(pom));
		assertFalse(installer.install(pom));
	}

	private int countOccurrences(String text, String token) {
		int count = 0;
		int index = text.indexOf(token);
		while (index >= 0) {
			count++;
			index = text.indexOf(token, index + token.length());
		}
		return count;
	}

	@Test
	void keepsExistingCompilerPlugin(@TempDir Path dir) throws IOException {
		Path pom = write(dir, POM_WITH_BUILD);
		installer.install(pom);
		assertTrue(Files.readString(pom).contains("maven-compiler-plugin"));
	}

	@Test
	void createsBuildAndPluginsWhenAbsent(@TempDir Path dir) throws IOException {
		Path pom = write(dir, POM_WITHOUT_BUILD);
		assertTrue(installer.install(pom));
		String result = Files.readString(pom);
		assertTrue(result.contains("<build>"));
		assertTrue(result.contains("maven-enforcer-plugin"));
	}

	@Test
	void secondInstallIsIdempotent(@TempDir Path dir) throws IOException {
		Path pom = write(dir, POM_WITH_BUILD);
		assertTrue(installer.install(pom));
		assertFalse(installer.install(pom));
	}

	@Test
	void preservesDefaultPomNamespace(@TempDir Path dir) throws IOException {
		Path pom = write(dir, POM_WITH_BUILD);
		installer.install(pom);
		assertTrue(Files.readString(pom).contains("http://maven.apache.org/POM/4.0.0"));
	}
}
