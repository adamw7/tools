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
	private final String redacted;
	private final String name;
	private final String slug;

	private RepositoryUrl(String value) {
		this.value = value;
		this.redacted = Redaction.of(value);
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

	/**
	 * The URL as given, stripped of surrounding whitespace — the one {@code git}
	 * is handed, credentials included. Anything that outlives the run reports
	 * {@link #redacted()} instead.
	 */
	public String value() {
		return value;
	}

	/**
	 * @return the URL with any clone credentials masked, for the logs, failure
	 *         messages, and JSON report a secret must not reach
	 */
	public String redacted() {
		return redacted;
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
		String lastSegment = lastSegment(stripTrailingSlash(repositoryUrl));
		return requireName(stripGitSuffix(lastSegment), repositoryUrl);
	}

	/**
	 * The URL's final path segment, ended by either separator git accepts — so the
	 * scp-like {@code git@host:repo}, whose {@code ':'} is the path separator it is,
	 * names the checkout directory {@code repo} rather than {@code git@host:repo}.
	 * This is the same reading of {@code ':'} that {@link #repositorySlug} already
	 * takes; the scheme is dropped first so its own {@code "://"} is never mistaken
	 * for that separator.
	 */
	private static String lastSegment(String url) {
		String withoutScheme = url.replaceFirst(SCHEME, "");
		int separator = Math.max(withoutScheme.lastIndexOf('/'), withoutScheme.lastIndexOf(':'));
		return withoutScheme.substring(separator + 1);
	}

	/**
	 * A URL whose last segment is empty or a directory alias — {@code .../repo//},
	 * {@code .../.git}, or a bare {@code ..} — would resolve the checkout onto the
	 * workspace itself or above it, so the clone would land beside the other
	 * repositories the workspace holds. Rejecting it fails the adoption on its input
	 * rather than several steps later on a checkout directory nobody intended.
	 */
	private static String requireName(String name, String repositoryUrl) {
		if (name.isEmpty() || ".".equals(name) || "..".equals(name) || isPath(name)) {
			throw new IllegalArgumentException(
					"repositoryUrl must end in a repository name but was: " + Redaction.of(repositoryUrl));
		}
		return name;
	}

	/**
	 * A repository name never carries a backslash, so a last segment that does is a
	 * path rather than a name — a Windows-style {@code C:\repos\tools}, or a segment
	 * carrying a {@code ..} traversal. Resolving one against the workspace would put
	 * the checkout outside it on a platform that reads {@code '\'} as a separator, so
	 * it is refused for the same reason an empty or alias segment is.
	 */
	private static boolean isPath(String name) {
		return name.indexOf('\\') >= 0;
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

	/**
	 * The suffix is matched case-insensitively, because git clones
	 * {@code .../repo.GIT} as readily as {@code .../repo.git} and both name the one
	 * repository. Keeping the case would name the checkout {@code repo.GIT} and ask
	 * GitHub about {@code owner/repo.GIT}, which answers 404 and stops the adoption
	 * on its very first step.
	 */
	private static String stripGitSuffix(String segment) {
		int suffixStart = segment.length() - GIT_SUFFIX.length();
		return endsWithGitSuffix(segment, suffixStart) ? segment.substring(0, suffixStart) : segment;
	}

	private static boolean endsWithGitSuffix(String segment, int suffixStart) {
		return suffixStart >= 0 && segment.regionMatches(true, suffixStart, GIT_SUFFIX, 0, GIT_SUFFIX.length());
	}
}
