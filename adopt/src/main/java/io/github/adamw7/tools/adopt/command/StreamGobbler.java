package io.github.adamw7.tools.adopt.command;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Drains a process's output stream on a dedicated daemon thread so the command can
 * be waited for with a timeout. Reading the stream inline would block until the
 * child closes it, which a hung process never does; consuming it in the background
 * lets {@link ProcessCommandRunner} destroy the child with its partial output
 * still recovered.
 *
 * <p>The stream is copied verbatim, so the transcript keeps the child's own line
 * terminators rather than a re-joined approximation — up to the bound
 * {@link BoundedTranscript} holds it to, past which the middle of a very long
 * transcript is dropped rather than the adoption growing with whatever the child
 * chose to print. {@link #output()} bounds its wait for the reader thread, because
 * a descendant the child spawned can keep the pipe open after the child itself has
 * exited.
 */
final class StreamGobbler {

	private static final Logger log = LogManager.getLogger(StreamGobbler.class);

	/**
	 * How long {@link #output()} waits for the reader thread to reach
	 * end-of-stream. A live child's pipe reaches EOF the instant it is destroyed,
	 * so this is only ever spent when a surviving descendant holds the pipe open.
	 */
	static final Duration JOIN_TIMEOUT = Duration.ofSeconds(5);

	/** How much is read from the stream at a time, before being appended to the transcript. */
	private static final int BUFFER_SIZE = 8 * 1024;

	private final Thread thread;

	/** Written by the reader thread and read by {@link #output()}, so its appends are synchronized. */
	private final BoundedTranscript output = new BoundedTranscript();

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
		return output.text();
	}

	private void join() {
		try {
			thread.join(JOIN_TIMEOUT.toMillis());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * A read that fails ends the transcript where it failed rather than propagating.
	 * This runs on a thread nobody joins for a result, so a thrown exception reaches
	 * the JVM's default handler and prints a stack trace to standard error — the one
	 * channel this module never logs on, and one the operator cannot attribute to a
	 * command. The partial output is what {@link #output()} answers either way, so
	 * the caller is no worse off for the failure being recorded here instead.
	 */
	private void drain(InputStream stream) {
		try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			transfer(reader);
		} catch (IOException e) {
			log.debug("Stopped reading the command's output before end-of-stream", e);
		}
	}

	/**
	 * Reads in fixed chunks rather than with {@link Reader#transferTo}, which would
	 * need a {@link java.io.Writer} to append to and so give up the bound
	 * {@link BoundedTranscript} exists to keep.
	 */
	private void transfer(Reader reader) throws IOException {
		char[] buffer = new char[BUFFER_SIZE];
		int read = reader.read(buffer);
		while (read >= 0) {
			output.append(buffer, read);
			read = reader.read(buffer);
		}
	}
}
