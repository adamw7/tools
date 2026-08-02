package io.github.adamw7.tools.adopt;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
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

	private static final Pattern SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://");
	private static final Pattern SEGMENT_SEPARATORS = Pattern.compile("[/:]");
	private static final int SEGMENTS_WITH_A_HOST = 3;
	private static final String GIT_SUFFIX = ".git";

	/**
	 * The port ending a URL's authority: a {@code ':'} followed by digits and then
	 * either the path or the end of the URL. It is dropped before the URL is split
	 * into segments, because splitting on {@code ':'} — which the scp-like form needs
	 * — otherwise reads the port as a path segment of its own. A
	 * {@code https://ghe.example.com:8443/owner/repo} then yielded the segments
	 * {@code [ghe.example.com, 8443, owner, repo]}, whose third-from-last is the port
	 * rather than the host, so {@link #repositorySlug} concluded the URL named no
	 * owner: {@code gh} lost its {@code --repo}, the toolchain check fell back to its
	 * weaker credentials probe, and the command line refused the URL outright.
	 *
	 * <p>Only a URL carrying a scheme is treated this way. The scp-like
	 * {@code git@host:owner/repo} has no scheme and cannot carry a port at all — that
	 * is what {@code ssh://} exists for — so its {@code ':'} always separates a path,
	 * even before an owner that happens to be all digits.
	 */
	private static final Pattern PORT = Pattern.compile("^([^/]*):\\d+(?=/|$)");

	/**
	 * The user information a URL carries before its host, ended by the last
	 * {@code @} that precedes the path — the same reading {@link Redaction} takes,
	 * but applied once the scheme is gone, so the scp-like {@code git@host:owner/repo}
	 * loses its {@code git@} too. It names no repository either way.
	 */
	private static final Pattern USER_INFO = Pattern.compile("^[^/]*@");

	private final String value;
	private final String redacted;
	private final String name;
	private final String slug;
	private final String identity;

	private RepositoryUrl(String value) {
		this.value = value;
		this.redacted = Redaction.of(value);
		this.name = repositoryName(value);
		this.slug = repositorySlug(value);
		this.identity = identity(value);
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

	/**
	 * Whether another clone URL names this same repository, comparing the two
	 * without the parts that vary between the forms one repository is cloned by:
	 * the scheme, the credentials, the {@code .git} suffix, a trailing slash, and
	 * letter case — and reading the scp-like form's {@code ':'} as the path
	 * separator it is. So {@code https://token@github.com/Octocat/Hello-World.git}
	 * and {@code git@github.com:octocat/hello-world} are one repository, while
	 * {@code .../alice/tools} and {@code .../bob/tools} are two.
	 *
	 * <p>Anything that does not reduce to the same text is answered as a different
	 * repository. The comparison is deliberately conservative in that direction,
	 * because the caller is deciding whether a checkout it found is the one it was
	 * asked to adopt: refusing a checkout that was in fact the right one costs a
	 * clone, while accepting the wrong one commits to it, pushes it, and opens its
	 * pull request.
	 *
	 * @param otherUrl the URL to compare against, typically a checkout's recorded
	 *                 {@code origin}; blank or {@code null} names no repository and
	 *                 so matches none
	 */
	public boolean isSameRepositoryAs(String otherUrl) {
		return identity.equals(identity(otherUrl));
	}

	/**
	 * A port is deliberately <em>not</em> dropped here, unlike in
	 * {@link #authorityAndPath}: two URLs differing only in their port name two
	 * servers, and answering them as one repository is the direction this comparison
	 * must never err in — the caller goes on to commit to that checkout, push it, and
	 * open its pull request. Reading the port as a path segment keeps them apart.
	 *
	 * @return the comparable form of a clone URL, or the empty string for text that
	 *         names no repository at all — which never equals the identity of a
	 *         parsed URL, since {@link #of} rejects a blank one
	 */
	private static String identity(String repositoryUrl) {
		if (!Text.isPresent(repositoryUrl)) {
			return "";
		}
		String withoutScheme = SCHEME.matcher(repositoryUrl.strip()).replaceFirst("");
		String path = USER_INFO.matcher(withoutScheme).replaceFirst("").replace(':', '/');
		return stripGitSuffix(stripTrailingSlash(path)).toLowerCase(Locale.ROOT);
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
		String withoutScheme = SCHEME.matcher(url).replaceFirst("");
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
		return Stream.of(SEGMENT_SEPARATORS.split(authorityAndPath(url)))
				.filter(segment -> !segment.isBlank())
				.toList();
	}

	/**
	 * The URL past its scheme, with any {@link #PORT} dropped, ready to be split on
	 * the two separators git accepts.
	 */
	private static String authorityAndPath(String url) {
		String withoutScheme = SCHEME.matcher(url).replaceFirst("");
		return hasScheme(url) ? PORT.matcher(withoutScheme).replaceFirst("$1") : withoutScheme;
	}

	private static boolean hasScheme(String url) {
		return SCHEME.matcher(url).find();
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
