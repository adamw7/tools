package io.github.adamw7.tools.enforcer.e2e;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Clones a real repository, which is the only way to hand the rules a project this
 * repository did not write. {@link FixtureProject} lays out every file the rules
 * read and lays them out correctly, so it can prove a rule binds and a violation is
 * reported — but every input it offers was written by someone who knew what the
 * rules expect. A repository from GitHub was not, and that is the whole point of
 * pointing the rules at one.
 * <p>
 * The clone is shallow and single-branch: only the working tree matters here, and a
 * full history would spend minutes fetching commits nothing reads. Nothing is ever
 * pushed, committed or written back — the checkout is read, and the enforcing build
 * runs beside it rather than in it — so these clones may be taken as often as the
 * integration suite likes.
 */
final class GitClone {

	/**
	 * Generous for repositories that are a megabyte or two, and short enough that a
	 * stalled network fails the test in minutes rather than leaving failsafe's own
	 * timeout to kill the fork and take the diagnosis with it.
	 */
	private static final long TIMEOUT_MINUTES = 3;

	private static final String GIT_SUFFIX = ".git";

	private static final LoggedProcess GIT = new LoggedProcess("enforcer-clone", TIMEOUT_MINUTES);

	private GitClone() {
	}

	/**
	 * Clones {@code url} into a directory of {@code workspace} named after the
	 * repository, and answers that directory. The workspace is created if it is not
	 * there, since it is what {@code git} is run from and a process cannot start in a
	 * directory that does not exist.
	 */
	static Path into(Path workspace, String url) {
		Path checkout = workspace.resolve(nameOf(url));
		createDirectory(workspace);
		run(workspace, List.of("git", "clone", "--depth", "1", "--single-branch", url, checkout.toString()));
		return checkout;
	}

	/**
	 * The repository's own name, which is what {@code git clone} would have called the
	 * directory anyway. It is spelled out here rather than left to git so a caller can
	 * name the checkout before the clone has run.
	 */
	static String nameOf(String url) {
		String path = url.endsWith(GIT_SUFFIX) ? url.substring(0, url.length() - GIT_SUFFIX.length()) : url;
		return path.substring(path.lastIndexOf('/') + 1);
	}

	/**
	 * What git said is read back only to explain a failure: a clone that worked has
	 * nothing to say, and one that did not is a broken test environment rather than a
	 * failed expectation, so it is reported as an {@link IllegalStateException}
	 * carrying that output.
	 */
	private static void run(Path directory, List<String> command) {
		BuildOutcome outcome = GIT.run(directory, command);
		if (!outcome.succeeded()) {
			throw new IllegalStateException(command + " failed: " + outcome.output());
		}
	}

	private static void createDirectory(Path directory) {
		try {
			Files.createDirectories(directory);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not create " + directory, e);
		}
	}
}
