package io.github.adamw7.context;

/**
 * A dependency-free {@link TokenEstimator} that approximates token count by
 * respecting word and symbol boundaries rather than dividing the whole text by a
 * fixed character count. Byte-pair encoders rarely merge across whitespace or
 * punctuation, so this estimator splits the text into runs: each maximal run of
 * letters, digits and underscores costs {@code ceil(length / charactersPerToken)}
 * tokens (at least one), every other non-whitespace character (punctuation or a
 * symbol) costs one token, and whitespace costs nothing. This tracks a real
 * tokenizer more closely than {@link HeuristicTokenEstimator} on
 * punctuation-dense source code, while staying fast and tokenizer-free.
 */
public class SubwordTokenEstimator extends AbstractTokenEstimator {

	public SubwordTokenEstimator() {
		this(DEFAULT_CHARACTERS_PER_TOKEN);
	}

	public SubwordTokenEstimator(int charactersPerToken) {
		super(charactersPerToken);
	}

	@Override
	protected int count(String text) {
		int total = 0;
		int wordLength = 0;
		for (int index = 0; index < text.length(); index++) {
			char character = text.charAt(index);
			if (isWordCharacter(character)) {
				wordLength++;
			} else {
				total += wordTokens(wordLength) + symbolTokens(character);
				wordLength = 0;
			}
		}
		return total + wordTokens(wordLength);
	}

	private boolean isWordCharacter(char character) {
		return Character.isLetterOrDigit(character) || character == '_';
	}

	private int wordTokens(int wordLength) {
		return wordLength == 0 ? 0 : tokensFor(wordLength);
	}

	private int symbolTokens(char character) {
		return Character.isWhitespace(character) ? 0 : 1;
	}
}
