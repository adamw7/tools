package io.github.adamw7.tools.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Text assembled in code is LF, so what a rewriter writes back has to be put on the
 * terminator the file it is written into already uses. Both directions are pinned,
 * because either one getting it wrong shows up the same way: a diff nobody wrote.
 */
class LineTerminatorsTest {

	@Test
	void writesCrlfIntoAFileThatAlreadyUsesIt() {
		assertEquals("a\r\nb\r\n", LineTerminators.matching("a\nb\n", "first\r\nsecond\r\n"));
	}

	@Test
	void leavesTextOnLfForAFileThatUsesLf() {
		assertEquals("a\nb\n", LineTerminators.matching("a\r\nb\r\n", "first\nsecond\n"));
	}

	/** Text mixing terminators — a converted body beside a part carried over — converts once. */
	@Test
	void doesNotDoubleConvertTextThatAlreadyMixesTerminators() {
		assertEquals("a\r\nb\r\n", LineTerminators.matching("a\r\nb\n", "sample\r\n"));
	}

	@Test
	void normalizesEveryTerminatorToLf() {
		assertEquals("a\nb\nc\n", LineTerminators.normalized("a\r\nb\rc\n"));
	}
}
