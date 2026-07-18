package io.github.adamw7.tools.adopt.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class StreamGobblerTest {

	@Test
	void joinsMultipleLinesWithTheLineSeparator() {
		String text = "first" + System.lineSeparator() + "second" + System.lineSeparator() + "third";
		StreamGobbler gobbler = StreamGobbler.consuming(
				new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
		assertEquals(text, gobbler.output());
	}

	@Test
	void keepsAnEmptyLeadingLine() {
		String text = System.lineSeparator() + "second";
		StreamGobbler gobbler = StreamGobbler.consuming(
				new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
		assertEquals(text, gobbler.output());
	}

	@Test
	void returnsEmptyForAStreamWithNoOutput() {
		StreamGobbler gobbler = StreamGobbler.consuming(new ByteArrayInputStream(new byte[0]));
		assertEquals("", gobbler.output());
	}

	/**
	 * A descendant of a destroyed child can keep the output pipe open, so the
	 * stream never reaches end-of-stream. {@link StreamGobbler#output()} must
	 * still return — with whatever it captured — rather than block forever. This
	 * deliberately waits out {@link StreamGobbler#JOIN_TIMEOUT}, so it opts out of
	 * the fast per-test timeout with its own generous bound.
	 */
	@Test
	@Timeout(30)
	void returnsCapturedOutputWhenTheStreamNeverEnds() throws IOException {
		PipedOutputStream sink = new PipedOutputStream();
		PipedInputStream source = new PipedInputStream(sink);
		sink.write(("partial" + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
		sink.flush();

		StreamGobbler gobbler = StreamGobbler.consuming(source);
		long startNanos = System.nanoTime();
		String output = gobbler.output();
		Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);

		assertTrue(output.contains("partial"), output);
		assertTrue(elapsed.compareTo(StreamGobbler.JOIN_TIMEOUT.plusSeconds(10)) < 0,
				"output() must return around the bounded join, not hang: " + elapsed);
		sink.close();
	}
}
