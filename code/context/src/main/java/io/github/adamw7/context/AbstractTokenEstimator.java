package io.github.adamw7.context;

/**
 * What the dependency-free {@link TokenEstimator}s share: the characters-per-token
 * ratio they are configured with, the rejection of a non-positive one, the rule
 * that absent or empty text costs nothing, and the rounding-up that gives any
 * shorter run one token rather than none. Subclasses supply only how they count
 * the text itself, which is the one concern in which they differ.
 */
public abstract class AbstractTokenEstimator implements TokenEstimator {

	public static final int DEFAULT_CHARACTERS_PER_TOKEN = 4;

	protected final int charactersPerToken;

	protected AbstractTokenEstimator(int charactersPerToken) {
		if (charactersPerToken <= 0) {
			throw new IllegalArgumentException(
					"charactersPerToken must be positive and received: " + charactersPerToken);
		}
		this.charactersPerToken = charactersPerToken;
	}

	@Override
	public final int estimate(String text) {
		return text == null || text.isEmpty() ? 0 : count(text);
	}

	/** Counts the tokens of text already known to be non-empty. */
	protected abstract int count(String text);

	/** Rounds up, so a run shorter than one token's worth of characters still costs one. */
	protected final int tokensFor(int characters) {
		return (characters + charactersPerToken - 1) / charactersPerToken;
	}
}
