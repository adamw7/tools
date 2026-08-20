package io.github.adamw7.tools.secret;

import java.util.regex.Pattern;

/**
 * Masks the credentials a URL can carry before the text reaches somewhere it outlives
 * the request that carried it. An adoption driven by CI is handed
 * {@code https://x-access-token:TOKEN@github.com/owner/repo.git}, and puts that URL in
 * three places a secret must not reach: the log, a failing command's
 * {@code AdoptionException}, and the JSON report's {@code failure} field. The MCP
 * servers are handed the same URL as a tool argument, and a JDBC URL carries a
 * password the same way.
 *
 * <p>It lives in {@code mcp-common} because that is the lowest module both users
 * share — the adoption pipeline and the shared MCP failure path — and one masking
 * rule is the point: a second implementation is a second reading of where a
 * credential ends, and the half that reads it differently is the half that logs the
 * token. It is deliberately outside {@code io.github.adamw7.tools.mcp}, since masking
 * a secret is not MCP scaffolding and the adoption's own layering rule keeps that
 * scaffolding to its delivery package.
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
