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
		this.repositoryUrl = requireUrl(repositoryUrl);
		this.workspace = requireWorkspace(workspace);
		this.repositoryDirectory = workspace.resolve(repositoryName(this.repositoryUrl));
		this.branchName = requireBranch(branchName);
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

	private static String requireUrl(String repositoryUrl) {
		if (repositoryUrl == null || repositoryUrl.isBlank()) {
			throw new IllegalArgumentException("repositoryUrl must not be blank");
		}
		return repositoryUrl.strip();
	}

	private static Path requireWorkspace(Path workspace) {
		if (workspace == null) {
			throw new IllegalArgumentException("workspace must not be null");
		}
		return workspace;
	}

	private static String requireBranch(String branchName) {
		if (branchName == null || branchName.isBlank()) {
			throw new IllegalArgumentException("branchName must not be blank");
		}
		return branchName.strip();
	}

	private static String repositoryName(String repositoryUrl) {
		String withoutTrailingSlash = stripTrailingSlash(repositoryUrl);
		String lastSegment = withoutTrailingSlash.substring(withoutTrailingSlash.lastIndexOf('/') + 1);
		return stripGitSuffix(lastSegment);
	}

	private static String stripTrailingSlash(String value) {
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}

	private static String stripGitSuffix(String segment) {
		return segment.endsWith(".git") ? segment.substring(0, segment.length() - ".git".length()) : segment;
	}
}
