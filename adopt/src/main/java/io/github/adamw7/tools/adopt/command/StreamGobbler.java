package io.github.adamw7.tools.adopt.command;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
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
 * <p>The stream is copied verbatim as it is read — the child's own line
 * terminators and any trailing newline survive — so the transcript is what the
 * command printed rather than a re-joined approximation. {@link #output()} bounds
 * its wait for the reader thread: a direct child can exit while a descendant it
 * spawned keeps the pipe open, and the bounded join returns what was captured
 * instead of hanging the caller forever.
 */
final class StreamGobbler {

	/**
	 * How long {@link #output()} waits for the reader thread to reach
	 * end-of-stream. A live child's pipe reaches EOF the instant it is destroyed,
	 * so this is only ever spent when a surviving descendant holds the pipe open.
	 */
	static final Duration JOIN_TIMEOUT = Duration.ofSeconds(5);

	private final Thread thread;

	/** Written by the reader thread and read by {@link #output()}, so its buffer must be synchronized. */
	private final StringWriter output = new StringWriter();

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
	 *         for end-of-stream. Destroying the process closes the stream, so a
	 *         killed child returns promptly and a descendant holding the pipe open
	 *         only costs the bounded wait.
	 */
	String output() {
		join();
		return output.toString();
	}

	private void join() {
		try {
			thread.join(JOIN_TIMEOUT.toMillis());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void drain(InputStream stream) {
		try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			reader.transferTo(output);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
