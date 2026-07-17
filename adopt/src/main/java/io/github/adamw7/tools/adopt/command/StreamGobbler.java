package io.github.adamw7.tools.adopt.command;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Drains a process's output stream on a dedicated daemon thread so the command
 * can be waited for with a timeout. Reading the stream inline would block until
 * the child closes it, which never happens for a hung process; consuming it in
 * the background lets {@link ProcessCommandRunner} time out and destroy the
 * child while its partial output is still recovered.
 */
final class StreamGobbler {

	private final Thread thread;
	private volatile String output = "";

	private StreamGobbler(InputStream stream) {
		this.thread = new Thread(() -> output = read(stream), "adopt-stream-gobbler");
		this.thread.setDaemon(true);
	}

	static StreamGobbler consuming(InputStream stream) {
		StreamGobbler gobbler = new StreamGobbler(stream);
		gobbler.thread.start();
		return gobbler;
	}

	/**
	 * @return everything the stream produced, blocking until it reaches
	 *         end-of-stream. Destroying the process closes the stream, so this
	 *         returns promptly once a timed-out child has been killed.
	 */
	String output() {
		join();
		return output;
	}

	private void join() {
		try {
			thread.join();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static String read(InputStream stream) {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			return reader.lines().collect(Collectors.joining(System.lineSeparator()));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
