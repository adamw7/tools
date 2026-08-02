package io.github.adamw7.tools.enforcer.e2e;

import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Enforces this repository's own documentation contract the way
 * {@code .github/workflows/maven.yml} does — {@code mvn -N validate
 * -DenforceClaudeMd}, with the rules wired in the root pom's
 * {@code claude-md-enforce} profile — and then breaks that repository to prove the
 * wiring bites.
 *
 * <p>{@link EnforcerRuleBuildIT} builds a fixture project, so it proves the rules
 * work; it says nothing about the configuration this repository actually ships. A
 * rule can be flawless and still guard nothing here: pointed at the wrong path,
 * left out of the profile, or written down in a document that no longer matches
 * the module list. The three things asserted here are exactly those — that the
 * wired configuration passes today, that every shipped rule is either wired or
 * deliberately left out, and that a regression in the repository's own files is
 * caught rather than waved through.
 *
 * <p>The two regression tests work on a copy in a temporary directory. Breaking
 * the checkout the build is running from and putting it back would leave a
 * developer with a modified working tree if the test failed halfway; the copy
 * carries the same files, so the rules see the same repository.
 */
class RepositoryEnforcementIT {

	/**
	 * Everything the wired rules read: the root pom they take the module map from,
	 * the three documents, the gitignore, and the whole agent configuration.
	 */
	private static final List<String> ENFORCED_PATHS = List.of(
			"pom.xml", "CLAUDE.md", "AGENTS.md", "README.md", ".gitignore", ".claude");

	/**
	 * The rules that ship unwired on purpose. Both take a definition directory that
	 * must exist when configured, so wiring one before the repository has the
	 * directory would fail the build as a setup mistake; they are wired the day the
	 * directory is added.
	 */
	private static final Set<String> DELIBERATELY_UNWIRED = Set.of("subAgentFormat", "commandFormat");

	private static BuildEnvironment environment;
	private static MavenBuild maven;
	private static RepositoryPom repositoryPom;

	@TempDir
	Path copy;

	@BeforeAll
	static void publishTheRuleUnderTest() {
		environment = new BuildEnvironment();
		repositoryPom = new RepositoryPom(environment.repositoryRoot());
		maven = new MavenBuild(environment);
		maven.publishRuleUnderTest(repositoryPom.pluginVersion("maven-install-plugin"));
	}

	/**
	 * The check the pull-request workflow runs, run against the checkout it is being
	 * run from. Each wired rule is confirmed to have reported a pass, because a rule
	 * that silently never ran would leave this test green while guarding nothing.
	 */
	@Test
	void theRepositoryPassesTheContractItShipsWith() {
		BuildOutcome outcome = enforce(environment.repositoryRoot());

		assertTrue(outcome.succeeded(), outcome::describe);
		for (String name : repositoryPom.wiredRuleNames()) {
			assertTrue(outcome.mentions("(" + name + ") passed"),
					() -> name + " is wired but did not report a pass: " + outcome.describe());
		}
	}

	/**
	 * A rule nobody wires guards nothing, and the two that ship unwired do so for a
	 * documented reason. Comparing the shipped catalogue against the profile turns
	 * "someone forgot to wire it" into a failure, and equally stops the exemption
	 * list from quietly growing.
	 */
	@Test
	void everyShippedRuleIsWiredIntoTheBuildOrDeliberatelyLeftOut() {
		Set<String> shipped = ShippedRules.byName().keySet();
		Set<String> wired = repositoryPom.wiredRuleNames();

		assertTrue(shipped.containsAll(wired),
				() -> "the profile wires rules this module does not ship: " + difference(wired, shipped));
		assertEquals(DELIBERATELY_UNWIRED, difference(shipped, wired),
				"a shipped rule is either wired into the claude-md-enforce profile or a documented exemption");
	}

