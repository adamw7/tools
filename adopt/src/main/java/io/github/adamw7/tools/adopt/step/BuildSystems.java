package io.github.adamw7.tools.adopt.step;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The build systems the adoption supports and the detection that picks the one
 * matching a checkout. The default set is tried in order, so a repository that
 * happens to carry more than one build file is adopted with the first-listed
 * build tool. The {@link FallbackBuildSystem} is listed last and matches every
 * checkout, so a repository with no recognised build file still gets a guard
 * rather than being adopted with none.
 */
public final class BuildSystems {

	/**
	 * Maven first, then Gradle, then the catch-all fallback: the order the checkout
	 * is probed in. The fallback stays last so a real build tool is always preferred
	 * when its build file is present.
	 */
	public static final List<BuildSystem> DEFAULTS = List.of(new MavenBuildSystem(), new GradleBuildSystem(),
			new FallbackBuildSystem());

	private BuildSystems() {
	}

	public static Optional<BuildSystem> detect(List<BuildSystem> candidates, Path repositoryDirectory) {
		return candidates.stream().filter(candidate -> candidate.matches(repositoryDirectory)).findFirst();
	}

	static String names(List<BuildSystem> candidates) {
		return candidates.stream().map(BuildSystem::name).reduce((a, b) -> a + "/" + b).orElse("");
	}
}
