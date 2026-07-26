package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

	/**
	 * Formatted the way a real project's POM is, with the details a DOM cannot
	 * remember: a start tag broken over several lines, tab indentation, and empty
	 * elements written with a space before the slash.
	 */
	private static final String POM_HAND_FORMATTED = "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
			+ "\txmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
			+ "\t<artifactId>demo</artifactId>\n"
			+ "\t<build>\n"
			+ "\t\t<plugins>\n"
			+ "\t\t\t<plugin>\n"
			+ "\t\t\t\t<artifactId>maven-enforcer-plugin</artifactId>\n"
			+ "\t\t\t\t<executions>\n"
			+ "\t\t\t\t\t<execution>\n"
			+ "\t\t\t\t\t\t<configuration>\n"
			+ "\t\t\t\t\t\t\t<rules>\n"
			+ "\t\t\t\t\t\t\t\t<dependencyConvergence />\n"
			+ "\t\t\t\t\t\t\t</rules>\n"
			+ "\t\t\t\t\t\t</configuration>\n"
			+ "\t\t\t\t\t</execution>\n"
			+ "\t\t\t\t</executions>\n"
			+ "\t\t\t</plugin>\n"
			+ "\t\t</plugins>\n"
			+ "\t</build>\n"
			+ "</project>\n";

	private static final String POM_SELF_CLOSING_PLUGINS = """
			<project xmlns="http://maven.apache.org/POM/4.0.0">
			  <artifactId>demo</artifactId>
			  <properties/>
			  <build>
			    <plugins/>
			  </build>
			</project>
			""";

	/** Wires the rule the way this repository does: behind an opt-in profile, not in the build. */
	private static final String POM_WITH_RULE_IN_A_PROFILE = """
			<project xmlns="http://maven.apache.org/POM/4.0.0">
			  <artifactId>demo</artifactId>
			  <profiles>
			    <profile>
			      <id>claude-md-enforce</id>
			      <build>
			        <plugins>
			          <plugin>
			            <artifactId>maven-enforcer-plugin</artifactId>
			            <dependencies>
			              <dependency>
			                <groupId>io.github.adamw7</groupId>
			                <artifactId>tools.claude-code-enforcer</artifactId>
			                <version>1.0.0</version>
			              </dependency>
			            </dependencies>
			          </plugin>
			        </plugins>
			      </build>
			    </profile>
			  </profiles>
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

	/**
	 * A project may well run the guard from somewhere other than its {@code build}:
	 * behind an opt-in profile, most often, so ordinary builds are unaffected.
	 * Looking only at {@code build/plugins} reports such a POM as unguarded and wires
	 * in a second, always-on copy of a rule the project already runs on its own
	 * terms.
	 */
	@Test
	void leavesAPomThatWiresTheRuleInsideAProfileAlone(@TempDir Path dir) throws IOException {
		Path pom = write(dir, POM_WITH_RULE_IN_A_PROFILE);
		assertFalse(installer.install(pom), "the rule is already wired in, in the profile");
		assertEquals(POM_WITH_RULE_IN_A_PROFILE, Files.readString(pom), "the POM must not have been touched");
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
	 * A declaration sharing its line with the root element keeps sharing it: the
	 * edit is spliced into the text the file already held, so a region the adoption
	 * has no business touching is not rewritten — not even to put the root element
	 * on a line of its own.
	 */
	@Test
	void preservesADeclarationThatSharesItsLineWithTheRootElement(@TempDir Path dir) throws IOException {
		Path pom = write(dir, "<?xml version=\"1.0\"?>" + POM_WITH_BUILD);
		installer.install(pom);
		String result = Files.readString(pom);
		assertTrue(result.startsWith("<?xml version=\"1.0\"?><project "), "unexpected start:\n" + result);
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

	/**
	 * The whole point of splicing rather than re-serialising: a start tag the project
	 * spread over several lines, and an empty element it wrote {@code <rule />},
	 * are details a DOM does not record, so writing the edited document out whole
	 * normalises both and an adoption that adds one block arrives as a diff across
	 * the file.
	 */
	@Test
	void leavesAMultiLineStartTagAndSpacedEmptyElementsUntouched(@TempDir Path dir) throws IOException {
		Path pom = write(dir, POM_HAND_FORMATTED);
		assertTrue(installer.install(pom));
		String result = Files.readString(pom);
		assertTrue(result.contains("<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
				+ "\txmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"),
				"the multi-line start tag must keep its own line breaks:\n" + result);
		assertTrue(result.contains("<dependencyConvergence />"),
				"an empty element the project spaced must not be rewritten as <x/>:\n" + result);
	}

	/**
	 * The general statement of the same contract, and the one that fails for a
	 * reformat anywhere in the file: every line the POM already held is still there,
	 * in order and character for character, with only new lines in between. Stated
	 * over lines rather than as a single untouched prefix and suffix because the
	 * install legitimately writes in two places at once — the rule dependency onto
	 * the plugin, the execution into its existing {@code <executions>}.
	 */
	@Test
	void changesNothingOutsideTheAddedBlock(@TempDir Path dir) throws IOException {
		Path pom = write(dir, POM_HAND_FORMATTED);
		installer.install(pom);
		assertEveryOriginalLineSurvives(POM_HAND_FORMATTED, Files.readString(pom));
	}

	/**
	 * A {@code <plugins/>} element has no end tag for the block to be inserted
	 * before, so it has to grow one rather than being left for the serialiser to
	 * expand along with everything else.
	 */
	@Test
	void wiresTheRuleIntoASelfClosingPluginsElement(@TempDir Path dir) throws IOException {
		Path pom = write(dir, POM_SELF_CLOSING_PLUGINS);
		assertTrue(installer.install(pom));
		String result = Files.readString(pom);
		assertTrue(result.contains("<artifactId>tools.claude-code-enforcer</artifactId>"),
				"the rule must be wired in:\n" + result);
		assertFalse(result.contains("<plugins/>"), "the empty element must have been reopened:\n" + result);
		assertTrue(result.contains("\n    </plugins>"),
				"the end tag it grew must be indented to the element's own depth:\n" + result);
		assertTrue(result.contains("\n  <properties/>"),
				"an unrelated empty element must be left exactly as it was:\n" + result);
	}

	/**
	 * Asserts the original's lines are still a subsequence of the result's: an edit
	 * that only inserts leaves them all matchable in order, while any reformat
	 * changes a line and leaves it unmatched.
	 *
	 * @param original what the file held before the install
	 * @param result   what it holds after
	 */
	private void assertEveryOriginalLineSurvives(String original, String result) {
		List<String> lines = result.lines().toList();
		int next = 0;
		for (String line : original.lines().toList()) {
			int found = lines.subList(next, lines.size()).indexOf(line);
			assertTrue(found >= 0, "the edit reformatted a line the adoption should not have touched: '" + line
					+ "'\nresult:\n" + result);
			next += found + 1;
		}
		assertTrue(result.lines().count() > original.lines().count(), "nothing was added:\n" + result);
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
	 * A version named on the command line is checked as it is supplied rather than at
	 * the one step that edits a POM: asking for a snapshot on purpose does not make it
	 * resolvable for the adopted project's CI, so there is nothing to gain by
	 * discovering it after a clone and a {@code claude init}.
	 */
	@Test
	void anExplicitSnapshotRuleVersionIsRejectedBeforeAnythingRuns() {
		assertThrows(AdoptionException.class, () -> new PomEnforcerInstaller("2.6.0-SNAPSHOT"));
	}

	@Test
	void aBlankExplicitRuleVersionIsRejected() {
		assertThrows(AdoptionException.class, () -> new PomEnforcerInstaller("  "));
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
