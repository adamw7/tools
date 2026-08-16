package io.github.adamw7.tools.adopt.command;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionException;

/**
 * A {@link CommandRunner} that runs a command the network refused again, a bounded
 * number of times, waiting longer before each attempt. It decorates another runner
 * rather than extending it, so the retry is one seam every step already shells out
 * through and no step has to know it is there.
 *
 * <p>What this buys is a batch that survives its network. An adoption is a long
 * unattended run over a list of repositories, and a single {@code git clone} met by
 * a connection reset failed that repository outright — after the run had already
 * paid for its {@code claude init}, which is the expensive part. The repositories
 * behind it were never in doubt ({@link io.github.adamw7.tools.adopt.BatchAdoption}
 * isolates them), but the one that was refused had to be adopted again from the
 * beginning, by an operator who had to notice first.
 *
 * <p>Only the failures {@link TransientFailures} recognises are retried: a
 * {@code git} or {@code gh} that reports a transport-level refusal. Everything else
 * — a rejected push, a 403, a query answering through its exit code — is handed
 * back on the first attempt exactly as an undecorated runner hands it back. A
 * command that <em>throws</em> is not retried either: the two failures
 * {@link ProcessCommandRunner} raises are a program that could not be started and
 * one destroyed on its timeout, and re-running either buys nothing but another
 * timeout's wait.
 *
 * <p>A runner configured with no retries is a pass-through, so the entry points can
 * always wrap and let {@code --retries 0} answer for itself.
 */
public class RetryingCommandRunner implements CommandRunner {

	/**
	 * How a run waits between attempts. Injected so a test can assert the backoff it
	 * would have served instead of serving it, which is what keeps these tests inside
	 * the module's five-second timeout.
	 */
	public interface Pause {

		void of(Duration duration);
	}

	private static final Logger log = LogManager.getLogger(RetryingCommandRunner.class);

	/**
	 * The wait before the second attempt, doubling before each one after it. Sized for
	 * the failure being waited out — a proxy hiccup, a load balancer restarting, a DNS
	 * lookup that missed — rather than for an outage, which no bounded retry recovers
	 * from and which the operator has to be told about instead.
	 */
	static final Duration FIRST_BACKOFF = Duration.ofSeconds(2);

	/** The wait the doubling stops at, so a generous {@code --retries} cannot idle a run for minutes. */
	static final Duration MAX_BACKOFF = Duration.ofSeconds(30);

	private final CommandRunner delegate;
	private final int retries;
	private final Pause pause;

	/**
	 * @param delegate the runner that actually runs the command
	 * @param retries  how many further attempts a transient failure earns, zero
	 *                 leaving the delegate's answer untouched
	 */
	public RetryingCommandRunner(CommandRunner delegate, int retries) {
		this(delegate, retries, RetryingCommandRunner::sleep);
	}

	/** The seam a test drives, with a pause that records rather than waits. */
	RetryingCommandRunner(CommandRunner delegate, int retries, Pause pause) {
		this.delegate = delegate;
		this.retries = requireNotNegative(retries);
		this.pause = pause;
	}

	private static int requireNotNegative(int retries) {
		if (retries < 0) {
			throw new IllegalArgumentException("retries must not be negative but was " + retries);
		}
		return retries;
	}

	/**
	 * How many further attempts a refused command earns here. Package-visible so
	 * {@link CommandRunners} can be asserted to have carried the run's own
	 * {@code --retries} into the decorator that serves them: a run wired with fewer
	 * than were asked for behaves exactly like one whose network faltered a little
	 * more, and neither this runner nor the step above it reports the difference.
	 */
	int retries() {
		return retries;
	}

	/**
	 * The runner this one decorates. Package-visible so the toolchain
	 * {@link CommandRunners} assembles can be asserted to still have both halves.
	 * Losing the decorated half is the failure that shows least: an undecorated
	 * runner answers every command exactly as this one answers a command the network
	 * did not refuse, so a run only differs on the transport failure this class
	 * exists to absorb — by which point the batch has already failed the repository.
	 */
	CommandRunner delegate() {
		return delegate;
	}

	@Override
	public CommandResult run(Path workingDirectory, List<String> command) {
		CommandResult result = delegate.run(workingDirectory, command);
		int attempts = 1;
		while (attempts <= retries && TransientFailures.worthRetrying(result)) {
			waitBefore(attempts, result);
			result = delegate.run(workingDirectory, command);
			attempts++;
		}
		return reported(result, attempts);
	}

	/**
	 * The transcript of the attempt being retried is logged here because nothing else
	 * will: a step only reports the command that stopped it, so an attempt this runner
	 * goes on to replace with a successful one would otherwise leave no trace of the
	 * network having faltered at all. It is redacted for the usual reason — a tool
	 * handed a credentialled clone URL echoes it back when it cannot use it.
	 */
	private void waitBefore(int attempt, CommandResult result) {
		Duration backoff = backoff(attempt);
		log.warn("{} failed (exit {}) with what reads as a transient network failure; retrying in {}s ({} of {}): {}",
				result.describe(), result.exitCode(), backoff.toSeconds(), attempt, retries,
				result.redactedOutput().strip());
		pause.of(backoff);
	}

	/**
	 * A run that needed more than one attempt says so even when it ended well, so the
	 * cost of a flaky network is readable in the log of a batch that landed rather
	 * than only in the one that did not.
	 */
	private CommandResult reported(CommandResult result, int attempts) {
		if (attempts > 1 && result.succeeded()) {
			log.info("{} succeeded on attempt {} of {}", result.describe(), attempts, retries + 1);
		}
		return result;
	}

	private Duration backoff(int attempt) {
		Duration doubled = FIRST_BACKOFF.multipliedBy(1L << (attempt - 1));
		return doubled.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : doubled;
	}

	/**
	 * The pause a real run serves. An interrupt aborts the adoption rather than being
	 * swallowed into an immediate retry, and leaves the flag set, so a host shutting
	 * its worker down — an MCP server, a cancelled CI job — is obeyed here as it is
	 * inside {@link ProcessCommandRunner}.
	 */
	private static void sleep(Duration backoff) {
		try {
			Thread.sleep(backoff.toMillis());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AdoptionException("Interrupted while waiting to retry a command the network refused", e);
		}
	}
}
