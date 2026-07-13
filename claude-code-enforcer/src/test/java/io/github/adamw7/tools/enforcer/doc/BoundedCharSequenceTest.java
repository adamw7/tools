package io.github.adamw7.tools.enforcer.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.enforcer.doc.BoundedCharSequence.BacktrackLimitExceededException;

class BoundedCharSequenceTest {

	@Test
	void readsCharactersUpToTheBudget() {
		BoundedCharSequence sequence = new BoundedCharSequence("abc", 3);

		assertEquals('a', sequence.charAt(0));
		assertEquals('b', sequence.charAt(1));
		assertEquals('c', sequence.charAt(2));
	}

	@Test
	void throwsOnceTheBudgetIsExceeded() {
		BoundedCharSequence sequence = new BoundedCharSequence("abc", 2);
		sequence.charAt(0);
		sequence.charAt(1);

		assertThrows(BacktrackLimitExceededException.class, () -> sequence.charAt(2));
	}

	@Test
	void subSequenceSharesTheBudgetWithItsParent() {
		BoundedCharSequence sequence = new BoundedCharSequence("abc", 1);
		sequence.charAt(0);

		CharSequence sub = sequence.subSequence(0, 3);
		assertThrows(BacktrackLimitExceededException.class, () -> sub.charAt(0));
	}

	@Test
	void toStringDoesNotSpendTheBudget() {
		BoundedCharSequence sequence = new BoundedCharSequence("abc", 0);

		assertEquals("abc", sequence.toString());
	}

	@Test
	void abortsARegexMatchThatExceedsTheBudget() {
		Pattern pattern = Pattern.compile("(x+x+)+y");
		Matcher matcher = pattern.matcher(new BoundedCharSequence("x".repeat(60) + "z", 1_000));

		assertThrows(BacktrackLimitExceededException.class, matcher::find);
	}

	@Test
	void completesAMatchThatStaysWithinTheBudget() {
		Pattern pattern = Pattern.compile("Java (\\d+)");
		Matcher matcher = pattern.matcher(new BoundedCharSequence("We target Java 25.", 1_000_000));

		assertTrue(matcher.find());
		assertEquals("25", matcher.group(1));
	}
}
