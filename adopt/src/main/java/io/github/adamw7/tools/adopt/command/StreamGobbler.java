package io.github.adamw7.tools.adopt.command;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Drains a process's output stream on a dedicated daemon thread so the command
 * can be waited for with a timeout. Reading the stream inline would block until
 * the child closes it, which never happens for a hung process; consuming it in
 * the background lets {@link ProcessCommandRunner} time out and destroy the
 * child while its partial output is still recovered.
 *
 * <p>Each line is accumulated as it is read, and {@link #output()} bounds its
 * wait for the reader thread. A direct child can exit while a descendant it
 * spawned keeps the output pipe open, in which case the stream never reaches
 * end-of-stream; the bounded join stops that from hanging the caller forever
 * and still returns whatever was captured before the wait elapsed.
 */
final class StreamGobbler {

	/**
	 * How long {@link #output()} waits for the reader thread to reach
	 * end-of-stream. A live child's pipe reaches EOF the instant it is destroyed,
	 * so this is only ever spent when a surviving descendant holds the pipe open.
	 */
	static final Duration JOIN_TIMEOUT = Duration.ofSeconds(5);

	private final Thread thread;
	private final StringBuilder output = new StringBuilder();
	private boolean firstLine = true;

	private StreamGobbler(InputStream stream) {
		this.thread = new Thread(() -> drain(stream), "adopt-stream-gobbler");
		this.thread.setDaemon(true);
	}

	static StreamGobbler consuming(InputStream stream) {
		StreamGobbler gobbler = new StreamGobbler(stream);
		gobbler.thread.start();
		return gobbler;
	}

	/**
	 * @return everything the stream produced, waiting up to {@link #JOIN_TIMEOUT}
	 *         for it to reach end-of-stream. Destroying the process closes the
	 *         stream, so this returns promptly once a timed-out child has been
	 *         killed; a descendant that keeps the pipe open only delays it by the
	 *         bounded wait rather than blocking forever.
	 */
	String output() {
		join();
		synchronized (output) {
			return output.toString();
		}
	}

	private void join() {
		try {
			thread.join(JOIN_TIMEOUT.toMillis());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void drain(InputStream stream) {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			append(reader);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void append(BufferedReader reader) throws IOException {
		String line = reader.readLine();
		while (line != null) {
			appendLine(line);
			line = reader.readLine();
		}
	}

	private void appendLine(String line) {
		synchronized (output) {
			if (!firstLine) {
				output.append(System.lineSeparator());
			}
			output.append(line);
			firstLine = false;
		}
	}
}
