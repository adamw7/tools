package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.adopt.AdoptionException;

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

	private static final String POM_SINGLE_LINE =
			"<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><artifactId>demo</artifactId></project>\n";

	private static final String POM_NO_TRAILING_NEWLINE = "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n"
			+ "  <artifactId>demo</artifactId>\n"
			+ "</project>";

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
	 * A POM that declares no namespace at all is still valid Maven input, and its
	 * added elements must stay namespace-less too — qualifying them would leave the
	 * build with elements Maven no longer recognises.
	 */
	@Test
	void wiresTheRuleIntoAPomThatDeclaresNoNamespace(@TempDir Path dir) throws IOException {
		Path pom = write(dir, POM_WITH_BUILD.replace(" xmlns=\"http://maven.apache.org/POM/4.0.0\"", ""));
		assertTrue(installer.install(pom));
		String result = Files.readString(pom);
		assertTrue(result.contains("<artifactId>tools.claude-code-enforcer</artifactId>"),
				"the rule must be wired in:\n" + result);
		assertFalse(result.contains("xmlns"), "no namespace may be invented for a POM that had none:\n" + result);
	}

	@Test
	void doesNotAddATrailingNewlineToAPomThatHadNone(@TempDir Path dir) throws IOException {
		Path pom = write(dir, POM_WITH_BUILD.stripTrailing());
		installer.install(pom);
		String result = Files.readString(pom);
		assertTrue(result.endsWith("</project>"), "the file must end exactly as it did:\n" + result);
	}

	/**
	 * A declaration sharing its line with the root element has no line terminator
	 * to carry over, so one is supplied rather than running {@code <project>} onto
	 * the declaration's line.
	 */
	@Test
	void preservesADeclarationThatSharesItsLineWithTheRootElement(@TempDir Path dir) throws IOException {
		Path pom = write(dir, "<?xml version=\"1.0\"?>" + POM_WITH_BUILD);
		installer.install(pom);
		String result = Files.readString(pom);
		assertTrue(result.startsWith("<?xml version=\"1.0\"?>\n<project "), "unexpected start:\n" + result);
		assertEquals(1, countOccurrences(result, "<?xml"), "the declaration must not be duplicated");
	}

	@Test
	void fallsBackToATwoSpaceUnitForAPomWithNoIndentation(@TempDir Path dir) throws IOException {
		Path pom = write(dir, POM_WITHOUT_BUILD.replace("\n  ", "\n"));
		installer.install(pom);
		String result = Files.readString(pom);
		assertTrue(result.contains("\n  <build>\n"),
				"a POM carrying no indentation of its own must get the two-space default:\n" + result);
		assertTrue(result.contains("\n    <plugins>\n"), "nesting must scale by the default unit:\n" + result);
	}

	@Test
	void malformedPomAbortsAdoption(@TempDir Path dir) throws IOException {
		Path pom = write(dir, "<project><build></project>");
		assertThrows(AdoptionException.class, () -> installer.install(pom));
	}

	@Test
	void missingPomAbortsAdoption(@TempDir Path dir) {
		Path pom = dir.resolve("pom.xml");
		assertThrows(AdoptionException.class, () -> installer.install(pom));
	}

	/**
	 * A POM whose elements sit on one line offers no indentation to copy, so the
	 * editor falls back to two spaces rather than running the added block together.
	 * The original line is still preserved verbatim.
	 */
	@Test
	void indentsTheAddedBlockByTwoSpacesWhenThePomShowsNoIndentation(@TempDir Path dir) throws IOException {
		Path pom = write(dir, POM_SINGLE_LINE);
		assertTrue(installer.install(pom));
		String result = Files.readString(pom);
		assertTrue(result.startsWith(POM_SINGLE_LINE.strip().replace("</project>", "")),
				"the original line must be preserved verbatim: " + result);
		assertTrue(result.contains("\n  <build>\n"), result);
		assertTrue(result.contains("\n    <plugins>\n"), result);
	}

	/**
	 * The rewrite matches whatever the original ended with, so a POM saved without a
	 * final newline does not gain one and show a spurious last-line change in the
	 * adoption commit.
	 */
	@Test
	void leavesAPomThatEndedWithoutANewlineWithoutOne(@TempDir Path dir) throws IOException {
		Path pom = write(dir, POM_NO_TRAILING_NEWLINE);
		assertTrue(installer.install(pom));
		String result = Files.readString(pom);
		assertTrue(result.contains("tools.claude-code-enforcer"), "the rule must still be wired in");
		assertFalse(result.endsWith("\n"), "a POM with no trailing newline must not gain one");
	}

	@Test
	void aReleaseRuleVersionIsPinnedIntoThePom(@TempDir Path dir) throws IOException {
		Path pom = write(dir, POM_WITH_BUILD);
		assertTrue(new PomEnforcerInstaller("4.1.0").install(pom));
		String result = Files.readString(pom);
		assertTrue(result.contains("tools.claude-code-enforcer"));
		assertTrue(result.contains("<version>4.1.0</version>"), result);
	}

	/**
	 * Resolving the version only when a POM is actually being edited keeps a
	 * non-Maven adoption — and merely constructing the default build-system list —
	 * independent of whichever version the module was built at.
	 */
	@Test
	void theDefaultInstallerResolvesItsVersionOnlyWhenItWiresAPom(@TempDir Path dir) throws IOException {
		Path pom = write(dir, POM_WITH_BUILD);
		installer.install(pom);
		assertFalse(new PomEnforcerInstaller().install(pom));
	}
}
