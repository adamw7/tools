package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StarterSkillsTest {

	private static final String WRAPPER = "mvnw";

	@Test
	void namesOneSkillDirectoryPerSkill(@TempDir Path checkout) {
		assertEquals(List.of(".claude/skills/build-and-test/SKILL.md", ".claude/skills/claude-md/SKILL.md"),
				installedPaths(new MavenBuildSystem(), checkout));
	}

	/**
	 * The paths are what {@link AdoptionAssets#WRITTEN_PATHS} folds in without a
	 * checkout in hand, so they must be the paths a run really writes rather than a
	 * second list kept beside them.
	 */
	@Test
	void writesTheVeryPathsItPublishes(@TempDir Path checkout) {
		assertEquals(StarterSkills.WRITTEN_PATHS, installedPaths(new MavenBuildSystem(), checkout));
	}

	/** {@code skillFilesExist} fails a skill whose {@code name:} is not its directory name. */
	@Test
	void eachSkillNamesItselfAfterItsDirectory(@TempDir Path checkout) throws IOException {
		install(new MavenBuildSystem(), checkout);
		StarterSkills.NAMES.forEach(name -> assertTrue(read(checkout, name).contains("\nname: " + name + "\n"),
				name));
	}

	@Test
	void eachSkillOpensWithFrontMatterDeclaringANameAndADescription(@TempDir Path checkout) throws IOException {
		install(new MavenBuildSystem(), checkout);
		StarterSkills.NAMES.forEach(name -> assertFrontMatterIsWellFormed(read(checkout, name), name));
	}

	/** {@code uniqueDescriptions} fails a repository where two definitions describe themselves alike. */
	@Test
	void theSkillsDescribeThemselvesDifferently(@TempDir Path checkout) throws IOException {
		install(new MavenBuildSystem(), checkout);
		assertFalse(description(read(checkout, StarterSkills.BUILD_SKILL))
				.equals(description(read(checkout, StarterSkills.CLAUDE_MD_SKILL))));
	}

	@Test
	void aMavenSkillListsTheHeadingsTheWiredGuardDemands(@TempDir Path checkout) throws IOException {
		install(new MavenBuildSystem(), checkout);
		String skill = read(checkout, StarterSkills.CLAUDE_MD_SKILL);
		ClaudeMdConformer.REQUIRED_SECTIONS.forEach(section -> assertTrue(skill.contains("`" + section + "`"),
				section + " missing from: " + skill));
	}

	/**
	 * The Gradle and workflow guards ask only that the document exist and carry
	 * something, so listing the Maven rule's headings would send a contributor
	 * appending sections nothing checks.
	 */
	@Test
	void aGradleSkillListsNoHeadingItsGuardNeverReads(@TempDir Path checkout) throws IOException {
		install(new GradleBuildSystem(), checkout);
		String skill = read(checkout, StarterSkills.CLAUDE_MD_SKILL);
		assertFalse(skill.contains("## Principles for Java Development"), skill);
		assertTrue(skill.contains("no section in particular"), skill);
	}

	@Test
	void eachSkillCarriesTheCommandThatRunsTheDetectedGuard(@TempDir Path checkout) throws IOException {
		install(new GradleBuildSystem(), checkout);
		StarterSkills.NAMES.forEach(name -> assertTrue(read(checkout, name).contains("gradle -q enforceClaudeMd"),
				read(checkout, name)));
	}

	@Test
	void theBuildSkillSaysWhatBuildsTheProject(@TempDir Path checkout) throws IOException {
		install(new MavenBuildSystem(), checkout);
		assertTrue(read(checkout, StarterSkills.BUILD_SKILL).contains("built with maven"),
				read(checkout, StarterSkills.BUILD_SKILL));
	}

	/**
	 * A checkout with no build file is adopted by the catch-all build system, which is
	 * not a build tool: a skill claiming the project "is built with github-actions"
	 * would be telling its contributors something untrue about their own repository.
	 */
	@Test
	void theBuildSkillDoesNotCallTheCatchAllGuardABuildTool(@TempDir Path checkout) throws IOException {
		install(new FallbackBuildSystem(), checkout);
		String skill = read(checkout, StarterSkills.BUILD_SKILL);
		assertFalse(skill.contains("built with github-actions"), skill);
		assertTrue(skill.contains("no Maven or Gradle build file"), skill);
	}

	/**
	 * {@link BuildSystem#verifyCommand(Path)} answers a checkout's wrapper by absolute
	 * path, because a process is launched from the JVM's working directory rather than
	 * the command's. Committing that verbatim would put the adoption host's workspace
	 * path into somebody else's repository, naming a directory no contributor has.
	 */
	@Test
	void theWrapperIsNamedRelativelyRatherThanByTheAdoptionHostsPath(@TempDir Path checkout) throws IOException {
		Files.writeString(checkout.resolve(WRAPPER), "#!/bin/sh\n");
		install(new MavenBuildSystem(), checkout);
		String skill = read(checkout, StarterSkills.BUILD_SKILL);
		assertTrue(skill.contains("./mvnw -q -N validate"), skill);
		assertFalse(skill.contains(checkout.toAbsolutePath().toString()), skill);
	}

	/** A flag is not a path, and asking the filesystem to parse one is how Windows differs. */
	@Test
	void theGuardCommandsFlagsSurviveUnchanged(@TempDir Path checkout) throws IOException {
		install(new MavenBuildSystem(), checkout);
		assertTrue(read(checkout, StarterSkills.BUILD_SKILL).contains("mvn -q -N validate"),
				read(checkout, StarterSkills.BUILD_SKILL));
	}

	@Test
	void aProjectsOwnCommandOfTheSameNameKeepsIt(@TempDir Path checkout) throws IOException {
		write(checkout, ".claude/commands/build-and-test.md", "# the project's own\n");
		install(new MavenBuildSystem(), checkout);
		assertTrue(Files.notExists(checkout.resolve(StarterSkills.skillFile(StarterSkills.BUILD_SKILL))));
		assertTrue(Files.isRegularFile(checkout.resolve(StarterSkills.skillFile(StarterSkills.CLAUDE_MD_SKILL))));
	}

	@Test
	void aProjectsOwnSubAgentOfTheSameNameKeepsIt(@TempDir Path checkout) throws IOException {
		write(checkout, ".claude/agents/claude-md.md", "# the project's own\n");
		install(new MavenBuildSystem(), checkout);
		assertTrue(Files.notExists(checkout.resolve(StarterSkills.skillFile(StarterSkills.CLAUDE_MD_SKILL))));
	}

	/**
	 * A skill directory the project already carries claims the name whether or not it
	 * holds a {@code SKILL.md} yet, so the adoption never writes its own skill into a
	 * directory somebody else made.
	 */
	@Test
	void aProjectsOwnSkillDirectoryKeepsItsNameEvenWhileEmpty(@TempDir Path checkout) throws IOException {
		Files.createDirectories(checkout.resolve(".claude/skills/build-and-test"));
		install(new MavenBuildSystem(), checkout);
		assertTrue(Files.notExists(checkout.resolve(StarterSkills.skillFile(StarterSkills.BUILD_SKILL))));
	}

	@Test
	void aCheckoutWithNoClaudeDirectoryClaimsNothing(@TempDir Path checkout) {
		assertEquals(Set.of(), StarterSkills.claimedNames(checkout));
	}

	@Test
	void claimedNamesAreReadFromEveryDirectoryADefinitionIsNamedBy(@TempDir Path checkout) throws IOException {
		write(checkout, ".claude/commands/review.md", "");
		write(checkout, ".claude/agents/auditor.md", "");
		Files.createDirectories(checkout.resolve(".claude/skills/deploy"));
		assertEquals(Set.of("review", "auditor", "deploy"), StarterSkills.claimedNames(checkout));
	}

	/** Only Markdown names a command or a sub-agent; a README beside them names nothing. */
	@Test
	void aNonMarkdownFileClaimsNoName(@TempDir Path checkout) throws IOException {
		write(checkout, ".claude/commands/notes.txt", "");
		assertEquals(Set.of(), StarterSkills.claimedNames(checkout));
	}

	@Test
	void reRunningInstallsNothingOverTheProjectsEdits(@TempDir Path checkout) throws IOException {
		install(new MavenBuildSystem(), checkout);
		Path skill = checkout.resolve(StarterSkills.skillFile(StarterSkills.BUILD_SKILL));
		Files.writeString(skill, "customised\n");
		install(new MavenBuildSystem(), checkout);
		assertEquals("customised\n", Files.readString(skill));
	}

	/**
	 * A build system may name no verification at all, and a skill telling a contributor
	 * to run an empty command line would be worse than one telling them there is
	 * nothing to run.
	 */
	@Test
	void aBuildSystemWithNoVerificationIsSaidToHaveNone(@TempDir Path checkout) throws IOException {
		install(new UnverifiedBuildSystem(), checkout);
		String skill = read(checkout, StarterSkills.BUILD_SKILL);
		assertTrue(skill.contains("no command of its own"), skill);
		assertFalse(skill.contains("```sh\n```"), skill);
	}

	/**
	 * A skill's name is a directory name, so it has to survive the checkout's ignore
	 * rules as well as its other definitions: {@code build} is one of the most widely
	 * ignored words in a JVM project's {@code .gitignore}, and naming a skill that lost
	 * four of the seven repositories {@link
	 * io.github.adamw7.tools.adopt.ForeignRepositoryAdoptionIT} adopts to
	 * {@link CommitStep}'s refusal to commit a path the checkout excludes.
	 */
	@Test
	void noSkillIsNamedAfterAWidelyIgnoredDirectory() {
		List<String> ignored = List.of("build", "target", "out", "dist", "bin", "obj");
		StarterSkills.NAMES.forEach(name -> assertFalse(ignored.contains(name),
				() -> "a skill called " + name + " would be excluded by the ignore rules of most projects"));
	}

	/** A build system whose guard runs as part of the build and has no command to name. */
	private static final class UnverifiedBuildSystem implements BuildSystem {

		@Override
		public String name() {
			return "unverified";
		}

		@Override
		public boolean matches(Path repositoryDirectory) {
			return true;
		}

		@Override
		public List<String> writtenPaths() {
			return List.of();
		}

		@Override
		public boolean install(Path repositoryDirectory) {
			return false;
		}

		@Override
		public boolean isGuardInstalled(Path repositoryDirectory) {
			return true;
		}

		@Override
		public List<String> verifyCommand(Path repositoryDirectory) {
			return List.of();
		}
	}

	private void assertFrontMatterIsWellFormed(String skill, String name) {
		assertTrue(skill.startsWith("---\n"), name + " must open with front matter: " + skill);
		assertTrue(skill.contains("\ndescription: "), name + " must declare a description: " + skill);
		assertFalse(description(skill).isBlank(), name + " must describe itself: " + skill);
	}

	private String description(String skill) {
		return skill.lines()
				.filter(line -> line.startsWith("description: "))
				.map(line -> line.substring("description: ".length()))
				.findFirst()
				.orElse("");
	}

	private List<String> installedPaths(BuildSystem buildSystem, Path checkout) {
		return StarterSkills.forCheckout(buildSystem, checkout).stream()
				.map(AssetInstaller::relativePath)
				.toList();
	}

	private void install(BuildSystem buildSystem, Path checkout) {
		StarterSkills.forCheckout(buildSystem, checkout).forEach(installer -> installer.install(checkout));
	}

	private String read(Path checkout, String skillName) {
		Path skill = checkout.resolve(StarterSkills.skillFile(skillName));
		try {
			return Files.readString(skill);
		} catch (IOException e) {
			throw new IllegalStateException("Could not read " + skill, e);
		}
	}

	private void write(Path checkout, String relativePath, String content) throws IOException {
		Path file = checkout.resolve(relativePath);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
	}
}
