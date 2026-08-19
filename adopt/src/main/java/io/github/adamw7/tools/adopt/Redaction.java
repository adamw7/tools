package io.github.adamw7.tools.adopt;

import java.util.regex.Pattern;

/**
 * Masks the credentials a clone URL can carry before the text reaches somewhere it
 * outlives the run. An adoption driven by CI is handed
 * {@code https://x-access-token:TOKEN@github.com/owner/repo.git}, and puts that URL
 * in three places a secret must not reach: the log, a failing command's
 * {@link AdoptionException}, and the JSON report's {@code failure} field.
 *
 * <p>Only the user information of a URL with a scheme is masked, so the scp-like
 * {@code git@host:owner/repo} — whose {@code git@} is a well-known user, not a
 * credential — reads as it was given.
 */
public final class Redaction {

	private static final String MASK = "***";

	/**
	 * The user information between a URL's {@code ://} and its {@code @}. The match
	 * runs to the <em>last</em> {@code @} before the path, because that is where git
	 * itself ends the user information: a password carrying an unencoded {@code @} —
	 * {@code https://user:p@ss@host/owner/repo.git} — is one credential, not a
	 * credential followed by a host. Stopping at the first {@code @} left the rest of
	 * the password in the text this class exists to keep it out of.
	 */
	private static final Pattern CREDENTIALS = Pattern.compile("(?<=://)[^/\\s]+(?=@)");

	private Redaction() {
	}

	/**
	 * @return the text with every URL's credentials replaced by {@value #MASK},
	 *         or the text unchanged when it carries none. A {@code null} text is
	 *         answered with {@code null}, so a caller passing a command's output
	 *         through does not have to null-check first.
	 */
	public static String of(String text) {
		return text == null ? null : CREDENTIALS.matcher(text).replaceAll(MASK);
	}
}