	/**
	 * The rule that would notice a skill losing its definition is wired at
	 * {@code ${project.basedir}/.claude/skills}, which is a claim about a path that
	 * no unit test can check. Removing a real {@code SKILL.md} from the copy is what
	 * proves the wired path is the one the skills are actually under.
	 */
	@Test
	void theWiredConfigurationCatchesASkillThatLostItsDefinition() {
		Path repository = copyOfRepository();
		Path skill = firstSkillIn(repository);
		delete(skill.resolve("SKILL.md"));

		BuildOutcome outcome = enforce(repository);

		assertFalse(outcome.succeeded(), outcome::describe);
		assertTrue(outcome.mentions("(skillFilesExist) failed"), outcome::describe);
		assertTrue(outcome.mentions("Missing SKILL.md in skill directory: "), outcome::describe);
		assertTrue(outcome.mentions(skill.getFileName().toString()), outcome::describe);
	}

	/**
	 * The cross-document rule is the one wired with two paths at once, and the fact
	 * it pins — the Java version — is the one most likely to be bumped in one
	 * document and forgotten in the other. Bumping it in the copy's CLAUDE.md alone
	 * is that mistake, made on purpose.
	 */
	@Test
	void theWiredConfigurationCatchesTheAgentDocumentsContradictingEachOther() {
		Path repository = copyOfRepository();
		Path claudeMd = repository.resolve("CLAUDE.md");
		write(claudeMd, read(claudeMd).replace("Java 25", "Java 21"));

		BuildOutcome outcome = enforce(repository);

		assertFalse(outcome.succeeded(), outcome::describe);
		assertTrue(outcome.mentions("(crossDocConsistency) failed"), outcome::describe);
		assertTrue(outcome.mentions("captured"), outcome::describe);
	}

	private BuildOutcome enforce(Path repository) {
		return maven.run(repository, "-N", "validate", "-DenforceClaudeMd");
	}

	/** The repository as the wired rules see it: only the files they are pointed at. */
	private Path copyOfRepository() {
		for (String path : ENFORCED_PATHS) {
			copyTree(environment.repositoryRoot().resolve(path), copy.resolve(path));
		}
		return copy;
	}

	/**
	 * Chosen by name rather than named outright, so renaming a skill does not rename
	 * it here too; sorted, so a failure reports the same skill on every run.
	 */
	private Path firstSkillIn(Path repository) {
		Path skills = repository.resolve(".claude/skills");
		try (Stream<Path> directories = Files.list(skills)) {
			return directories.filter(Files::isDirectory).min(Comparator.comparing(Path::getFileName))
					.orElseThrow(() -> new IllegalStateException("There are no skills under " + skills));
		} catch (IOException e) {
			throw new UncheckedIOException("Could not list " + skills, e);
		}
	}

	/**
	 * Copied with the file attributes, because one of the things the wired rules
	 * check is that a hook script is executable — a copy that dropped the mode bits
	 * would fail for a reason the repository is not guilty of.
	 */
	private void copyTree(Path source, Path target) {
		try (Stream<Path> tree = Files.walk(source)) {
			for (Path path : tree.toList()) {
				copyOne(path, target.resolve(source.relativize(path).toString()));
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Could not copy " + source, e);
		}
	}

	private void copyOne(Path source, Path target) throws IOException {
		if (Files.isDirectory(source)) {
			Files.createDirectories(target);
		} else {
			Files.createDirectories(target.getParent());
			Files.copy(source, target, COPY_ATTRIBUTES);
		}
	}

	private String read(Path file) {
		try {
			return Files.readString(file);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not read " + file, e);
		}
	}

	private void write(Path file, String content) {
		try {
			Files.writeString(file, content);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not write " + file, e);
		}
	}

	private void delete(Path file) {
		try {
			Files.delete(file);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not delete " + file, e);
		}
	}

	/** What is in {@code all} but not in {@code some}, sorted so a failure message is stable. */
	private Set<String> difference(Set<String> all, Set<String> some) {
		Set<String> difference = new TreeSet<>(all);
		difference.removeAll(some);
		return difference;
	}
}
