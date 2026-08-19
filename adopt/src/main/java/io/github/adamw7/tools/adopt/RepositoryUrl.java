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
	 * The port ending a URL's authority, dropped before the URL is split into
	 * segments: splitting on {@code ':'} — which the scp-like form needs — otherwise
	 * reads the port as a path segment, so {@code https://ghe.example.com:8443/owner/repo}
	 * gave {@link #repositorySlug} the port where the host should be and it concluded
	 * the URL named no owner. Only a URL carrying a scheme is treated this way: the
	 * scp-like {@code git@host:owner/repo} cannot carry a port, so its {@code ':'}
	 * always separates a path.
	 */
	private static final Pattern PORT = Pattern.compile("^([^/]*):\\d+(?=/|$)");

	/**
	 * The user information a URL carries before its host, ended by the last
	 * {@code @} that precedes the path — the same reading {@link Redaction} takes,
	 * but applied once the scheme is gone, so the scp-like {@code git@host:owner/repo}
	 * loses its {@code git@} too. It names no repository either way.
	 */
	private static final Pattern USER_INFO = Pattern.compile("^[^/]*@");

	/**
	 * The credentials of an {@code http(s)} URL: everything between the {@code ://}
	 * and the last {@code @} before the path, the reading {@link Redaction} takes.
	 * Only those two schemes match, because {@link #withoutCredentials()} removes what
	 * it finds rather than masking it — the {@code git@} of {@code ssh://git@host/...}
	 * is the account to log in as, not a secret.
	 *
	 * <p>The scheme is matched without regard to case, git cloning
	 * {@code HTTPS://token@host/owner/repo} as readily as the lower-case form. Reading
	 * only lower case left the URL unchanged, so {@code CloneStep} saw nothing to
	 * rewrite and the token stayed in {@code .git/config} — while {@link Redaction},
	 * which matches any scheme, masked it everywhere the run <em>reported</em> it.
	 */
	private static final Pattern HTTP_CREDENTIALS = Pattern.compile("(?<=^https?://)[^/\\s]+@",
			Pattern.CASE_INSENSITIVE);

	private final String value;
	private final String redacted;
	private final String withoutCredentials;
	private final String name;
	private final String slug;
	private final String identity;

	private RepositoryUrl(String value) {
		this.value = value;
		this.redacted = Redaction.of(value);
		this.withoutCredentials = HTTP_CREDENTIALS.matcher(value).replaceFirst("");
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
	 * The URL as given, stripped of surrounding whitespace — the one {@code git} is
	 * handed, credentials included. Anything outliving the run reports
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

	/**
	 * The URL with its credentials removed rather than masked, so it still clones and
	 * fetches: {@link #redacted()} answers {@code https://***@host/owner/repo}, which
	 * names a host called {@code ***@host} and is no use to git. This is the form the
	 * checkout records as its {@code origin}, a credentialled URL written there
	 * outliving the run as plaintext in {@code .git/config}.
	 *
	 * @return the URL without its user information for {@code http} and {@code https},
	 *         and unchanged for every other form
	 */
	public String withoutCredentials() {
		return withoutCredentials;
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
	 * repository. The comparison errs that way deliberately: the caller is deciding
	 * whether a checkout it found is the one it was asked to adopt, and refusing the
	 * right one costs a clone while accepting the wrong one commits to it, pushes it,
	 * and opens its pull request.
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
	 * must never err in.
	 *
	 * @return the comparable form of a clone URL, or the empty string for text that
	 *         names no repository at all — which never equals the identity of a
	 *         parsed URL, since {@link #of} rejects a blank one
	 */
	private static String identity(String repositoryUrl) {
		if (!Text.isPresent(repositoryUrl)) {
			return "";
		}
		String path = USER_INFO.matcher(withoutScheme(repositoryUrl.strip())).replaceFirst("").replace(':', '/');
		return stripGitSuffix(stripTrailingSlash(path)).toLowerCase(Locale.ROOT);
	}

	private static String repositoryName(String repositoryUrl) {
		String lastSegment = lastSegment(stripTrailingSlash(repositoryUrl));
		return requireName(stripGitSuffix(lastSegment), repositoryUrl);
	}

	/**
	 * The URL's final path segment, ended by either separator git accepts — so the
	 * scp-like {@code git@host:repo} names the checkout directory {@code repo} rather
	 * than {@code git@host:repo}. The scheme is dropped first so its own {@code "://"}
	 * is never mistaken for that separator.
	 */
	private static String lastSegment(String url) {
		String path = withoutScheme(url);
		int separator = Math.max(path.lastIndexOf('/'), path.lastIndexOf(':'));
		return path.substring(separator + 1);
	}

	/**
	 * A URL whose last segment is empty or a directory alias — {@code .../repo//},
	 * {@code .../.git}, a bare {@code ..} — would resolve the checkout onto the
	 * workspace itself or above it.
	 */
	private static String requireName(String name, String repositoryUrl) {
		if (name.isEmpty() || ".".equals(name) || "..".equals(name) || isPath(name) || isUserInfo(name)) {
			throw new IllegalArgumentException(
					"repositoryUrl must end in a repository name but was: " + Redaction.of(repositoryUrl));
		}
		return name;
	}

	/**
	 * A last segment ending at an {@code @} is the URL's user information and nothing
	 * else — {@code https://token@} names credentials and no repository. Only a URL
	 * with no path past its authority reaches this. It protects {@link #identity},
	 * which strips exactly that user information: such a URL reduced to the empty
	 * identity {@link #isSameRepositoryAs} answers for text naming no repository, so a
	 * checkout of <em>any</em> other repository was accepted as this one.
	 */
	private static boolean isUserInfo(String name) {
		return name.endsWith("@");
	}

	/**
	 * A repository name never carries a backslash, so a last segment that does is a
	 * path — a Windows-style {@code C:\repos\tools}, or a {@code ..} traversal —
	 * which would put the checkout outside the workspace on such a platform.
	 */
	private static boolean isPath(String name) {
		return name.indexOf('\\') >= 0;
	}

	/**
	 * Derives {@code owner/repository} from the clone URL, covering the forms git
	 * accepts: {@code https://host/owner/repo.git}, {@code ssh://git@host/owner/repo},
	 * and the scp-like {@code git@host:owner/repo} — hence splitting on both
	 * separators.
	 *
	 * <p>A URL only names an owner when a host-looking segment precedes the last two:
	 * without that, a filesystem path such as {@code /tmp/workspace/repo} yields the
	 * nonsense slug {@code workspace/repo}. An empty result leaves the step to infer.
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
		String path = withoutScheme(url);
		return path.equals(url) ? path : PORT.matcher(path).replaceFirst("$1");
	}

	/**
	 * The URL past its {@link #SCHEME}, or the URL itself when it carries none — which
	 * is how {@link #authorityAndPath} tells the two apart, since only a URL with a
	 * scheme may carry a port.
	 */
	private static String withoutScheme(String url) {
		return SCHEME.matcher(url).replaceFirst("");
	}

	private static boolean isHost(String segment) {
		return segment.contains(".");
	}

	private static String stripTrailingSlash(String value) {
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}

	/**
	 * Matched case-insensitively: git clones {@code .../repo.GIT} as readily as
	 * {@code .../repo.git}, and keeping the case asked GitHub about
	 * {@code owner/repo.GIT}, which answers 404.
	 */
	private static String stripGitSuffix(String segment) {
		int suffixStart = segment.length() - GIT_SUFFIX.length();
		return endsWithGitSuffix(segment, suffixStart) ? segment.substring(0, suffixStart) : segment;
	}

	private static boolean endsWithGitSuffix(String segment, int suffixStart) {
		return suffixStart >= 0 && segment.regionMatches(true, suffixStart, GIT_SUFFIX, 0, GIT_SUFFIX.length());
	}
}
