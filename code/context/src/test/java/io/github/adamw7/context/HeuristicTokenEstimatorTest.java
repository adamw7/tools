package io.github.adamw7.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class HeuristicTokenEstimatorTest {

	private final HeuristicTokenEstimator estimator = new HeuristicTokenEstimator();

	@Test
	void emptyTextCostsNoTokens() {
		assertEquals(0, estimator.estimate(""));
	}

	@Test
	void nullTextCostsNoTokens() {
		assertEquals(0, estimator.estimate(null));
	}

	@Test
	void roundsUpSoAnyNonEmptyTextCostsAtLeastOneToken() {
		assertEquals(1, estimator.estimate("a"));
		assertEquals(1, estimator.estimate("abcd"));
		assertEquals(2, estimator.estimate("abcde"));
	}

	@Test
	void honoursACustomCharactersPerToken() {
		HeuristicTokenEstimator twoCharsPerToken = new HeuristicTokenEstimator(2);

		assertEquals(3, twoCharsPerToken.estimate("abcde"));
	}

	@Test
	void rejectsNonPositiveCharactersPerToken() {
		assertThrows(IllegalArgumentException.class, () -> new HeuristicTokenEstimator(0));
		assertThrows(IllegalArgumentException.class, () -> new HeuristicTokenEstimator(-1));
	}
}
