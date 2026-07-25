package io.github.adamw7.tools.adopt;

import java.nio.file.Path;

/**
 * Immutable inputs shared by every adoption step: the GitHub repository URL to
 * adopt, the workspace directory the clone is created under, the resulting
 * checkout directory, and the feature branch the adoption commits are made on.
 * The repository name is derived from the URL up front so the clone target is
 * known before the first step runs. The adoption never pushes to the default
 * branch: it works on {@code branchName} and opens a pull request from it.
 */
public final class AdoptionContext {

	/** Feature branch the adoption commits, pushes, and raises a pull request from. */
	public static final String DEFAULT_BRANCH = "claude/adopt-claude-code";

	private final String repositoryUrl;
	private final Path workspace;
	private final Path repositoryDirectory;
	private final String branchName;

	public AdoptionContext(String repositoryUrl, Path workspace) {
		this(repositoryUrl, workspace, DEFAULT_BRANCH);
	}

	public AdoptionContext(String repositoryUrl, Path workspace, String branchName) {
		this.repositoryUrl = requireText(repositoryUrl, "repositoryUrl");
		this.workspace = requireWorkspace(workspace);
		this.repositoryDirectory = workspace.resolve(repositoryName(this.repositoryUrl));
		this.branchName = requireText(branchName, "branchName");
	}

	public String repositoryUrl() {
		return repositoryUrl;
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

	private static String requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " must not be blank");
		}
		return value.strip();
	}

	private static Path requireWorkspace(Path workspace) {
		if (workspace == null) {
			throw new IllegalArgumentException("workspace must not be null");
		}
		return workspace;
	}

	private static String repositoryName(String repositoryUrl) {
		String withoutTrailingSlash = stripTrailingSlash(repositoryUrl);
		String lastSegment = withoutTrailingSlash.substring(withoutTrailingSlash.lastIndexOf('/') + 1);
		return requireName(stripGitSuffix(lastSegment), repositoryUrl);
	}

	/**
	 * A URL whose last segment is empty or a directory alias — {@code .../repo//},
	 * {@code .../.git}, or a bare {@code ..} — would otherwise resolve the checkout
	 * onto the workspace itself or above it, so the clone would land beside the
	 * other repositories the workspace holds instead of in its own directory.
	 * Rejecting it here fails the adoption on its input rather than several steps
	 * later on a checkout directory nobody intended.
	 */
	private static String requireName(String name, String repositoryUrl) {
		if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
			throw new IllegalArgumentException(
					"repositoryUrl must end in a repository name but was: " + repositoryUrl);
		}
		return name;
	}

	private static String stripTrailingSlash(String value) {
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}

	private static String stripGitSuffix(String segment) {
		return segment.endsWith(".git") ? segment.substring(0, segment.length() - ".git".length()) : segment;
	}
}
