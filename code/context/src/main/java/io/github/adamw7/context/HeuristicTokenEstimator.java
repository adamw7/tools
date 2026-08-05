package io.github.adamw7.context;

/**
 * A fast, dependency-free {@link TokenEstimator} that approximates token count
 * from character count. Most byte-pair encoders average a few characters per
 * token, so dividing the length by a fixed {@code charactersPerToken} (default
 * {@value #DEFAULT_CHARACTERS_PER_TOKEN}) gives a cheap upper-ish estimate
 * without a tokenizer dependency. The estimate is rounded up so that any
 * non-empty text costs at least one token.
 */
public class HeuristicTokenEstimator extends AbstractTokenEstimator {

	public HeuristicTokenEstimator() {
		this(DEFAULT_CHARACTERS_PER_TOKEN);
	}

	public HeuristicTokenEstimator(int charactersPerToken) {
		super(charactersPerToken);
	}

	@Override
	protected int count(String text) {
		return tokensFor(text.length());
	}
}
