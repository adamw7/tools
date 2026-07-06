package io.github.adamw7.tools.data.uniqueness;

/**
 * Checks whether a set of columns forms a unique key over a data source. The
 * source is supplied when the check is created, so an instance is always ready
 * to {@link #exec} and can never be left in a half-initialised state.
 */
public interface Uniqueness {

	/**
	 * Checks the given columns for uniqueness.
	 *
	 * @param keyCandidates the columns to check, must be non-empty and free of
	 *                      {@code null}s and duplicates
	 * @return the result of the check
	 */
	Result exec(String... keyCandidates);

	/**
	 * Checks every column of the data source for uniqueness.
	 *
	 * @return the result of the check
	 */
	Result execForAllColumns();
}
