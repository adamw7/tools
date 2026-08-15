package io.github.adamw7.tools.enforcer.e2e;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
	 * Output is merged into a temporary log and read back only to explain a failure:
	 * a clone that worked has nothing to say, and one that did not is a broken test
	 * environment rather than a failed expectation, so it is reported as an
	 * {@link IllegalStateException} carrying what git said.
	 */
	private static void run(Path directory, List<String> command) {
		Path log = temporaryFile();
		try {
			Process process = new ProcessBuilder(command)
					.directory(directory.toFile())
					.redirectErrorStream(true)
					.redirectOutput(log.toFile())
					.start();
			requireSucceeded(process, command, log);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not run " + command, e);
		} finally {
			delete(log);
		}
	}

	private static void requireSucceeded(Process process, List<String> command, Path log) {
		if (exitCodeOf(process, command) != 0) {
			throw new IllegalStateException(command + " failed: " + read(log));
		}
	}

	private static int exitCodeOf(Process process, List<String> command) {
		try {
			if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
				process.destroyForcibly();
				throw new IllegalStateException(command + " did not finish within " + TIMEOUT_MINUTES + " minutes");
			}
			return process.exitValue();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for " + command, e);
		}
	}

	private static String read(Path log) {
		try {
			return new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not read the clone log " + log, e);
		}
	}

	private static void createDirectory(Path directory) {
		try {
			Files.createDirectories(directory);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not create " + directory, e);
		}
	}

	private static Path temporaryFile() {
		try {
			return Files.createTempFile("enforcer-clone", ".log");
		} catch (IOException e) {
			throw new UncheckedIOException("Could not create a clone log", e);
		}
	}

	private static void delete(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not delete " + path, e);
		}
	}
}
