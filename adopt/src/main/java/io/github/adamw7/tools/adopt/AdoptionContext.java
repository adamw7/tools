package io.github.adamw7.tools.adopt;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Immutable inputs shared by every adoption step: the GitHub repository URL to
 * adopt, the workspace directory the clone is created under, the resulting
 * checkout directory, and the feature branch the adoption commits are made on.
 * The URL is parsed by {@link RepositoryUrl} up front so the clone target is
 * known before the first step runs. The adoption never pushes to the default
 * branch: it works on {@code branchName} and opens a pull request from it.
 */
public final class AdoptionContext {

	/** Feature branch the adoption commits, pushes, and raises a pull request from. */
	public static final String DEFAULT_BRANCH = "claude/adopt-claude-code";

	/**
	 * The remote the adoption fetches from, starts an already-published branch at,
	 * and pushes to. Named once here because the clone, the branch, and the push must
	 * agree on it: a branch started from one remote and pushed to another is rejected
	 * as a non-fast-forward.
	 */
	public static final String REMOTE = "origin";

	private final RepositoryUrl repository;
	private final Path workspace;
	private final Path repositoryDirectory;
	private final String branchName;

	public AdoptionContext(String repositoryUrl, Path workspace) {
		this(repositoryUrl, workspace, DEFAULT_BRANCH);
	}

	public AdoptionContext(String repositoryUrl, Path workspace, String branchName) {
		this.repository = RepositoryUrl.of(repositoryUrl);
		this.workspace = requireWorkspace(workspace);
		this.repositoryDirectory = workspace.resolve(repository.name());
		this.branchName = Text.required(branchName, "branchName");
	}

	/** The clone URL as given, credentials included, for the commands that need it. */
	public String repositoryUrl() {
		return repository.value();
	}

	/** @see RepositoryUrl#redacted() */
	public String displayUrl() {
		return repository.redacted();
	}

	/** @see RepositoryUrl#slug() */
	public Optional<String> repositorySlug() {
		return repository.slug();
	}

	/**
	 * Asks the question a step has about a checkout it found — is this the
	 * repository I was asked to adopt? — without handing it the credentialled clone
	 * URL to compare for itself.
	 *
	 * @see RepositoryUrl#isSameRepositoryAs(String)
	 */
	public boolean isSameRepository(String otherUrl) {
		return repository.isSameRepositoryAs(otherUrl);
	}

	public Path workspace() {
		return workspace;
	}

	public Path repositoryDirectory() {
		return repositoryDirectory;
	}

	public String branchName() {
		return branchName;
	}

	/**
	 * @return the branch to adopt on: the one the caller named, or
	 *         {@link #DEFAULT_BRANCH} when none was. A blank name counts as none, so
	 *         an omitted command-line positional and an empty MCP argument both fall
	 *         back to the default instead of being rejected as an invalid branch.
	 */
	public static String branchOrDefault(String branchName) {
		return Text.isPresent(branchName) ? branchName.strip() : DEFAULT_BRANCH;
	}

	private static Path requireWorkspace(Path workspace) {
		if (workspace == null) {
			throw new IllegalArgumentException("workspace must not be null");
		}
		return workspace;
	}
}
