package io.github.adamw7.tools.adopt.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.adopt.Redaction;

class BoundedTranscriptTest {

	private static final String CREDENTIALLED_URL = "https://x-access-token:SECRET@github.com/owner/repo.git";

	@Test
	void returnsEmptyWhenNothingWasAppended() {
		assertEquals("", new BoundedTranscript().text());
	}

	/**
	 * The common case must stay byte-for-byte what the child printed, terminators and
	 * trailing newline included: a transcript that fits is not a transcript that was
	 * reassembled.
	 */
	@Test
	void returnsShortOutputVerbatim() {
		String text = "first\r\nsecond\nthird\n";
		assertEquals(text, transcriptOf(new BoundedTranscript(), text));
	}

	@Test
	void keepsAppendingAcrossSeveralChunks() {
		BoundedTranscript transcript = new BoundedTranscript();
		append(transcript, "first\n");
		append(transcript, "second\n");
		assertEquals("first\nsecond\n", transcript.text());
	}

	/**
	 * The bound has to hold however the stream is read: a caller appending the whole
	 * output at once must be cut just as one appending it chunk by chunk is.
	 */
	@Test
	void keepsTheBeginningAndTheEndWhenTheOutputArrivesInChunks() {
		BoundedTranscript transcript = new BoundedTranscript(16, 16);
		append(transcript, "head\n");
		appendRepeatedly(transcript, "filler\n", 4000);
		append(transcript, "tail\n");

		String text = transcript.text();
		assertTrue(text.startsWith("head\n"), text);
		assertTrue(text.endsWith("tail\n"), text);
		assertTrue(text.contains("characters of output omitted"), text);
	}

	@Test
	void keepsTheBeginningAndTheEndOfAnOverflowingTranscript() {
		String text = transcriptOf(new BoundedTranscript(16, 16), "head\n" + filler(4000) + "tail\n");

		assertTrue(text.startsWith("head\n"), text);
		assertTrue(text.endsWith("tail\n"), text);
	}

	@Test
	void namesHowMuchWasOmitted() {
		String input = "head\n" + filler(4000) + "tail\n";
		String text = transcriptOf(new BoundedTranscript(16, 16), input);

		assertTrue(text.contains("characters of output omitted"), text);
		assertTrue(text.length() < input.length() / 10, "the middle must not survive: kept " + text.length());
	}

	/**
	 * The bound is what makes the transcript safe to carry into an exception message
	 * and from there into the JSON report, so it has to hold for output far larger
	 * than the limits rather than merely for output a little over them.
	 */
	@Test
	void boundsTheTextItKeepsWhateverTheChildPrints() {
		BoundedTranscript transcript = new BoundedTranscript(1024, 1024);
		appendRepeatedly(transcript, "a line of noise from a very talkative command\n", 40_000);

		int kept = transcript.text().length();
		assertTrue(kept < 1024 + 2 * 1024 + 256, "the head, both tail windows, and the marker, but no more: " + kept);
	}

	/**
	 * Credentials are masked by {@link Redaction} recognising the user information
	 * that follows a {@code ://}, so a cut through the middle of a clone URL would
	 * strand the token with nothing left to recognise it by. Cutting whole lines is
	 * what keeps that from happening — here the head limit falls inside the token
	 * itself.
	 */
	@Test
	void neverCutsThroughACredentialledUrl() {
		int insideTheToken = "keep\n".length() + "https://x-access-token:SEC".length();
		String text = transcriptOf(new BoundedTranscript(insideTheToken, 16),
				"keep\n" + CREDENTIALLED_URL + "\n" + filler(4000) + "tail\n");

		assertFalse(text.contains("SECRET"), text);
		assertFalse(text.contains("github.com"), text);
		assertTrue(text.startsWith("keep\n"), text);
	}

	/** A transcript that fits is untouched, so the caller's redaction still has a whole URL to mask. */
	@Test
	void leavesAnIntactUrlForTheCallerToRedact() {
		String text = transcriptOf(new BoundedTranscript(), "fatal: could not read Username for '"
				+ CREDENTIALLED_URL + "'\n");

		assertTrue(text.contains("SECRET"), text);
		assertEquals("fatal: could not read Username for 'https://***@github.com/owner/repo.git'\n",
				Redaction.of(text));
	}

	/**
	 * A region carrying no line feed cannot be cut at one, so it is dropped whole
	 * rather than trimmed mid-line — the one reading that cannot split a URL.
	 */
	@Test
	void dropsAKeptRegionThatCarriesNoLineFeed() {
		String text = transcriptOf(new BoundedTranscript(16, 16), "a".repeat(4000));

		assertFalse(text.contains("aaa"), text);
		assertTrue(text.contains("characters of output omitted"), text);
	}

	@Test
	void rejectsNonPositiveLimits() {
		assertThrows(IllegalArgumentException.class, () -> new BoundedTranscript(0, 16));
		assertThrows(IllegalArgumentException.class, () -> new BoundedTranscript(16, -1));
	}

	private static String transcriptOf(BoundedTranscript transcript, String text) {
		append(transcript, text);
		return transcript.text();
	}

	private static void append(BoundedTranscript transcript, String text) {
		transcript.append(text.toCharArray(), text.length());
	}

	private static void appendRepeatedly(BoundedTranscript transcript, String line, int times) {
		char[] buffer = line.toCharArray();
		for (int written = 0; written < times; written++) {
			transcript.append(buffer, buffer.length);
		}
	}

	private static String filler(int lines) {
		return ("filler\n").repeat(lines);
	}
}
