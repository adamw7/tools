package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.BeforeAll;
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

	private static final String POM_FOUR_SPACE_INDENT = "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n"
			+ "    <modelVersion>4.0.0</modelVersion>\n"
			+ "    <groupId>com.example</groupId>\n"
			+ "    <artifactId>demo</artifactId>\n"
			+ "    <version>1.0.0</version>\n"
			+ "</project>\n";

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

	/**
	 * The JDK's JAXP factories ({@code DocumentBuilderFactory} and
	 * {@code TransformerFactory}) pay a one-time, classpath-scanning
	 * initialization cost the first time they are used. Charging that cold start
	 * to whichever {@code @Test} happens to run first makes it flake against
	 * surefire's 900ms per-test timeout, so pay it once here — a full parse and
	 * write through the real install path — under the looser lifecycle-method
	 * timeout instead.
	 */
	@BeforeAll
	static void warmUpXmlToolchain(@TempDir Path dir) throws IOException {
		Path pom = dir.resolve("pom.xml");
		Files.writeString(pom, POM_WITH_BUILD);
		new PomEnforcerInstaller("0.0.0").install(pom);
	}

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

	@Test
	void leavesExistingFormattingUntouchedAndOnlyIndentsTheNewBlock(@TempDir Path dir) throws IOException {
		Path pom = write(dir, POM_WITH_BUILD);
		installer.install(pom);
		String result = Files.readString(pom);
		assertTrue(result.contains(
				"      <plugin>\n"
						+ "        <groupId>org.apache.maven.plugins</groupId>\n"
						+ "        <artifactId>maven-compiler-plugin</artifactId>\n"
						+ "      </plugin>"),
				"the existing plugin must be preserved verbatim, not reformatted");
		assertTrue(result.contains("\n          <dependency>\n"),
				"the added dependency must be indented to the POM's own two-space unit, not jammed onto one line");
		assertTrue(result.contains("\n            <artifactId>tools.claude-code-enforcer</artifactId>\n"),
				"nested added elements must keep indenting by the same unit");
		assertFalse(result.contains("<?xml"),
				"no XML declaration should be invented for a POM that had none");
		assertTrue(result.startsWith("<project "), "the first line must be preserved unchanged");
		assertTrue(result.endsWith("</project>\n"), "the original trailing newline must be preserved");
	}

	@Test
	void preservesAnExistingXmlDeclarationOnItsOwnLine(@TempDir Path dir) throws IOException {
		Path pom = write(dir, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + POM_WITH_BUILD);
		installer.install(pom);
		String result = Files.readString(pom);
		assertTrue(result.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<project "),
				"the original declaration must be kept verbatim on its own line, not rewritten");
		assertEquals(1, countOccurrences(result, "<?xml"), "the declaration must not be duplicated");
	}

	@Test
	void preservesCarriageReturnLineEndingsRatherThanReformattingToLf(@TempDir Path dir) throws IOException {
		Path pom = write(dir, POM_WITH_BUILD.replace("\n", "\r\n"));
		installer.install(pom);
		String result = Files.readString(pom);
		assertTrue(result.contains("tools.claude-code-enforcer"), "the rule must still be wired in");
		assertFalse(stripCrlf(result).contains("\n"),
				"a CRLF POM must stay CRLF; no line may be left with a bare LF ending");
		assertTrue(result.contains(
				"      <plugin>\r\n"
						+ "        <groupId>org.apache.maven.plugins</groupId>\r\n"
						+ "        <artifactId>maven-compiler-plugin</artifactId>\r\n"
						+ "      </plugin>"),
				"the existing plugin must be preserved verbatim with its CRLF endings");
		assertTrue(result.contains("\r\n            <artifactId>tools.claude-code-enforcer</artifactId>\r\n"),
				"the added block must use the file's CRLF endings, not LF");
	}

	private String stripCrlf(String text) {
		return text.replace("\r\n", "");
	}

	@Test
	void indentsTheAddedBlockToTheDocumentsOwnIndentationUnit(@TempDir Path dir) throws IOException {
		Path pom = write(dir, POM_FOUR_SPACE_INDENT);
		installer.install(pom);
		String result = Files.readString(pom);
		assertTrue(result.contains("\n    <modelVersion>4.0.0</modelVersion>\n"),
				"the four-space original lines must be preserved");
		assertTrue(result.contains("\n    <build>\n"),
				"a created element must use the file's four-space unit, not the default two");
		assertTrue(result.contains("\n        <plugins>\n"),
				"nesting must scale by the detected four-space unit");
	}

	/**
	 * The default installer must pin the rule to the version Maven filtered into
	 * {@code adopt-build.properties} — the release actually running the adoption —
	 * rather than a hardcoded literal that drifts as the project is versioned.
	 */
	@Test
	void defaultInstallerPinsTheRuleToTheFilteredBuildVersion(@TempDir Path dir) throws IOException {
		String buildVersion = filteredRuleVersion();
		Path pom = write(dir, POM_WITH_BUILD);
		assertTrue(new PomEnforcerInstaller().install(pom));
		String result = Files.readString(pom);
		assertTrue(result.contains("tools.claude-code-enforcer"));
		assertTrue(result.contains("<version>" + buildVersion + "</version>"),
				"the rule dependency must be pinned to the filtered build version " + buildVersion);
	}

	private String filteredRuleVersion() throws IOException {
		try (InputStream stream = getClass().getResourceAsStream(PomEnforcerInstaller.BUILD_PROPERTIES)) {
			assertNotNull(stream, PomEnforcerInstaller.BUILD_PROPERTIES + " must be filtered onto the test classpath");
			Properties properties = new Properties();
			properties.load(stream);
			String version = properties.getProperty(PomEnforcerInstaller.RULE_VERSION_KEY, "").strip();
			assertFalse(version.isEmpty() || version.startsWith("${"),
					"enforcer.rule.version must be filtered to a concrete version, was: " + version);
			return version;
		}
	}
}
