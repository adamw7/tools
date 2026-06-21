package io.github.adamw7.context;

/**
 * Estimates how many LLM tokens a piece of text is worth. Implementations trade
 * accuracy for speed; the estimate lets a budget-aware {@link Context} decide how
 * much source code fits within a model's context window.
 */
@FunctionalInterface
public interface TokenEstimator {

	int estimate(String text);
}
