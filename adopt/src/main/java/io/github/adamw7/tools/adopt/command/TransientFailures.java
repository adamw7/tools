package io.github.adamw7.tools.adopt.command;

import java.util.List;
import java.util.Locale;

/**
 * Decides whether a failed command is worth running again: one the network
 * refused, rather than one the repository, the remote, or the operator's
 * credentials refused. {@link RetryingCommandRunner} asks this and nothing else,
 * so what an adoption retries is stated once, in the vocabulary of the tools it
 * drives.
 *
 * <p>Two conditions have to hold, and both are narrowing.
 *
 * <p>First, the program must be {@code git} or {@code gh}. They are the only
 * commands the pipeline runs whose work <em>is</em> the network, and both are
 * re-runnable: a {@code git clone} that fails removes the directory it created, a
 * {@code git fetch} and a {@code git push} are idempotent, and a repeated
 * {@code gh pr create} is what {@link io.github.adamw7.tools.adopt.step.PullRequestStep}
 * already tolerates. The two commands left out are deliberate. A
 * {@code claude init} reaches the network too, but it is the run's most expensive
 * command and its transcript is a model's prose — text that may discuss a
 * connection reset without one having happened. A build tool's transcript carries
 * whatever the project's own tests print, for the same reason.
 *
 * <p>Second, the transcript must report a transport-level refusal in one of the
 * tools' own words. Everything else fails once, as it does today: an
 * authentication failure, a 403 or a 404, a rejected non-fast-forward push, and a
 * query answering "no" through its exit code all pass through untouched — the last
 * of which matters most, since {@link io.github.adamw7.tools.adopt.step.BranchStep}
 * and {@link io.github.adamw7.tools.adopt.step.CommitStep} ask git questions whose
 * answer is a non-zero exit.
 *
 * <p>A rate limit is not in the list. GitHub answers a secondary rate limit by
 * asking for a wait measured in minutes, and retrying it seconds later is what the
 * limit exists to stop — so it is reported to the operator rather than hammered.
 */
public final class TransientFailures {

	/**
	 * The programs whose failures may be retried, matched against the command as the
	 * step wrote it. A step names its program plainly and
	 * {@link ExecutableResolver} expands it to a path afterwards, so this compares
	 * what the pipeline asked for rather than what the platform resolved it to.
	 */
	private static final List<String> NETWORK_TOOLS = List.of("git", "gh");

	/**
	 * Fragments of {@code git}'s and {@code gh}'s own wording for a network that did
	 * not answer, matched case-insensitively against the merged transcript. The
	 * server-side entries stop at the {@code 5} of a status code on purpose: a 5xx is
	 * the remote failing and worth another attempt, while the 4xx that shares the
	 * sentence — {@code The requested URL returned error: 403} — is the adoption being
	 * told no, and retrying it only delays the report.
	 */
	private static final List<String> TRANSIENT_WORDING = List.of(
			"could not resolve host",
			"temporary failure in name resolution",
			"no such host",
			"failed to connect to",
			"connection refused",
			"connection reset by peer",
			"connection timed out",
			"operation timed out",
			"timeout was reached",
			"i/o timeout",
			"network is unreachable",
			"the remote end hung up unexpectedly",
			"unexpected disconnect",
			"early eof",
			"rpc failed",
			"gnutls recv error",
			"ssl connect error",
			"ssl_error_syscall",
			"tls handshake timeout",
			"the requested url returned error: 5",
			"server error: 5",
			"internal server error",
			"bad gateway",
			"service unavailable",
			"gateway timeout");

	private TransientFailures() {
	}

	/**
	 * @param result the outcome of one attempt
	 * @return whether that attempt failed in a way another one could survive
	 */
	public static boolean worthRetrying(CommandResult result) {
		return !result.succeeded() && isNetworkTool(result.command()) && reportsATransientFailure(result.output());
	}

	private static boolean isNetworkTool(List<String> command) {
		return !command.isEmpty() && NETWORK_TOOLS.stream().anyMatch(tool -> tool.equalsIgnoreCase(command.get(0)));
	}

	private static boolean reportsATransientFailure(String output) {
		String transcript = output.toLowerCase(Locale.ROOT);
		return TRANSIENT_WORDING.stream().anyMatch(transcript::contains);
	}
}
