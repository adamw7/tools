package io.github.adamw7.tools.adopt.step;

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The starter Claude Code skills a {@link SkillsStep} writes under
 * {@value #SKILLS_DIRECTORY}, one directory each holding a {@code SKILL.md}. A
 * skill is how Claude Code is told a convention once instead of being reminded of
 * it every session, so an adopted repository that gets a {@code CLAUDE.md} and no
 * skills is adopted only halfway.
 *
 * <p>Both skills are written from what the adoption itself established rather than
 * from anything guessed about the project: the build system {@link EnforcerStep}
 * wired the guard into, the sections that guard demands, and the very command
 * {@link VerifyStep} runs. Everything the adoption does <em>not</em> know — how this
 * project builds, tests and lints — is left as a heading for its maintainers to
 * fill in, the same bargain {@code .claude/hooks/session-start.sh} strikes.
 *
 * <p>The bodies vary with the build system; the paths do not, which is what lets
 * {@link AdoptionAssets#WRITTEN_PATHS} name them without a checkout in hand.
 */
public final class StarterSkills {

	private static final Logger log = LogManager.getLogger(StarterSkills.class);

	/** The directory Claude Code reads a project's skills from. */
	static final String SKILLS_DIRECTORY = ".claude/skills";

	/** The file inside a skill directory that carries the skill; the name Claude Code looks for. */
	static final String SKILL_FILE = "SKILL.md";

	/**
	 * The other two directories a Claude Code definition is named by, read only to
	 * find out which names are taken — the adoption installs nothing into either.
	 */
	static final String COMMANDS_DIRECTORY = ".claude/commands";
	static final String AGENTS_DIRECTORY = ".claude/agents";

	private static final String MARKDOWN_SUFFIX = ".md";

	/**
	 * Not {@code build}: a skill's name is a directory name, and {@code build} is one of
	 * the most widely ignored words in a JVM project's {@code .gitignore}. Four of the
	 * seven real repositories {@code ForeignRepositoryAdoptionIT} adopts excluded
	 * {@code .claude/skills/build/SKILL.md} on that rule alone, and {@link CommitStep}
	 * rightly refused to commit around it — an adoption lost to a word.
	 */
	static final String BUILD_SKILL = "build-and-test";
	static final String CLAUDE_MD_SKILL = "claude-md";

	/**
	 * The skills installed, in the order they are written. A name is also the skill's
	 * directory name and its {@code name:} front matter, which the guard's
	 * {@code skillFilesExist} rule requires to be the same word.
	 */
	static final List<String> NAMES = List.of(BUILD_SKILL, CLAUDE_MD_SKILL);

	/** Every checkout-relative path the starter skills occupy, whatever the build system. */
	public static final List<String> WRITTEN_PATHS = NAMES.stream().map(StarterSkills::skillFile).toList();

	private static final String BUILD_SKILL_TEMPLATE = """
			---
			name: build-and-test
			description: Build, test and verify this repository, and run the CLAUDE.md guard wired into its build. Use when building the project, running its tests, or checking a change before pushing it.
			---

			# Build and test

			%1$s

			## Run the CLAUDE.md guard

			Claude Code adoption wired a guard into the build and verified the generated
			`CLAUDE.md` with this command, run from the repository root:

			```sh
			%2$s
			```

			Run it before pushing a change to `CLAUDE.md` or `AGENTS.md`: it is the same
			check the build runs, so a failure here is a failure in CI.

			## This project's own commands

			The adoption knows the build, not this project's conventions. Fill these in so
			an agent stops guessing at them:

			- Build:
			- Test:
			- Lint or static analysis:
			- Run locally:
			""";

	private static final String CLAUDE_MD_SKILL_TEMPLATE = """
			---
			name: claude-md
			description: Keep CLAUDE.md and AGENTS.md satisfying the guard this repository's build runs. Use when editing either file, when adding a section to it, or when the guard fails the build.
			---

			# CLAUDE.md

			`CLAUDE.md` carries this repository's agent instructions and is loaded into every
			Claude Code session, so it is the file to put a convention in — and the file to
			keep short. `AGENTS.md` beside it points back at `CLAUDE.md` for the agents that
			follow the cross-tool convention; edit `CLAUDE.md` and leave `AGENTS.md`
			pointing at it.

			## What the guard demands

			%1$s

			## Check it before pushing

			```sh
			%2$s
			```

			A `CLAUDE.md` that fails this fails the build, so run it after editing either
			file rather than finding out in CI.

			## Adding to it

			Put a new convention under the heading it belongs to. A section that grows past
			a screen belongs in a skill of its own under `%3$s`, which is loaded
			only when it is needed, rather than in the file every session pays for.
			""";

	private StarterSkills() {
	}

	/**
	 * The installers for the checkout in hand: the skill bodies name the detected
	 * build system and the command that runs its guard.
	 *
	 * @param buildSystem         the build system {@link EnforcerStep} wired the guard
	 *                            into, so the skills describe the guard the repository
	 *                            actually got
	 * @param repositoryDirectory the checkout, needed because the guard command depends
	 *                            on what it ships — a project carrying a build wrapper
	 *                            is built with that wrapper
	 */
	public static List<AssetInstaller> forCheckout(BuildSystem buildSystem, Path repositoryDirectory) {
		String guardCommand = guardCommand(buildSystem, repositoryDirectory);
		Set<String> claimed = claimedNames(repositoryDirectory);
		return List.of(
				skill(BUILD_SKILL,
						BUILD_SKILL_TEMPLATE.formatted(buildSystem.buildDescription(), guardCommand)),
				skill(CLAUDE_MD_SKILL, CLAUDE_MD_SKILL_TEMPLATE.formatted(demands(buildSystem), guardCommand,
						SKILLS_DIRECTORY)))
				.stream()
				.filter(installer -> isFree(installer, claimed))
				.toList();
	}

	private static AssetInstaller skill(String name, String content) {
		return new AssetInstaller(skillFile(name), content);
	}

	/**
	 * Whether the checkout still has room for this skill's name. A name already worn by
	 * one of the project's own commands, sub-agents or skills is left alone: the guard
	 * the adoption is about to verify fails a repository where two definitions claim
	 * one name, so installing over a taken name would break the very build the run
	 * exists to leave green — and it is the project's name, not the adoption's.
	 */
	private static boolean isFree(AssetInstaller installer, Set<String> claimed) {
		String name = nameOf(installer);
		if (claimed.contains(name)) {
			log.info("The project already has a Claude Code definition called {}; the starter skill is not"
					+ " installed", name);
			return false;
		}
		return true;
	}

	/** The skill's own name, read back from the path it installs to rather than passed twice. */
	private static String nameOf(AssetInstaller installer) {
		Path path = Path.of(installer.relativePath());
		return path.getParent().getFileName().toString();
	}

	/**
	 * Every name the checkout's Claude Code definitions already claim: a command's and
	 * a sub-agent's is its {@code *.md} file name, a skill's is its directory name.
	 * A directory the project does not carry claims nothing, which is the common case.
	 */
	static Set<String> claimedNames(Path repositoryDirectory) {
		Set<String> names = new HashSet<>(markdownNames(repositoryDirectory.resolve(COMMANDS_DIRECTORY)));
		names.addAll(markdownNames(repositoryDirectory.resolve(AGENTS_DIRECTORY)));
		names.addAll(subdirectoryNames(repositoryDirectory.resolve(SKILLS_DIRECTORY)));
		return names;
	}

	private static List<String> markdownNames(Path directory) {
		return childrenOf(directory).stream()
				.filter(File::isFile)
				.map(File::getName)
				.filter(name -> name.endsWith(MARKDOWN_SUFFIX))
				.map(name -> name.substring(0, name.length() - MARKDOWN_SUFFIX.length()))
				.toList();
	}

	private static List<String> subdirectoryNames(Path directory) {
		return childrenOf(directory).stream().filter(File::isDirectory).map(File::getName).toList();
	}

	/** {@link File#listFiles()} answers {@code null} for a directory that is not there. */
	private static List<File> childrenOf(Path directory) {
		File[] children = directory.toFile().listFiles();
		return children == null ? List.of() : List.of(children);
	}

	/** The checkout-relative path a named skill is written to. */
	static String skillFile(String name) {
		return SKILLS_DIRECTORY + "/" + name + "/" + SKILL_FILE;
	}

	/**
	 * What the wired guard holds {@code CLAUDE.md} to, in the words of the build system
	 * that wired it: a list of headings for the Maven rule that reads them, and the
	 * presence-and-non-empty check for the guards that ask for no section in particular.
	 * Listing the Maven headings in a Gradle project's skill would send a contributor
	 * appending sections nothing checks.
	 */
	private static String demands(BuildSystem buildSystem) {
		List<String> sections = buildSystem.requiredClaudeMdSections();
		if (sections.isEmpty()) {
			return "The guard asks for no section in particular: it fails the build when\n"
					+ "`CLAUDE.md` is missing or empty.";
		}
		return "The guard fails the build unless `CLAUDE.md` declares every one of these\nheadings:\n\n"
				+ sections.stream().map(section -> "- `" + section + "`").collect(Collectors.joining("\n"));
	}

	/**
	 * The guard command as a contributor would type it, which is not always as the
	 * adoption ran it: {@link BuildSystem#verifyCommand(Path)} answers a checkout's
	 * wrapper by absolute path, because {@link ProcessBuilder} resolves a relative
	 * program against the JVM's working directory rather than the command's. Writing
	 * that verbatim would commit the adoption host's workspace path into somebody
	 * else's repository, where it names a directory that never existed.
	 *
	 * @return the command, or a note in its place when the build system names none
	 */
	private static String guardCommand(BuildSystem buildSystem, Path repositoryDirectory) {
		List<String> command = buildSystem.verifyCommand(repositoryDirectory);
		if (command.isEmpty()) {
			return "# This build system runs the guard as part of the build; it has no command of its own.";
		}
		return command.stream()
				.map(word -> insideTheCheckout(word, repositoryDirectory))
				.collect(Collectors.joining(" "));
	}

	/**
	 * Rewrites a path inside the checkout as the relative one a contributor standing in
	 * it would use. A word carrying no separator cannot be such a path, and is answered
	 * without asking the filesystem to parse it — {@code -N} is a flag on every
	 * platform, but {@link Path#of} rejects some perfectly ordinary argument text on
	 * Windows.
	 */
	private static String insideTheCheckout(String word, Path repositoryDirectory) {
		if (word.indexOf('/') < 0 && word.indexOf('\\') < 0) {
			return word;
		}
		Path path = Path.of(word);
		if (!path.isAbsolute() || !path.startsWith(repositoryDirectory.toAbsolutePath())) {
			return word;
		}
		return "./" + repositoryDirectory.toAbsolutePath().relativize(path).toString().replace('\\', '/');
	}
}
