package io.github.adamw7.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class SubwordTokenEstimatorTest {

	private final SubwordTokenEstimator estimator = new SubwordTokenEstimator();

	@Test
	void emptyTextCostsNoTokens() {
		assertEquals(0, estimator.estimate(""));
	}

	@Test
	void nullTextCostsNoTokens() {
		assertEquals(0, estimator.estimate(null));
	}

	@Test
	void aShortWordCostsAtLeastOneToken() {
		assertEquals(1, estimator.estimate("a"));
		assertEquals(1, estimator.estimate("abcd"));
		assertEquals(2, estimator.estimate("abcde"));
	}

	@Test
	void whitespaceSeparatesWordsButCostsNothing() {
		assertEquals(3, estimator.estimate("a b c"));
		assertEquals(3, estimator.estimate("a  b\tc\n"));
	}

	@Test
	void eachPunctuationCharacterCostsOneToken() {
		assertEquals(3, estimator.estimate("a.b"));
		assertEquals(4, estimator.estimate("foo(bar)"));
	}

	@Test
	void honoursACustomCharactersPerToken() {
		SubwordTokenEstimator twoCharsPerToken = new SubwordTokenEstimator(2);

		assertEquals(3, twoCharsPerToken.estimate("abcde"));
	}

	@Test
	void countsPunctuationDenseCodeMoreThanAPlainCharacterDivision() {
		String code = "a=b+c;";

		assertEquals(6, estimator.estimate(code));
	}

	@Test
	void underscoresAndDigitsArePartOfAWord() {
		assertEquals(2, estimator.estimate("foo_bar"));
		assertEquals(2, estimator.estimate("abc123"));
	}

	@Test
	void leadingAndTrailingPunctuationEachCostOneToken() {
		assertEquals(3, estimator.estimate("(a)"));
		assertEquals(2, estimator.estimate(".a"));
	}

	@Test
	void aRunOfSymbolsCostsOneTokenPerSymbol() {
		assertEquals(4, estimator.estimate("+-*/"));
	}

	@Test
	void unicodeLettersCountAsWordCharacters() {
		assertEquals(1, estimator.estimate("café"));
	}

	@Test
	void aCustomCharactersPerTokenSplitsLongWords() {
		SubwordTokenEstimator threeCharsPerToken = new SubwordTokenEstimator(3);

		assertEquals(3, threeCharsPerToken.estimate("abcdefg"));
	}

	@Test
	void exposesTheDefaultCharactersPerTokenConstant() {
		assertEquals(4, SubwordTokenEstimator.DEFAULT_CHARACTERS_PER_TOKEN);
	}

	@Test
	void countsAMixedWordAndSymbolSequence() {
		assertEquals(4, estimator.estimate("foo = bar;"));
	}

	@Test
	void rejectsNonPositiveCharactersPerToken() {
		assertThrows(IllegalArgumentException.class, () -> new SubwordTokenEstimator(0));
		assertThrows(IllegalArgumentException.class, () -> new SubwordTokenEstimator(-1));
	}
}
