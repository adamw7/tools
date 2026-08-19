package io.github.adamw7.tools.adopt.step;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The build systems the adoption supports and the detection that picks the one
 * matching a checkout. The default set is tried in order, so a repository that
 * happens to carry more than one build file is adopted with the first-listed
 * build tool. The {@link FallbackBuildSystem} is listed last and matches every
 * checkout, so a repository with no recognised build file still gets a guard
 * rather than being adopted with none.
 */
public final class BuildSystems {

	/** The set every step falls back to, resolving the rule version of the running build. */
	public static final List<BuildSystem> DEFAULTS = defaults(Optional.empty());

	private BuildSystems() {
	}

	/**
	 * Maven first, then Gradle, then the catch-all fallback: the order the checkout
	 * is probed in. The fallback stays last so a real build tool is always preferred
	 * when its build file is present. Only Maven wires a versioned artifact into the
	 * adopted project, so it is the only one the rule version reaches.
	 *
	 * @param ruleVersion the released {@code claude-code-enforcer} version to pin, or
	 *                    empty to resolve the version of the {@code tools} build
	 *                    running the adoption
	 */
	public static List<BuildSystem> defaults(Optional<String> ruleVersion) {
		return defaults(GuardOptions.pinning(ruleVersion));
	}

	/**
	 * @param guard what the wired guard is made of: its rule set, its pinned rule
	 *              version, and the {@code CLAUDE.md} sections it demands. Only Maven
	 *              wires a versioned artifact into the adopted project, so it is the
	 *              only build system the guard options reach.
	 */
	public static List<BuildSystem> defaults(GuardOptions guard) {
		return List.of(new MavenBuildSystem(guard), new GradleBuildSystem(), new FallbackBuildSystem());
	}

	public static Optional<BuildSystem> detect(List<BuildSystem> candidates, Path repositoryDirectory) {
		return candidates.stream().filter(candidate -> candidate.matches(repositoryDirectory)).findFirst();
	}

	static String names(List<BuildSystem> candidates) {
		return candidates.stream().map(BuildSystem::name).collect(Collectors.joining("/"));
	}
}
