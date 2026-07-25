package io.github.adamw7.tools.adopt;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A repository clone URL, parsed once into the two facts the adoption needs from
 * it: the repository name the checkout directory is created under, and the
 * {@code owner/repository} slug the steps name their target repository with. Both
 * are derived up front so a malformed URL fails on the adoption's input rather
 * than several steps later, and so {@link AdoptionContext} can stay the inputs the
 * pipeline shares instead of also being a git-URL parser.
 */
public final class RepositoryUrl {

	private static final String SCHEME = "^[a-zA-Z][a-zA-Z0-9+.-]*://";
	private static final String SEGMENT_SEPARATORS = "[/:]";
	private static final int SEGMENTS_WITH_A_HOST = 3;
	private static final String GIT_SUFFIX = ".git";

	private final String value;
	private final String name;
	private final String slug;

	private RepositoryUrl(String value) {
		this.value = value;
		this.name = repositoryName(value);
		this.slug = repositorySlug(value);
	}

	/**
	 * @param repositoryUrl the clone URL, which must not be blank
	 * @throws IllegalArgumentException when the URL is blank or names no repository
	 */
	public static RepositoryUrl of(String repositoryUrl) {
		return new RepositoryUrl(Text.required(repositoryUrl, "repositoryUrl"));
	}

	/** The URL as given, stripped of surrounding whitespace. */
	public String value() {
		return value;
	}

	/** The repository name the checkout directory is created under. */
	public String name() {
		return name;
	}

	/**
	 * @return the {@code owner/repository} the URL names, for the steps that tell a
	 *         tool which repository to act on rather than let it guess from the
	 *         checkout's git remote; empty when the URL names no owner
	 */
	public Optional<String> slug() {
		return Optional.ofNullable(slug);
	}

	private static String repositoryName(String repositoryUrl) {
		String withoutTrailingSlash = stripTrailingSlash(repositoryUrl);
		String lastSegment = withoutTrailingSlash.substring(withoutTrailingSlash.lastIndexOf('/') + 1);
		return requireName(stripGitSuffix(lastSegment), repositoryUrl);
	}

	/**
	 * A URL whose last segment is empty or a directory alias — {@code .../repo//},
	 * {@code .../.git}, or a bare {@code ..} — would resolve the checkout onto the
	 * workspace itself or above it, so the clone would land beside the other
	 * repositories the workspace holds. Rejecting it fails the adoption on its input
	 * rather than several steps later on a checkout directory nobody intended.
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
	 * and the scp-like {@code git@host:owner/repo} — hence splitting on both
	 * separators, so that form's {@code ':'} is the path separator it is.
	 *
	 * <p>A URL only names an owner when a host-looking segment precedes the last two.
	 * Without that check a plain filesystem path such as {@code /tmp/workspace/repo}
	 * would yield the nonsense slug {@code workspace/repo} and point a tool at a
	 * repository that does not exist; an empty result leaves the step to infer.
	 *
	 * @return the slug, or {@code null} when the URL names no owner
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
		return segment.endsWith(GIT_SUFFIX) ? segment.substring(0, segment.length() - GIT_SUFFIX.length()) : segment;
	}
}
