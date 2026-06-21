package io.github.adamw7.context;

/**
 * A fast, dependency-free {@link TokenEstimator} that approximates token count
 * from character count. Most byte-pair encoders average a few characters per
 * token, so dividing the length by a fixed {@code charactersPerToken} (default
 * {@value #DEFAULT_CHARACTERS_PER_TOKEN}) gives a cheap upper-ish estimate
 * without a tokenizer dependency. The estimate is rounded up so that any
 * non-empty text costs at least one token.
 */
public class HeuristicTokenEstimator implements TokenEstimator {

	public static final int DEFAULT_CHARACTERS_PER_TOKEN = 4;

	private final int charactersPerToken;

	public HeuristicTokenEstimator() {
		this(DEFAULT_CHARACTERS_PER_TOKEN);
	}

	public HeuristicTokenEstimator(int charactersPerToken) {
		requirePositive(charactersPerToken);
		this.charactersPerToken = charactersPerToken;
	}

	private void requirePositive(int charactersPerToken) {
		if (charactersPerToken <= 0) {
			throw new IllegalArgumentException(
					"charactersPerToken must be positive and received: " + charactersPerToken);
		}
	}

	@Override
	public int estimate(String text) {
		if (text == null || text.isEmpty()) {
			return 0;
		}
		return ceilDivision(text.length(), charactersPerToken);
	}

	private int ceilDivision(int dividend, int divisor) {
		return (dividend + divisor - 1) / divisor;
	}
}
