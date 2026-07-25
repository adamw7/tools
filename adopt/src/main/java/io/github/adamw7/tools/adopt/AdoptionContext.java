package io.github.adamw7.tools.adopt;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

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

	private static final String SCHEME = "^[a-zA-Z][a-zA-Z0-9+.-]*://";
	private static final String SEGMENT_SEPARATORS = "[/:]";
	private static final int SEGMENTS_WITH_A_HOST = 3;

	private final String repositoryUrl;
	private final Path workspace;
	private final Path repositoryDirectory;
	private final String branchName;
	private final String repositorySlug;

	public AdoptionContext(String repositoryUrl, Path workspace) {
		this(repositoryUrl, workspace, DEFAULT_BRANCH);
	}

	public AdoptionContext(String repositoryUrl, Path workspace, String branchName) {
		this.repositoryUrl = Text.required(repositoryUrl, "repositoryUrl");
		this.workspace = requireWorkspace(workspace);
		this.repositoryDirectory = workspace.resolve(repositoryName(this.repositoryUrl));
		this.branchName = Text.required(branchName, "branchName");
		this.repositorySlug = repositorySlug(this.repositoryUrl);
	}

	public String repositoryUrl() {
		return repositoryUrl;
	}

	/**
	 * @return the {@code owner/repository} the URL names, for the steps that must
	 *         tell a tool which repository to act on rather than let it guess from
	 *         the checkout's git remote; empty when the URL carries no host and so
	 *         names no owner
	 */
	public Optional<String> repositorySlug() {
		return Optional.ofNullable(repositorySlug);
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

	/**
	 * Derives {@code owner/repository} from the clone URL, covering the forms git
	 * accepts: {@code https://host/owner/repo.git}, {@code ssh://git@host/owner/repo},
	 * and the scp-like {@code git@host:owner/repo}. The scheme is dropped and the
	 * remainder split on both separators, so the scp-like form's {@code ':'} is
	 * treated as the path separator it is.
	 *
	 * <p>A URL is only read as naming an owner when a host segment precedes the last
	 * two — the segment before the owner must look like a hostname. Without that
	 * check a plain filesystem path such as {@code /tmp/workspace/repo} would yield
	 * the nonsense slug {@code workspace/repo}, and a step would confidently point a
	 * tool at a repository that does not exist; an empty result instead leaves the
	 * step to fall back to its own inference.
	 */
	private static String repositorySlug(String repositoryUrl) {
		List<String> segments = segments(stripGitSuffix(stripTrailingSlash(repositoryUrl)));
		if (segments.size() < SEGMENTS_WITH_A_HOST || !isHost(segments.get(segments.size() - 3))) {
			return null;
		}
		return segments.get(segments.size() - 2) + "/" + segments.get(segments.size() - 1);
	}

	private static List<String> segments(String url) {
		return Stream.of(url.replaceFirst(SCHEME, "").split(SEGMENT_SEPARATORS))
				.filter(segment -> !segment.isBlank())
				.toList();
	}

	private static boolean isHost(String segment) {
		return segment.contains(".");
	}

	private static String stripTrailingSlash(String value) {
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}

	private static String stripGitSuffix(String segment) {
		return segment.endsWith(".git") ? segment.substring(0, segment.length() - ".git".length()) : segment;
	}
}
